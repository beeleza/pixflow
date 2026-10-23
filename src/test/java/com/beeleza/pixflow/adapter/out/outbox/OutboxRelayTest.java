package com.beeleza.pixflow.adapter.out.outbox;

import com.beeleza.pixflow.adapter.out.persistence.OutboxEventEntity;
import com.beeleza.pixflow.adapter.out.persistence.SpringDataOutboxEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class OutboxRelayTest {

    private static final String TOPIC = "pix-transaction";

    private final SpringDataOutboxEventRepository repository = mock(SpringDataOutboxEventRepository.class);
    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
    private final OutboxRelay relay = new OutboxRelay(
            repository, kafkaTemplate, mock(PlatformTransactionManager.class), 50, Duration.ofSeconds(1));

    @Test
    void marksEventAsPublishedOnceBrokerAcknowledges() {
        OutboxEventEntity event = event("{\"transactionId\":\"1\"}");
        given(repository.lockNextPendingBatch(anyInt())).willReturn(List.of(event));
        given(kafkaTemplate.send(TOPIC, event.getAggregateId(), event.getPayload()))
                .willReturn(CompletableFuture.completedFuture(null));

        relay.publishPending();

        verify(kafkaTemplate).send(TOPIC, event.getAggregateId(), event.getPayload());
        assertThat(event.getPublishedAt()).isNotNull();
        assertThat(event.getAttempts()).isZero();
    }

    @Test
    void recordsFailureAndKeepsEventPending() {
        OutboxEventEntity event = event("{}");
        given(repository.lockNextPendingBatch(anyInt())).willReturn(List.of(event));
        given(kafkaTemplate.send(TOPIC, event.getAggregateId(), event.getPayload()))
                .willReturn(CompletableFuture.failedFuture(new RuntimeException("broker down")));

        relay.publishPending();

        assertThat(event.getPublishedAt()).isNull();
        assertThat(event.getAttempts()).isEqualTo(1);
        assertThat(event.getLastError()).contains("broker down");
    }

    @Test
    void keepsPublishingRestOfBatchWhenOneEventFails() {
        OutboxEventEntity failing = event("{\"n\":1}");
        OutboxEventEntity succeeding = event("{\"n\":2}");
        given(repository.lockNextPendingBatch(anyInt())).willReturn(List.of(failing, succeeding));
        given(kafkaTemplate.send(TOPIC, failing.getAggregateId(), failing.getPayload()))
                .willReturn(CompletableFuture.failedFuture(new RuntimeException("broker down")));
        given(kafkaTemplate.send(TOPIC, succeeding.getAggregateId(), succeeding.getPayload()))
                .willReturn(CompletableFuture.completedFuture(null));

        relay.publishPending();

        assertThat(failing.getPublishedAt()).isNull();
        assertThat(succeeding.getPublishedAt()).isNotNull();
    }

    @Test
    void recordsTimeoutAsFailedAttempt() {
        OutboxRelay impatientRelay = new OutboxRelay(
                repository, kafkaTemplate, mock(PlatformTransactionManager.class), 50, Duration.ofMillis(10));
        OutboxEventEntity event = event("{}");
        given(repository.lockNextPendingBatch(anyInt())).willReturn(List.of(event));
        given(kafkaTemplate.send(TOPIC, event.getAggregateId(), event.getPayload()))
                .willReturn(new CompletableFuture<>());

        impatientRelay.publishPending();

        assertThat(event.getPublishedAt()).isNull();
        assertThat(event.getAttempts()).isEqualTo(1);
    }

    @Test
    void stopsAndKeepsInterruptFlagWhenInterrupted() {
        OutboxEventEntity event = event("{}");
        given(repository.lockNextPendingBatch(anyInt())).willReturn(List.of(event));
        given(kafkaTemplate.send(TOPIC, event.getAggregateId(), event.getPayload()))
                .willReturn(new CompletableFuture<>());

        Thread.currentThread().interrupt();
        try {
            assertThatThrownBy(relay::publishPending).isInstanceOf(IllegalStateException.class);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
        assertThat(event.getPublishedAt()).isNull();
        assertThat(event.getAttempts()).isZero();
    }

    @Test
    void sendsNothingWhenNoEventIsPending() {
        given(repository.lockNextPendingBatch(anyInt())).willReturn(List.of());

        relay.publishPending();

        verifyNoInteractions(kafkaTemplate);
    }

    @Test
    void asksForConfiguredBatchSize() {
        given(repository.lockNextPendingBatch(anyInt())).willReturn(List.of());

        relay.publishPending();

        verify(repository).lockNextPendingBatch(50);
    }

    private static OutboxEventEntity event(String payload) {
        return new OutboxEventEntity(UUID.randomUUID().toString(), UUID.randomUUID().toString(),
                "PIX_TRANSFER_REQUESTED", TOPIC, payload, Instant.now());
    }
}
