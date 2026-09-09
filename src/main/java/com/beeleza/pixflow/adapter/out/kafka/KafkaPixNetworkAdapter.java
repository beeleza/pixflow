package com.beeleza.pixflow.adapter.out.kafka;

import com.beeleza.pixflow.application.port.out.PixNetworkException;
import com.beeleza.pixflow.application.port.out.PixNetworkGateway;
import com.beeleza.pixflow.domain.model.PixTransaction;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class KafkaPixNetworkAdapter implements PixNetworkGateway {

    private static final String TOPIC = "pix-transaction";

    private final KafkaTemplate<String, PixTransactionMessage> kafkaTemplate;

    public KafkaPixNetworkAdapter(
            KafkaTemplate<String, PixTransactionMessage> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public void dispatch(PixTransaction transaction) {
        PixTransactionMessage message = new PixTransactionMessage(
                transaction.id().toString(),
                transaction.idempotencyKey().value(),
                transaction.sourceAccount().toString(),
                transaction.destinationKey().value(),
                transaction.destinationKey().type().name(),
                transaction.amount().amount()
        );

        try
        {
            kafkaTemplate.send(TOPIC, transaction.id().toString(), message).join();
        } catch (Exception e) {
            throw new PixNetworkException("Failed to send pix transaction to Kafka", e);
        }
    }
}
