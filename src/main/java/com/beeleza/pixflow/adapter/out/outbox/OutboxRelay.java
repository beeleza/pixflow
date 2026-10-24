package com.beeleza.pixflow.adapter.out.outbox;

import com.beeleza.pixflow.adapter.out.persistence.OutboxEventEntity;
import com.beeleza.pixflow.adapter.out.persistence.SpringDataOutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Publishes pending outbox events to Kafka. Delivery is at-least-once: a crash after the broker
 * acknowledges but before the commit sends the event again on the next run.
 *
 * <p>A failed send is retried with exponential backoff, and after {@code max-attempts} the relay gives up
 * on the event. The transaction it belongs to stays {@code PENDING}: a send that timed out may still have
 * reached the broker, so failing the transfer here could contradict what the network settled.
 */
@Component
@EnableScheduling
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final SpringDataOutboxEventRepository repository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final TransactionTemplate transactionTemplate;
    private final int batchSize;
    private final Duration sendTimeout;
    private final int maxAttempts;
    private final Duration initialBackoff;
    private final Duration maxBackoff;

    public OutboxRelay(
            SpringDataOutboxEventRepository repository,
            KafkaTemplate<String, String> kafkaTemplate,
            PlatformTransactionManager transactionManager,
            @Value("${pixflow.outbox.batch-size:50}")
            int batchSize,
            @Value("${pixflow.outbox.send-timeout:5s}")
            Duration sendTimeout,
            @Value("${pixflow.outbox.max-attempts:10}")
            int maxAttempts,
            @Value("${pixflow.outbox.initial-backoff:1s}")
            Duration initialBackoff,
            @Value("${pixflow.outbox.max-backoff:5m}")
            Duration maxBackoff
    ) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("pixflow.outbox.max-attempts must be at least 1");
        }
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.batchSize = batchSize;
        this.sendTimeout = sendTimeout;
        this.maxAttempts = maxAttempts;
        this.initialBackoff = initialBackoff;
        this.maxBackoff = maxBackoff;
    }

    // The row locks from FOR UPDATE SKIP LOCKED only hold inside a transaction, so the whole batch runs in one.
    @Scheduled(fixedDelayString = "${pixflow.outbox.poll-interval:1s}")
    public void publishPending() {
        transactionTemplate.executeWithoutResult(status ->
                repository.lockNextPendingBatch(Instant.now(), batchSize).forEach(this::publish));
    }

    // Failures are recorded on the event instead of thrown, so one bad event does not roll back the batch.
    // send() reports some failures (e.g. an invalid topic) by throwing KafkaException instead of failing the future.
    private void publish(OutboxEventEntity event) {
        try {
            kafkaTemplate.send(event.getTopic(), event.getAggregateId(), event.getPayload())
                    .get(sendTimeout.toMillis(), TimeUnit.MILLISECONDS);
            event.setPublishedAt(Instant.now());
        } catch (ExecutionException | TimeoutException | KafkaException e) {
            recordFailure(event, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Outbox relay interrupted", e);
        }
    }

    private void recordFailure(OutboxEventEntity event, Exception error) {
        int attempts = event.getAttempts() + 1;
        Instant now = Instant.now();
        event.setAttempts(attempts);
        // The wrappers ("Send failed", ExecutionException) say little; the innermost cause says what broke.
        event.setLastError(NestedExceptionUtils.getMostSpecificCause(error).toString());

        if (attempts >= maxAttempts) {
            event.setFailedAt(now);
            log.error("Giving up on outbox event after {} attempts. id={}, aggregateId={}, topic={}, lastError={}",
                    attempts, event.getId(), event.getAggregateId(), event.getTopic(), event.getLastError());
        } else {
            event.setNextAttemptAt(now.plus(backoff(attempts)));
        }
    }

    // initialBackoff * 2^(attempts - 1), capped at maxBackoff. The shift is bounded so it cannot overflow.
    Duration backoff(int attempts) {
        Duration delay = initialBackoff.multipliedBy(1L << Math.min(attempts - 1, 30));
        return delay.compareTo(maxBackoff) > 0 ? maxBackoff : delay;
    }
}
