package com.beeleza.pixflow.adapter.out.outbox;

import com.beeleza.pixflow.adapter.out.persistence.SpringDataOutboxEventRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class OutboxCleanupTest {

    private static final Duration RETENTION = Duration.ofDays(7);
    private static final int BATCH_SIZE = 1000;

    private final SpringDataOutboxEventRepository repository = mock(SpringDataOutboxEventRepository.class);
    private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    private final OutboxCleanup cleanup = new OutboxCleanup(repository, transactionManager, RETENTION, BATCH_SIZE);

    @Test
    void keepsDeletingBatchesUntilNothingIsLeft() {
        given(repository.deletePublishedBefore(any(), eq(BATCH_SIZE))).willReturn(BATCH_SIZE, BATCH_SIZE, 3, 0);

        cleanup.purgePublished();

        verify(repository, times(4)).deletePublishedBefore(any(), eq(BATCH_SIZE));
    }

    @Test
    void runsEachBatchInItsOwnTransaction() {
        given(repository.deletePublishedBefore(any(), eq(BATCH_SIZE))).willReturn(BATCH_SIZE, 0);

        cleanup.purgePublished();

        verify(transactionManager, times(2)).getTransaction(any());
        verify(transactionManager, times(2)).commit(any());
    }

    @Test
    void stopsAfterOneQueryWhenNothingExpired() {
        given(repository.deletePublishedBefore(any(), eq(BATCH_SIZE))).willReturn(0);

        cleanup.purgePublished();

        verify(repository).deletePublishedBefore(any(), eq(BATCH_SIZE));
    }

    @Test
    void usesTheSameCutoffForEveryBatch() {
        given(repository.deletePublishedBefore(any(), eq(BATCH_SIZE))).willReturn(BATCH_SIZE, 0);
        Instant before = Instant.now().minus(RETENTION);

        cleanup.purgePublished();

        Instant after = Instant.now().minus(RETENTION);
        ArgumentCaptor<Instant> cutoff = ArgumentCaptor.forClass(Instant.class);
        verify(repository, times(2)).deletePublishedBefore(cutoff.capture(), eq(BATCH_SIZE));
        assertThat(cutoff.getAllValues()).hasSize(2).allSatisfy(value -> assertThat(value).isBetween(before, after));
        assertThat(cutoff.getAllValues().get(0)).isEqualTo(cutoff.getAllValues().get(1));
    }
}
