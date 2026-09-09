package com.beeleza.pixflow.adapter.in.kafka;

import com.beeleza.pixflow.application.port.in.SettlePixTransferUseCase;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class KafkaPixTransferConsumer {

    private static final String TOPIC = "pix-transaction-response";

    private final SettlePixTransferUseCase settlePixTransferUseCase;

    public KafkaPixTransferConsumer(SettlePixTransferUseCase settlePixTransferUseCase) {
        this.settlePixTransferUseCase = settlePixTransferUseCase;
    }

    @KafkaListener(topics = TOPIC, groupId = "pixflow-settlement")
    public void consume(PixTransactionResponseMessage message) {
        SettlePixTransferUseCase.Command command = new SettlePixTransferUseCase.Command(
                message.idempotencyKey(),
                message.transactionId(),
                message.accepted(),
                message.failureReason()
        );

        try {
            settlePixTransferUseCase.settle(command);
        } catch (Exception e) {
            System.err.println("Failed to settle transaction " + message.transactionId() + ": " + e.getMessage());
        }
    }
}