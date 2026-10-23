package com.beeleza.pixflow.adapter.out.outbox;

import com.beeleza.pixflow.adapter.out.persistence.SpringDataOutboxEventRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;

/**
 * Deletes outbox events that were published longer ago than the retention period.
 * Events that were never published are kept.
 */
@Component
public class OutboxCleanup {

    private final SpringDataOutboxEventRepository repository;
    private final TransactionTemplate transactionTemplate;
    private final Duration retention;
    private final int deleteBatchSize;

    public OutboxCleanup(
            SpringDataOutboxEventRepository repository,
            PlatformTransactionManager transactionManager,
            @Value("${pixflow.outbox.retention:7d}")
            Duration retention,
            @Value("${pixflow.outbox.cleanup-batch-size:1000}")
            int deleteBatchSize
    ) {
        this.repository = repository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.retention = retention;
        this.deleteBatchSize = deleteBatchSize;
    }

    // Each batch commits on its own, so the table is never locked by one large delete.
    @Scheduled(cron = "${pixflow.outbox.cleanup-cron:0 0 * * * *}")
    public void purgePublished() {
        Instant cutoff = Instant.now().minus(retention);
        Integer deleted;
        do {
            deleted = transactionTemplate.execute(status ->
                    repository.deletePublishedBefore(cutoff, deleteBatchSize));
        } while (deleted != null && deleted > 0);
    }
}
