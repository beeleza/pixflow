package com.beeleza.pixflow.application.service;

import com.beeleza.pixflow.application.port.in.ProcessPixTransferUseCase.Command;
import com.beeleza.pixflow.application.port.out.LoadPixTransactionPort;
import com.beeleza.pixflow.application.port.out.PixTransferEventsPort;
import com.beeleza.pixflow.application.port.out.SavePixTransactionPort;
import com.beeleza.pixflow.application.port.out.TransactionRunner;
import com.beeleza.pixflow.application.port.out.TransferLockPort;
import com.beeleza.pixflow.application.service.exception.IdempotencyKeyReuseException;
import com.beeleza.pixflow.application.service.exception.PixTransferInProgressException;
import com.beeleza.pixflow.domain.exception.InvalidPixKeyException;
import com.beeleza.pixflow.domain.exception.InvalidTransactionAmountException;
import com.beeleza.pixflow.domain.model.AccountId;
import com.beeleza.pixflow.domain.model.IdempotencyKey;
import com.beeleza.pixflow.domain.model.Money;
import com.beeleza.pixflow.domain.model.PixKey;
import com.beeleza.pixflow.domain.model.PixKeyType;
import com.beeleza.pixflow.domain.model.PixTransaction;
import com.beeleza.pixflow.domain.model.TransactionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class ProcessPixTransferServiceTest {

    private static final String KEY = "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d";
    private static final UUID SOURCE = UUID.fromString("a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d");

    private final LoadPixTransactionPort load = mock(LoadPixTransactionPort.class);
    private final SavePixTransactionPort save = mock(SavePixTransactionPort.class);
    private final PixTransferEventsPort events = mock(PixTransferEventsPort.class);
    private final TransferLockPort lock = mock(TransferLockPort.class);
    private final TransactionRunner transactionRunner = mock(TransactionRunner.class);
    private final ProcessPixTransferService service =
            new ProcessPixTransferService(load, save, events, lock, transactionRunner);

    private PixTransaction existing;

    @BeforeEach
    void setUp() {
        existing = PixTransaction.initiate(
                IdempotencyKey.of(KEY),
                new AccountId(SOURCE),
                new PixKey("fulano@email.com", PixKeyType.EMAIL),
                new Money(new BigDecimal("150.50")));
        given(load.findByIdempotencyKey(IdempotencyKey.of(KEY))).willReturn(Optional.of(existing));
        given(lock.acquire(any())).willReturn(Optional.of("token"));
        given(transactionRunner.run(any())).willAnswer(invocation -> invocation.<Supplier<?>>getArgument(0).get());
    }

    @Test
    void replaysExistingTransactionForSameRequest() {
        PixTransaction result = service.process(command("fulano@email.com", "150.50"));

        assertThat(result).isSameAs(existing);
        verify(events, never()).transferRequested(any());
        verify(save, never()).save(any());
    }

    @Test
    void savesNewTransferAsPendingAndRecordsEvent() {
        given(load.findByIdempotencyKey(IdempotencyKey.of(KEY))).willReturn(Optional.empty());
        given(save.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        PixTransaction result = service.process(command("fulano@email.com", "150.50"));

        assertThat(result.status()).isEqualTo(TransactionStatus.PENDING);
        verify(save).save(result);
        verify(events).transferRequested(result);
    }

    @Test
    void treatsNormalizedValuesAsSameRequest() {
        PixTransaction result = service.process(command(" fulano@email.com ", "150.5"));

        assertThat(result).isSameAs(existing);
    }

    @Test
    void rejectsKeyReusedWithDifferentAmount() {
        assertThatThrownBy(() -> service.process(command("fulano@email.com", "999.00")))
                .isInstanceOf(IdempotencyKeyReuseException.class);
        verify(events, never()).transferRequested(any());
    }

    @Test
    void rejectsKeyReusedWithDifferentDestination() {
        assertThatThrownBy(() -> service.process(command("ciclano@email.com", "150.50")))
                .isInstanceOf(IdempotencyKeyReuseException.class);
    }

    @Test
    void rejectsKeyReusedWhileLockIsHeldElsewhere() {
        given(lock.acquire(any())).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.process(command("fulano@email.com", "999.00")))
                .isInstanceOf(IdempotencyKeyReuseException.class);
    }

    @Test
    void releasesLockWhenKeyIsReused() {
        assertThatThrownBy(() -> service.process(command("fulano@email.com", "999.00")))
                .isInstanceOf(IdempotencyKeyReuseException.class);

        verify(lock).release(IdempotencyKey.of(KEY), "token");
    }

    @Test
    void releasesLockOnlyAfterTransactionCompletes() {
        given(load.findByIdempotencyKey(IdempotencyKey.of(KEY))).willReturn(Optional.empty());
        given(save.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        service.process(command("fulano@email.com", "150.50"));

        InOrder order = inOrder(lock, transactionRunner);
        order.verify(lock).acquire(IdempotencyKey.of(KEY));
        order.verify(transactionRunner).run(any());
        order.verify(lock).release(IdempotencyKey.of(KEY), "token");
    }

    @Test
    void savesTransferAndEventInsideTheSameTransaction() {
        given(load.findByIdempotencyKey(IdempotencyKey.of(KEY))).willReturn(Optional.empty());
        willReturn(existing).given(transactionRunner).run(any());

        service.process(command("fulano@email.com", "150.50"));

        verify(save, never()).save(any());
        verify(events, never()).transferRequested(any());
    }

    @Test
    void reportsInProgressWhenLockIsHeldAndNothingIsStoredYet() {
        given(lock.acquire(any())).willReturn(Optional.empty());
        given(load.findByIdempotencyKey(IdempotencyKey.of(KEY))).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.process(command("fulano@email.com", "150.50")))
                .isInstanceOf(PixTransferInProgressException.class);
        verify(transactionRunner, never()).run(any());
        verify(lock, never()).release(any(), any());
    }

    @Test
    void replaysWithoutLockWhenLockIsHeldAndSameRequestIsStored() {
        given(lock.acquire(any())).willReturn(Optional.empty());

        PixTransaction result = service.process(command("fulano@email.com", "150.50"));

        assertThat(result).isSameAs(existing);
        verify(transactionRunner, never()).run(any());
        verify(lock, never()).release(any(), any());
    }

    @Test
    void releasesLockWhenSaveFails() {
        given(load.findByIdempotencyKey(IdempotencyKey.of(KEY))).willReturn(Optional.empty());
        given(save.save(any())).willThrow(new IllegalStateException("database down"));

        assertThatThrownBy(() -> service.process(command("fulano@email.com", "150.50")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("database down");
        verify(lock).release(IdempotencyKey.of(KEY), "token");
        verify(events, never()).transferRequested(any());
    }

    @Test
    void rejectsMalformedInputBeforeTakingTheLock() {
        assertThatThrownBy(() -> service.process(command("not-an-email", "150.50")))
                .isInstanceOf(InvalidPixKeyException.class);
        verify(lock, never()).acquire(any());
    }

    @Test
    void rejectsZeroAmountForNewTransferAndReleasesLock() {
        given(load.findByIdempotencyKey(IdempotencyKey.of(KEY))).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.process(command("fulano@email.com", "0.00")))
                .isInstanceOf(InvalidTransactionAmountException.class);
        verify(save, never()).save(any());
        verify(lock).release(IdempotencyKey.of(KEY), "token");
    }

    private static Command command(String destinationKey, String amount) {
        return new Command(KEY, SOURCE, PixKeyType.EMAIL, destinationKey, new BigDecimal(amount));
    }
}
