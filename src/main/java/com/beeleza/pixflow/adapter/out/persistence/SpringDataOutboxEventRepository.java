package com.beeleza.pixflow.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface SpringDataOutboxEventRepository extends JpaRepository<OutboxEventEntity, String> {

    // Events waiting for a retry are skipped, so a failing event never blocks the ones behind it.
    @Query(value = """
            SELECT *
            FROM outbox_events
            WHERE published_at IS NULL
              AND failed_at IS NULL
              AND next_attempt_at <= :now
            ORDER BY next_attempt_at ASC
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxEventEntity> lockNextPendingBatch(@Param("now") Instant now, @Param("limit") int limit);

    // SKIP LOCKED lets cleanups running on several instances split the work instead of waiting on each other.
    @Modifying
    @Query(value = """
            DELETE FROM outbox_events
            WHERE id IN (
                SELECT id FROM outbox_events
                WHERE published_at < :cutoff
                LIMIT :limit
                FOR UPDATE SKIP LOCKED
            )
            """, nativeQuery = true)
    int deletePublishedBefore(@Param("cutoff") Instant cutoff, @Param("limit") int limit);
}
