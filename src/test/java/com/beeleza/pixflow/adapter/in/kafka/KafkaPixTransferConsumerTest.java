package com.beeleza.pixflow.adapter.in.kafka;

import com.beeleza.pixflow.application.port.in.SettlePixTransferUseCase;
import com.beeleza.pixflow.application.service.exception.TransactionMismatchException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class KafkaPixTransferConsumerTest {

    private final SettlePixTransferUseCase settle = mock(SettlePixTransferUseCase.class);
    private final KafkaPixTransferConsumer consumer = new KafkaPixTransferConsumer(settle);

    @Test
    void settlesWithTheFieldsOfTheResponse() {
        consumer.consume(new PixTransactionResponseMessage("tx-1", "key-1", false, "INVALID_AMOUNT"));

        verify(settle).settle(new SettlePixTransferUseCase.Command("key-1", "tx-1", false, "INVALID_AMOUNT"));
    }

    // The error handler decides between retry and DLT, so the consumer must not swallow failures.
    @Test
    void propagatesSettlementFailures() {
        given(settle.settle(any())).willThrow(new TransactionMismatchException("mismatch"));

        assertThatThrownBy(() -> consumer.consume(new PixTransactionResponseMessage("tx-1", "key-1", true, null)))
                .isInstanceOf(TransactionMismatchException.class);
    }
}
