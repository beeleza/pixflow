package com.beeleza.pixflow.adapter.out.persistence;

import com.beeleza.pixflow.adapter.out.kafka.PixTransactionMessage;
import com.beeleza.pixflow.application.port.out.PixTransferEventsPort;
import com.beeleza.pixflow.domain.model.PixTransaction;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.UUID;

@Component
public class OutboxPixTransferEventsAdapter implements PixTransferEventsPort {

    private static final String EVENT_TYPE = "PIX_TRANSFER_REQUESTED";
    private static final String TOPIC = "pix-transaction";

    private final SpringDataOutboxEventRepository repository;
    private final ObjectMapper objectMapper;

    public OutboxPixTransferEventsAdapter(SpringDataOutboxEventRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Override
    public void transferRequested(PixTransaction transaction) {
        PixTransactionMessage message = new PixTransactionMessage(
                transaction.id().toString(),
                transaction.idempotencyKey().value(),
                transaction.sourceAccount().toString(),
                transaction.destinationKey().value(),
                transaction.destinationKey().type().name(),
                transaction.amount().amount()
        );

        repository.save(new OutboxEventEntity(
                UUID.randomUUID().toString(),
                transaction.id().toString(),
                EVENT_TYPE,
                TOPIC,
                objectMapper.writeValueAsString(message),
                Instant.now()
        ));
    }
}
