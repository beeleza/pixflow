package com.beeleza.pixflow.adapter.out.outbox;

import com.beeleza.pixflow.adapter.out.persistence.OutboxEventEntity;
import com.beeleza.pixflow.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the relay against the real broker. An invalid topic name makes Kafka refuse the send,
 * which stands in for an event that can never be delivered.
 */
class OutboxRelayIT extends AbstractIntegrationTest {

    private static final String UNDELIVERABLE_TOPIC = "invalid topic name!";

    @Autowired
    private OutboxRelay outboxRelay;

    @Value("${pixflow.outbox.max-attempts}")
    private int maxAttempts;

    @Test
    void reschedulesFailedEventAndStillPublishesTheOnesBehindIt() {
        OutboxEventEntity broken = outboxEventRepository.save(event(UNDELIVERABLE_TOPIC, Instant.now().minusSeconds(60)));
        OutboxEventEntity healthy = outboxEventRepository.save(event("pix-transaction", Instant.now().minusSeconds(30)));
        Instant before = Instant.now();

        outboxRelay.publishPending();

        OutboxEventEntity brokenAfter = outboxEventRepository.findById(broken.getId()).orElseThrow();
        assertThat(brokenAfter.getPublishedAt()).isNull();
        assertThat(brokenAfter.getFailedAt()).isNull();
        assertThat(brokenAfter.getAttempts()).isEqualTo(1);
        assertThat(brokenAfter.getLastError()).isNotBlank();
        assertThat(brokenAfter.getNextAttemptAt()).isAfter(before);
        assertThat(outboxEventRepository.findById(healthy.getId()).orElseThrow().getPublishedAt()).isNotNull();
    }

    @Test
    void waitsForTheBackoffBeforeTryingAgain() {
        OutboxEventEntity broken = outboxEventRepository.save(event(UNDELIVERABLE_TOPIC, Instant.now().minusSeconds(60)));

        outboxRelay.publishPending();
        outboxRelay.publishPending();

        assertThat(outboxEventRepository.findById(broken.getId()).orElseThrow().getAttempts()).isEqualTo(1);
    }

    @Test
    void abandonsEventOnItsLastAttempt() {
        OutboxEventEntity broken = event(UNDELIVERABLE_TOPIC, Instant.now().minusSeconds(60));
        broken.setAttempts(maxAttempts - 1);
        outboxEventRepository.save(broken);

        outboxRelay.publishPending();

        OutboxEventEntity after = outboxEventRepository.findById(broken.getId()).orElseThrow();
        assertThat(after.getAttempts()).isEqualTo(maxAttempts);
        assertThat(after.getFailedAt()).isNotNull();
        assertThat(after.getPublishedAt()).isNull();
    }

    private static OutboxEventEntity event(String topic, Instant createdAt) {
        return new OutboxEventEntity(UUID.randomUUID().toString(), UUID.randomUUID().toString(),
                "PIX_TRANSFER_REQUESTED", topic, "{}", createdAt);
    }
}
