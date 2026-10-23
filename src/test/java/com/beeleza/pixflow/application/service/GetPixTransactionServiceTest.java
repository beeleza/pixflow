package com.beeleza.pixflow.application.service;

import com.beeleza.pixflow.application.port.out.LoadPixTransactionPort;
import com.beeleza.pixflow.application.service.exception.TransactionNotFoundException;
import com.beeleza.pixflow.domain.model.AccountId;
import com.beeleza.pixflow.domain.model.IdempotencyKey;
import com.beeleza.pixflow.domain.model.Money;
import com.beeleza.pixflow.domain.model.PixKey;
import com.beeleza.pixflow.domain.model.PixKeyType;
import com.beeleza.pixflow.domain.model.PixTransaction;
import com.beeleza.pixflow.domain.model.TransactionId;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class GetPixTransactionServiceTest {

    private final LoadPixTransactionPort load = mock(LoadPixTransactionPort.class);
    private final GetPixTransactionService service = new GetPixTransactionService(load);

    @Test
    void returnsStoredTransaction() {
        PixTransaction transaction = PixTransaction.initiate(
                IdempotencyKey.of("key"),
                new AccountId(UUID.randomUUID()),
                new PixKey("fulano@email.com", PixKeyType.EMAIL),
                Money.of(10));
        given(load.findById(transaction.id())).willReturn(Optional.of(transaction));

        assertThat(service.get(transaction.id().value())).isSameAs(transaction);
    }

    @Test
    void reportsMissingTransaction() {
        UUID id = UUID.randomUUID();
        given(load.findById(any())).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(id))
                .isInstanceOf(TransactionNotFoundException.class)
                .hasMessageContaining(id.toString());
    }

    @Test
    void looksUpByTheGivenId() {
        UUID id = UUID.randomUUID();
        given(load.findById(any())).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(id)).isInstanceOf(TransactionNotFoundException.class);
        verify(load).findById(new TransactionId(id));
    }
}
