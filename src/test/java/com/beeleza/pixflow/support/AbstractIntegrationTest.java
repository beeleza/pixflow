package com.beeleza.pixflow.support;

import com.beeleza.pixflow.adapter.out.persistence.SpringDataOutboxEventRepository;
import com.beeleza.pixflow.adapter.out.persistence.SpringDataPixTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;

/**
 * Base for tests against real Postgres, Kafka and Redis. Every subclass shares one Spring context,
 * so the containers start once per run.
 *
 * <p>The scheduled jobs are effectively turned off (the relay runs once at startup, on an empty table,
 * and the cleanup never), so tests decide when the outbox is published or purged.
 */
@SpringBootTest(properties = {
        "pixflow.outbox.poll-interval=1h",
        "pixflow.outbox.cleanup-cron=-"
})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
public abstract class AbstractIntegrationTest {

    @Autowired
    protected SpringDataPixTransactionRepository pixTransactionRepository;

    @Autowired
    protected SpringDataOutboxEventRepository outboxEventRepository;

    @BeforeEach
    void cleanDatabase() {
        outboxEventRepository.deleteAllInBatch();
        pixTransactionRepository.deleteAllInBatch();
    }
}
