package com.beeleza.pixflow.adapter.out.outbox;

import com.beeleza.pixflow.adapter.out.persistence.OutboxEventEntity;
import com.beeleza.pixflow.adapter.out.persistence.SpringDataOutboxEventRepository;
import org.springframework.beans.factory.annotation.Value;
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
 */
@Component
@EnableScheduling
public class OutboxRelay {

    private final SpringDataOutboxEventRepository repository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final TransactionTemplate transactionTemplate;
    private final int batchSize;
    private final Duration sendTimeout;

    public OutboxRelay(
            SpringDataOutboxEventRepository repository,
            KafkaTemplate<String, String> kafkaTemplate,
            PlatformTransactionManager transactionManager,
            @Value("${pixflow.outbox.batch-size:50}")
            int batchSize,
            @Value("${pixflow.outbox.send-timeout:5s}")
            Duration sendTimeout
    ) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.batchSize = batchSize;
        this.sendTimeout = sendTimeout;
    }

    // The row locks from FOR UPDATE SKIP LOCKED only hold inside a transaction, so the whole batch runs in one.
    @Scheduled(fixedDelayString = "${pixflow.outbox.poll-interval:1s}")
    public void publishPending() {
        transactionTemplate.executeWithoutResult(status ->
                repository.lockNextPendingBatch(batchSize).forEach(this::publish));
    }

    // Failures are recorded on the event instead of thrown, so one bad event does not roll back the batch.
    private void publish(OutboxEventEntity event) {
        try {
            kafkaTemplate.send(event.getTopic(), event.getAggregateId(), event.getPayload())
                    .get(sendTimeout.toMillis(), TimeUnit.MILLISECONDS);
            event.setPublishedAt(Instant.now());
        } catch (ExecutionException | TimeoutException e) {
            event.setAttempts(event.getAttempts() + 1);
            event.setLastError(e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Outbox relay interrupted", e);
        }
    }
}
