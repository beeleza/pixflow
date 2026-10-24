package com.beeleza.pixflow.adapter.out.persistence;

import com.beeleza.pixflow.adapter.out.outbox.OutboxCleanup;
import com.beeleza.pixflow.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OutboxEventRepositoryIT extends AbstractIntegrationTest {

    private static final Instant NOW = Instant.now();

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private OutboxCleanup outboxCleanup;

    private TransactionTemplate transaction() {
        return new TransactionTemplate(transactionManager);
    }

    @Test
    void locksOnlyPendingEventsOldestFirstUpToTheLimit() {
        OutboxEventEntity second = save(pending(NOW.minusSeconds(20)));
        save(published(NOW.minusSeconds(40), NOW.minusSeconds(30)));
        OutboxEventEntity first = save(pending(NOW.minusSeconds(50)));
        save(pending(NOW.minusSeconds(10)));

        List<String> locked = lockDueNow(2);

        assertThat(locked).containsExactly(first.getId(), second.getId());
    }

    // A failing event waits for its retry without holding back the events behind it.
    @Test
    void skipsEventsWaitingForTheirNextAttempt() {
        OutboxEventEntity backingOff = pending(NOW.minusSeconds(60));
        backingOff.setAttempts(3);
        backingOff.setNextAttemptAt(Instant.now().plusSeconds(60));
        save(backingOff);
        OutboxEventEntity due = save(pending(NOW.minusSeconds(10)));

        assertThat(lockDueNow(10)).containsExactly(due.getId());
    }

    @Test
    void picksEventsUpAgainOnceTheirRetryIsDue() {
        OutboxEventEntity retried = pending(NOW.minusSeconds(60));
        retried.setAttempts(1);
        retried.setNextAttemptAt(NOW.minusSeconds(1));
        save(retried);

        assertThat(lockDueNow(10)).containsExactly(retried.getId());
    }

    @Test
    void neverPicksAbandonedEvents() {
        OutboxEventEntity abandoned = pending(NOW.minusSeconds(60));
        abandoned.setAttempts(10);
        abandoned.setFailedAt(NOW.minusSeconds(30));
        save(abandoned);

        assertThat(lockDueNow(10)).isEmpty();
    }

    @Test
    void rejectsEventThatIsBothPublishedAndAbandoned() {
        OutboxEventEntity contradictory = published(NOW.minusSeconds(60), NOW.minusSeconds(30));
        contradictory.setFailedAt(NOW.minusSeconds(30));

        assertThatThrownBy(() -> outboxEventRepository.saveAndFlush(contradictory))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // Two relays running at the same time must split the pending events instead of sending them twice.
    @Test
    void skipsEventsAlreadyLockedByAnotherTransaction() throws Exception {
        OutboxEventEntity a = save(pending(NOW.minusSeconds(30)));
        OutboxEventEntity b = save(pending(NOW.minusSeconds(20)));
        OutboxEventEntity c = save(pending(NOW.minusSeconds(10)));
        CountDownLatch firstLocked = new CountDownLatch(1);
        CountDownLatch secondDone = new CountDownLatch(1);

        CompletableFuture<List<String>> firstRelay = CompletableFuture.supplyAsync(() ->
                transaction().execute(status -> {
                    List<String> ids = ids(outboxEventRepository.lockNextPendingBatch(Instant.now(), 2));
                    firstLocked.countDown();
                    await(secondDone);
                    return ids;
                }));
        assertThat(firstLocked.await(10, TimeUnit.SECONDS)).isTrue();

        List<String> secondRelay = lockDueNow(10);
        secondDone.countDown();

        assertThat(firstRelay.get(10, TimeUnit.SECONDS)).containsExactly(a.getId(), b.getId());
        assertThat(secondRelay).containsExactly(c.getId());
    }

    @Test
    void deletesOnlyEventsPublishedBeforeTheCutoff() {
        Instant cutoff = NOW.minus(Duration.ofDays(7));
        OutboxEventEntity expired = save(published(NOW.minus(Duration.ofDays(9)), NOW.minus(Duration.ofDays(8))));
        OutboxEventEntity recent = save(published(NOW.minus(Duration.ofDays(2)), NOW.minus(Duration.ofDays(1))));
        OutboxEventEntity oldButPending = save(pending(NOW.minus(Duration.ofDays(30))));
        OutboxEventEntity oldAbandoned = pending(NOW.minus(Duration.ofDays(30)));
        oldAbandoned.setFailedAt(NOW.minus(Duration.ofDays(29)));
        save(oldAbandoned);

        Integer deleted = transaction().execute(status -> outboxEventRepository.deletePublishedBefore(cutoff, 100));

        assertThat(deleted).isEqualTo(1);
        assertThat(outboxEventRepository.findById(expired.getId())).isEmpty();
        assertThat(outboxEventRepository.findById(recent.getId())).isPresent();
        assertThat(outboxEventRepository.findById(oldButPending.getId())).isPresent();
        assertThat(outboxEventRepository.findById(oldAbandoned.getId())).isPresent();
    }

    @Test
    void deletesAtMostTheBatchSize() {
        for (int i = 0; i < 3; i++) {
            save(published(NOW.minus(Duration.ofDays(10)), NOW.minus(Duration.ofDays(9))));
        }
        Instant cutoff = NOW.minus(Duration.ofDays(7));

        Integer firstBatch = transaction().execute(status -> outboxEventRepository.deletePublishedBefore(cutoff, 2));
        Integer secondBatch = transaction().execute(status -> outboxEventRepository.deletePublishedBefore(cutoff, 2));

        assertThat(firstBatch).isEqualTo(2);
        assertThat(secondBatch).isEqualTo(1);
        assertThat(outboxEventRepository.count()).isZero();
    }

    @Test
    void cleanupJobPurgesExpiredEventsAndKeepsTheRest() {
        for (int i = 0; i < 5; i++) {
            save(published(NOW.minus(Duration.ofDays(10)), NOW.minus(Duration.ofDays(8))));
        }
        OutboxEventEntity recent = save(published(NOW.minus(Duration.ofDays(1)), NOW.minusSeconds(60)));
        OutboxEventEntity pending = save(pending(NOW.minus(Duration.ofDays(30))));

        outboxCleanup.purgePublished();

        assertThat(ids(outboxEventRepository.findAll())).containsExactlyInAnyOrder(recent.getId(), pending.getId());
    }

    private OutboxEventEntity save(OutboxEventEntity event) {
        return outboxEventRepository.save(event);
    }

    private List<String> lockDueNow(int limit) {
        return transaction().execute(status -> ids(outboxEventRepository.lockNextPendingBatch(Instant.now(), limit)));
    }

    private static OutboxEventEntity pending(Instant createdAt) {
        return new OutboxEventEntity(UUID.randomUUID().toString(), UUID.randomUUID().toString(),
                "PIX_TRANSFER_REQUESTED", "pix-transaction", "{}", createdAt);
    }

    private static OutboxEventEntity published(Instant createdAt, Instant publishedAt) {
        OutboxEventEntity event = pending(createdAt);
        event.setPublishedAt(publishedAt);
        return event;
    }

    private static List<String> ids(List<OutboxEventEntity> events) {
        return events.stream().map(OutboxEventEntity::getId).toList();
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
