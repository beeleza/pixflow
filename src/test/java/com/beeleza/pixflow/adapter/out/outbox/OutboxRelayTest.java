package com.beeleza.pixflow.adapter.out.outbox;

import com.beeleza.pixflow.adapter.out.persistence.OutboxEventEntity;
import com.beeleza.pixflow.adapter.out.persistence.SpringDataOutboxEventRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class OutboxRelayTest {

    private static final String TOPIC = "pix-transaction";
    private static final int MAX_ATTEMPTS = 3;
    private static final Duration INITIAL_BACKOFF = Duration.ofSeconds(1);
    private static final Duration MAX_BACKOFF = Duration.ofSeconds(10);

    private final SpringDataOutboxEventRepository repository = mock(SpringDataOutboxEventRepository.class);
    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
    private final OutboxRelay relay = new OutboxRelay(repository, kafkaTemplate,
            mock(PlatformTransactionManager.class), 50, Duration.ofSeconds(1), MAX_ATTEMPTS, INITIAL_BACKOFF,
            MAX_BACKOFF);

    @Test
    void marksEventAsPublishedOnceBrokerAcknowledges() {
        OutboxEventEntity event = event("{\"transactionId\":\"1\"}");
        given(repository.lockNextPendingBatch(any(), anyInt())).willReturn(List.of(event));
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
        given(repository.lockNextPendingBatch(any(), anyInt())).willReturn(List.of(event));
        given(kafkaTemplate.send(TOPIC, event.getAggregateId(), event.getPayload()))
                .willReturn(CompletableFuture.failedFuture(new RuntimeException("broker down")));

        relay.publishPending();

        assertThat(event.getPublishedAt()).isNull();
        assertThat(event.getFailedAt()).isNull();
        assertThat(event.getAttempts()).isEqualTo(1);
        assertThat(event.getLastError()).contains("broker down");
    }

    @Test
    void schedulesNextAttemptAfterTheBackoff() {
        OutboxEventEntity event = event("{}");
        givenSendFails(event);
        Instant before = Instant.now();

        relay.publishPending();

        assertThat(event.getNextAttemptAt())
                .isBetween(before.plus(INITIAL_BACKOFF), Instant.now().plus(INITIAL_BACKOFF));
    }

    @Test
    void givesUpAfterMaxAttempts() {
        OutboxEventEntity event = event("{}");
        event.setAttempts(MAX_ATTEMPTS - 1);
        Instant scheduled = event.getNextAttemptAt();
        givenSendFails(event);

        relay.publishPending();

        assertThat(event.getAttempts()).isEqualTo(MAX_ATTEMPTS);
        assertThat(event.getFailedAt()).isNotNull();
        assertThat(event.getPublishedAt()).isNull();
        assertThat(event.getNextAttemptAt()).isEqualTo(scheduled);
        assertThat(event.getLastError()).contains("broker down");
    }

    @Test
    void publishesOnTheLastAllowedAttempt() {
        OutboxEventEntity event = event("{}");
        event.setAttempts(MAX_ATTEMPTS - 1);
        given(repository.lockNextPendingBatch(any(), anyInt())).willReturn(List.of(event));
        given(kafkaTemplate.send(TOPIC, event.getAggregateId(), event.getPayload()))
                .willReturn(CompletableFuture.completedFuture(null));

        relay.publishPending();

        assertThat(event.getPublishedAt()).isNotNull();
        assertThat(event.getFailedAt()).isNull();
    }

    @Test
    void recordsSendThatFailsSynchronouslyWithoutAbortingTheBatch() {
        OutboxEventEntity rejected = event("{\"n\":1}");
        OutboxEventEntity next = event("{\"n\":2}");
        given(repository.lockNextPendingBatch(any(), anyInt())).willReturn(List.of(rejected, next));
        given(kafkaTemplate.send(TOPIC, rejected.getAggregateId(), rejected.getPayload()))
                .willThrow(new KafkaException("Send failed", new IllegalStateException("invalid topic")));
        given(kafkaTemplate.send(TOPIC, next.getAggregateId(), next.getPayload()))
                .willReturn(CompletableFuture.completedFuture(null));

        relay.publishPending();

        assertThat(rejected.getAttempts()).isEqualTo(1);
        assertThat(rejected.getLastError()).contains("invalid topic").doesNotContain("Send failed");
        assertThat(next.getPublishedAt()).isNotNull();
    }

    @Test
    void doublesTheBackoffUpToTheCap() {
        assertThat(relay.backoff(1)).isEqualTo(Duration.ofSeconds(1));
        assertThat(relay.backoff(2)).isEqualTo(Duration.ofSeconds(2));
        assertThat(relay.backoff(3)).isEqualTo(Duration.ofSeconds(4));
        assertThat(relay.backoff(4)).isEqualTo(Duration.ofSeconds(8));
        assertThat(relay.backoff(5)).isEqualTo(MAX_BACKOFF);
        assertThat(relay.backoff(1_000)).isEqualTo(MAX_BACKOFF);
    }

    @Test
    void asksOnlyForEventsDueNow() {
        given(repository.lockNextPendingBatch(any(), anyInt())).willReturn(List.of());
        Instant before = Instant.now();

        relay.publishPending();

        ArgumentCaptor<Instant> now = ArgumentCaptor.forClass(Instant.class);
        verify(repository).lockNextPendingBatch(now.capture(), anyInt());
        assertThat(now.getValue()).isBetween(before, Instant.now());
    }

    @Test
    void rejectsMaxAttemptsBelowOne() {
        assertThatThrownBy(() -> new OutboxRelay(repository, kafkaTemplate, mock(PlatformTransactionManager.class),
                50, Duration.ofSeconds(1), 0, INITIAL_BACKOFF, MAX_BACKOFF))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private void givenSendFails(OutboxEventEntity event) {
        given(repository.lockNextPendingBatch(any(), anyInt())).willReturn(List.of(event));
        given(kafkaTemplate.send(TOPIC, event.getAggregateId(), event.getPayload()))
                .willReturn(CompletableFuture.failedFuture(new RuntimeException("broker down")));
    }

    @Test
    void keepsPublishingRestOfBatchWhenOneEventFails() {
        OutboxEventEntity failing = event("{\"n\":1}");
        OutboxEventEntity succeeding = event("{\"n\":2}");
        given(repository.lockNextPendingBatch(any(), anyInt())).willReturn(List.of(failing, succeeding));
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
        OutboxRelay impatientRelay = new OutboxRelay(repository, kafkaTemplate,
                mock(PlatformTransactionManager.class), 50, Duration.ofMillis(10), MAX_ATTEMPTS, INITIAL_BACKOFF,
                MAX_BACKOFF);
        OutboxEventEntity event = event("{}");
        given(repository.lockNextPendingBatch(any(), anyInt())).willReturn(List.of(event));
        given(kafkaTemplate.send(TOPIC, event.getAggregateId(), event.getPayload()))
                .willReturn(new CompletableFuture<>());

        impatientRelay.publishPending();

        assertThat(event.getPublishedAt()).isNull();
        assertThat(event.getAttempts()).isEqualTo(1);
    }

    @Test
    void stopsAndKeepsInterruptFlagWhenInterrupted() {
        OutboxEventEntity event = event("{}");
        given(repository.lockNextPendingBatch(any(), anyInt())).willReturn(List.of(event));
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
        given(repository.lockNextPendingBatch(any(), anyInt())).willReturn(List.of());

        relay.publishPending();

        verifyNoInteractions(kafkaTemplate);
    }

    @Test
    void asksForConfiguredBatchSize() {
        given(repository.lockNextPendingBatch(any(), anyInt())).willReturn(List.of());

        relay.publishPending();

        verify(repository).lockNextPendingBatch(any(), eq(50));
    }

    private static OutboxEventEntity event(String payload) {
        return new OutboxEventEntity(UUID.randomUUID().toString(), UUID.randomUUID().toString(),
                "PIX_TRANSFER_REQUESTED", TOPIC, payload, Instant.now());
    }
}
