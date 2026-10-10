package com.beeleza.pixflow.adapter.in.kafka;

import com.beeleza.pixflow.application.port.in.SettlePixTransferUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class KafkaPixTransferConsumer {

    private static final Logger log = LoggerFactory.getLogger(KafkaPixTransferConsumer.class);

    private static final String TOPIC = "pix-transaction-response";

    private final SettlePixTransferUseCase settlePixTransferUseCase;

    public KafkaPixTransferConsumer(SettlePixTransferUseCase settlePixTransferUseCase) {
        this.settlePixTransferUseCase = settlePixTransferUseCase;
    }

    @KafkaListener(topics = TOPIC, groupId = "pixflow-settlement")
    public void consume(PixTransactionResponseMessage message) {
        log.info(
                "Received PIX transaction response. transactionId={}, accepted={}",
                message.transactionId(),
                message.accepted()
        );

        SettlePixTransferUseCase.Command command = new SettlePixTransferUseCase.Command(
                message.idempotencyKey(),
                message.transactionId(),
                message.accepted(),
                message.failureReason()
        );

        settlePixTransferUseCase.settle(command);

        log.info(
                "PIX transaction settled successfully. transactionId={}",
                message.transactionId()
        );
    }
}