package com.beeleza.pixflow.application.service;

import com.beeleza.pixflow.application.port.in.SettlePixTransferUseCase.Command;
import com.beeleza.pixflow.application.port.out.LoadPixTransactionPort;
import com.beeleza.pixflow.application.port.out.SavePixTransactionPort;
import com.beeleza.pixflow.application.service.exception.TransactionMismatchException;
import com.beeleza.pixflow.application.service.exception.TransactionNotFoundException;
import com.beeleza.pixflow.domain.exception.IllegalTransactionStateException;
import com.beeleza.pixflow.domain.exception.InvalidIdempotencyKeyException;
import com.beeleza.pixflow.domain.model.AccountId;
import com.beeleza.pixflow.domain.model.IdempotencyKey;
import com.beeleza.pixflow.domain.model.Money;
import com.beeleza.pixflow.domain.model.PixKey;
import com.beeleza.pixflow.domain.model.PixKeyType;
import com.beeleza.pixflow.domain.model.PixTransaction;
import com.beeleza.pixflow.domain.model.TransactionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class SettlePixTransferServiceTest {

    private static final String KEY = "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d";

    private final LoadPixTransactionPort load = mock(LoadPixTransactionPort.class);
    private final SavePixTransactionPort save = mock(SavePixTransactionPort.class);
    private final SettlePixTransferService service = new SettlePixTransferService(load, save);

    private PixTransaction transaction;

    @BeforeEach
    void setUp() {
        transaction = PixTransaction.initiate(
                IdempotencyKey.of(KEY),
                new AccountId(UUID.randomUUID()),
                new PixKey("fulano@email.com", PixKeyType.EMAIL),
                new Money(new BigDecimal("10.00")));
        given(load.findByIdempotencyKey(IdempotencyKey.of(KEY))).willReturn(Optional.of(transaction));
        given(save.save(any())).willAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void completesMatchingTransaction() {
        PixTransaction settled = service.settle(new Command(KEY, transaction.id().toString(), true, null));

        assertThat(settled.status()).isEqualTo(TransactionStatus.COMPLETED);
    }

    @Test
    void rejectsResponseForAnotherTransaction() {
        Command command = new Command(KEY, UUID.randomUUID().toString(), true, null);

        assertThatThrownBy(() -> service.settle(command)).isInstanceOf(TransactionMismatchException.class);
        assertThat(transaction.status()).isEqualTo(TransactionStatus.PENDING);
        verify(save, never()).save(any());
    }

    @Test
    void truncatesLongFailureReason() {
        String reason = "x".repeat(PixTransaction.MAX_FAILURE_REASON_LENGTH + 100);

        PixTransaction settled = service.settle(new Command(KEY, transaction.id().toString(), false, reason));

        assertThat(settled.status()).isEqualTo(TransactionStatus.FAILED);
        assertThat(settled.failureReason()).hasSize(PixTransaction.MAX_FAILURE_REASON_LENGTH);
    }

    @Test
    void failsTransactionWithReasonWhenRejected() {
        PixTransaction settled = service.settle(
                new Command(KEY, transaction.id().toString(), false, "INVALID_DESTINATION_KEY"));

        assertThat(settled.status()).isEqualTo(TransactionStatus.FAILED);
        assertThat(settled.failureReason()).isEqualTo("INVALID_DESTINATION_KEY");
        assertThat(settled.settledAt()).isNotNull();
        verify(save).save(transaction);
    }

    @Test
    void rejectsResponseForUnknownTransaction() {
        given(load.findByIdempotencyKey(IdempotencyKey.of(KEY))).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.settle(new Command(KEY, transaction.id().toString(), true, null)))
                .isInstanceOf(TransactionNotFoundException.class);
        verify(save, never()).save(any());
    }

    @Test
    void ignoresDuplicateResponseWithSameOutcome() {
        transaction.complete();

        PixTransaction settled = service.settle(new Command(KEY, transaction.id().toString(), true, null));

        assertThat(settled).isSameAs(transaction);
        verify(save, never()).save(any());
    }

    @Test
    void refusesResponseThatContradictsSettledOutcome() {
        transaction.fail("INVALID_AMOUNT");

        assertThatThrownBy(() -> service.settle(new Command(KEY, transaction.id().toString(), true, null)))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThat(transaction.status()).isEqualTo(TransactionStatus.FAILED);
        verify(save, never()).save(any());
    }

    @Test
    void rejectsResponseWithoutIdempotencyKey() {
        assertThatThrownBy(() -> service.settle(new Command(" ", transaction.id().toString(), true, null)))
                .isInstanceOf(InvalidIdempotencyKeyException.class);
        verify(load, never()).findByIdempotencyKey(any());
    }
}
