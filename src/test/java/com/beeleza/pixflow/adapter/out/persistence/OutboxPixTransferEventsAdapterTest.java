package com.beeleza.pixflow.adapter.out.persistence;

import com.beeleza.pixflow.domain.model.AccountId;
import com.beeleza.pixflow.domain.model.IdempotencyKey;
import com.beeleza.pixflow.domain.model.Money;
import com.beeleza.pixflow.domain.model.PixKey;
import com.beeleza.pixflow.domain.model.PixKeyType;
import com.beeleza.pixflow.domain.model.PixTransaction;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class OutboxPixTransferEventsAdapterTest {

    private final SpringDataOutboxEventRepository repository = mock(SpringDataOutboxEventRepository.class);
    private final ObjectMapper objectMapper = JsonMapper.builder().build();
    private final OutboxPixTransferEventsAdapter adapter = new OutboxPixTransferEventsAdapter(repository, objectMapper);

    private final PixTransaction transaction = PixTransaction.initiate(
            IdempotencyKey.of("9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d"),
            new AccountId(UUID.fromString("a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d")),
            new PixKey("fulano@email.com", PixKeyType.EMAIL),
            Money.of("150.50"));

    @Test
    void storesPendingEventKeyedByTransaction() {
        Instant before = Instant.now();

        OutboxEventEntity event = recordAndCapture();

        assertThat(event.getId()).isNotBlank();
        assertThat(event.getAggregateId()).isEqualTo(transaction.id().toString());
        assertThat(event.getEventType()).isEqualTo("PIX_TRANSFER_REQUESTED");
        assertThat(event.getTopic()).isEqualTo("pix-transaction");
        assertThat(event.getCreatedAt()).isBetween(before, Instant.now());
        assertThat(event.getPublishedAt()).isNull();
        assertThat(event.getAttempts()).isZero();
    }

    // Field names must match PixTransactionMessageRequest in pixflow-network-simulator.
    @Test
    void writesPayloadInTheFormatTheNetworkExpects() {
        String payload = recordAndCapture().getPayload();

        Map<String, Object> json = objectMapper.readValue(payload, Map.class);

        assertThat(json).containsOnlyKeys(
                "transactionId", "idempotencyKey", "sourceAccount", "destinationKey", "destinationKeyType", "amount");
        assertThat(json)
                .containsEntry("transactionId", transaction.id().toString())
                .containsEntry("idempotencyKey", "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d")
                .containsEntry("sourceAccount", "a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d")
                .containsEntry("destinationKey", "fulano@email.com")
                .containsEntry("destinationKeyType", "EMAIL");
        assertThat(new BigDecimal(json.get("amount").toString())).isEqualByComparingTo("150.50");
    }

    private OutboxEventEntity recordAndCapture() {
        adapter.transferRequested(transaction);
        ArgumentCaptor<OutboxEventEntity> captor = ArgumentCaptor.forClass(OutboxEventEntity.class);
        verify(repository).save(captor.capture());
        return captor.getValue();
    }
}
