package com.beeleza.pixflow.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SpringDataOutboxEventRepository extends JpaRepository<OutboxEventEntity, String> {

    @Query(value = """
            SELECT *
            FROM outbox_events
            WHERE published_at IS NULL
            ORDER BY created_at ASC
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxEventEntity> lockNextPendingBatch(@Param("limit") int limit);
}
