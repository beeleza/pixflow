package com.beeleza.pixflow.adapter.out.persistence;

import com.beeleza.pixflow.application.port.in.ListPixTransactionsUseCase.PixTransactionPage;
import com.beeleza.pixflow.domain.model.AccountId;
import com.beeleza.pixflow.domain.model.IdempotencyKey;
import com.beeleza.pixflow.domain.model.Money;
import com.beeleza.pixflow.domain.model.PixKey;
import com.beeleza.pixflow.domain.model.PixKeyType;
import com.beeleza.pixflow.domain.model.PixTransaction;
import com.beeleza.pixflow.domain.model.TransactionId;
import com.beeleza.pixflow.domain.model.TransactionStatus;
import com.beeleza.pixflow.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class PixTransactionPersistenceAdapterIT extends AbstractIntegrationTest {

    private static final AccountId ACCOUNT = new AccountId(UUID.randomUUID());
    private static final Instant BASE_TIME = Instant.parse("2026-10-21T12:00:00Z");

    @Autowired
    private PixTransactionPersistenceAdapter adapter;

    @Test
    void storesAndLoadsTransactionByIdAndByIdempotencyKey() {
        PixTransaction saved = adapter.save(newTransaction("key-1"));

        PixTransaction byId = adapter.findById(saved.id()).orElseThrow();
        PixTransaction byKey = adapter.findByIdempotencyKey(IdempotencyKey.of("key-1")).orElseThrow();

        assertThat(byId).usingRecursiveComparison().ignoringFields("createdAt").isEqualTo(saved);
        // Postgres keeps microseconds, Instant.now() may carry nanoseconds.
        assertThat(byId.createdAt()).isCloseTo(saved.createdAt(), within(1, ChronoUnit.MICROS));
        assertThat(byKey.id()).isEqualTo(saved.id());
        assertThat(byId.version()).isZero();
    }

    @Test
    void returnsEmptyForUnknownTransaction() {
        assertThat(adapter.findById(TransactionId.generate())).isEmpty();
        assertThat(adapter.findByIdempotencyKey(IdempotencyKey.of("missing"))).isEmpty();
    }

    @Test
    void persistsSettlementAndBumpsVersion() {
        PixTransaction stored = adapter.save(newTransaction("key-1"));

        stored.fail("INVALID_DESTINATION_KEY");
        PixTransaction settled = adapter.save(stored);

        PixTransaction reloaded = adapter.findById(stored.id()).orElseThrow();
        assertThat(reloaded.status()).isEqualTo(TransactionStatus.FAILED);
        assertThat(reloaded.failureReason()).isEqualTo("INVALID_DESTINATION_KEY");
        assertThat(reloaded.settledAt()).isNotNull();
        assertThat(settled.version()).isEqualTo(1L);
    }

    @Test
    void uniqueConstraintRejectsSecondTransactionWithSameIdempotencyKey() {
        adapter.save(newTransaction("key-1"));

        assertThatThrownBy(() -> adapter.save(newTransaction("key-1")))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(pixTransactionRepository.count()).isEqualTo(1);
    }

    // The domain already refuses zero; the CHECK constraint is the last line of defense.
    @Test
    void checkConstraintRejectsNonPositiveAmount() {
        PixTransactionEntity entity = new PixTransactionEntity(UUID.randomUUID().toString(), "key-1",
                ACCOUNT.toString(), "fulano@email.com", PixKeyType.EMAIL, BigDecimal.ZERO, Instant.now(),
                TransactionStatus.PENDING, null, null, null);

        assertThatThrownBy(() -> pixTransactionRepository.saveAndFlush(entity))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void optimisticLockRejectsUpdateBasedOnStaleVersion() {
        TransactionId id = adapter.save(newTransaction("key-1")).id();
        PixTransaction firstCopy = adapter.findById(id).orElseThrow();
        PixTransaction secondCopy = adapter.findById(id).orElseThrow();

        firstCopy.complete();
        adapter.save(firstCopy);
        secondCopy.fail("late rejection");

        assertThatThrownBy(() -> adapter.save(secondCopy)).isInstanceOf(OptimisticLockingFailureException.class);
        assertThat(adapter.findById(id).orElseThrow().status()).isEqualTo(TransactionStatus.COMPLETED);
    }

    @Test
    void listsAccountTransactionsNewestFirst() {
        PixTransaction oldest = adapter.save(storedAt("key-1", ACCOUNT, BASE_TIME));
        PixTransaction middle = adapter.save(storedAt("key-2", ACCOUNT, BASE_TIME.plusSeconds(60)));
        PixTransaction newest = adapter.save(storedAt("key-3", ACCOUNT, BASE_TIME.plusSeconds(120)));
        adapter.save(storedAt("key-other", new AccountId(UUID.randomUUID()), BASE_TIME));

        PixTransactionPage page = adapter.findBySourceAccount(ACCOUNT, null, 0, 20);

        assertThat(page.items()).extracting(PixTransaction::id)
                .containsExactly(newest.id(), middle.id(), oldest.id());
        assertThat(page.totalElements()).isEqualTo(3);
    }

    @Test
    void paginatesAccountTransactions() {
        for (int i = 0; i < 5; i++) {
            adapter.save(storedAt("key-" + i, ACCOUNT, BASE_TIME.plusSeconds(i)));
        }

        PixTransactionPage secondPage = adapter.findBySourceAccount(ACCOUNT, null, 1, 2);
        PixTransactionPage lastPage = adapter.findBySourceAccount(ACCOUNT, null, 2, 2);

        assertThat(secondPage.items()).extracting(t -> t.idempotencyKey().value()).containsExactly("key-2", "key-1");
        assertThat(lastPage.items()).extracting(t -> t.idempotencyKey().value()).containsExactly("key-0");
        assertThat(secondPage.totalElements()).isEqualTo(5);
        assertThat(secondPage.totalPages()).isEqualTo(3);
    }

    @Test
    void filtersAccountTransactionsByStatus() {
        PixTransaction failed = storedAt("key-1", ACCOUNT, BASE_TIME);
        failed.fail("INVALID_AMOUNT");
        adapter.save(failed);
        adapter.save(storedAt("key-2", ACCOUNT, BASE_TIME.plusSeconds(1)));

        PixTransactionPage page = adapter.findBySourceAccount(ACCOUNT, TransactionStatus.FAILED, 0, 20);

        assertThat(page.items()).extracting(PixTransaction::id).containsExactly(failed.id());
        assertThat(page.totalElements()).isEqualTo(1);
    }

    private static PixTransaction newTransaction(String key) {
        return PixTransaction.initiate(IdempotencyKey.of(key), ACCOUNT,
                new PixKey("fulano@email.com", PixKeyType.EMAIL), Money.of("150.50"));
    }

    private static PixTransaction storedAt(String key, AccountId account, Instant createdAt) {
        return PixTransaction.reconstitute(TransactionId.generate(), IdempotencyKey.of(key), account,
                new PixKey("fulano@email.com", PixKeyType.EMAIL), Money.of("10.00"), createdAt,
                TransactionStatus.PENDING, null, null, null);
    }
}
