package com.beeleza.pixflow.domain.model;

import com.beeleza.pixflow.domain.exception.IllegalTransactionStateException;
import com.beeleza.pixflow.domain.exception.InvalidTransactionAmountException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PixTransactionTest {

    private static final IdempotencyKey KEY = IdempotencyKey.of("9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d");
    private static final AccountId SOURCE = AccountId.of("a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d");
    private static final PixKey DESTINATION = new PixKey("fulano@email.com", PixKeyType.EMAIL);
    private static final Money AMOUNT = new Money(new BigDecimal("150.50"));

    @Test
    void initiatesAsPendingAndNotYetStored() {
        Instant before = Instant.now();

        PixTransaction transaction = PixTransaction.initiate(KEY, SOURCE, DESTINATION, AMOUNT);

        assertThat(transaction.id()).isNotNull();
        assertThat(transaction.status()).isEqualTo(TransactionStatus.PENDING);
        assertThat(transaction.createdAt()).isBetween(before, Instant.now());
        assertThat(transaction.settledAt()).isNull();
        assertThat(transaction.failureReason()).isNull();
        assertThat(transaction.version()).isNull();
    }

    @Test
    void generatesDistinctIdsForEachInitiation() {
        PixTransaction first = PixTransaction.initiate(KEY, SOURCE, DESTINATION, AMOUNT);
        PixTransaction second = PixTransaction.initiate(KEY, SOURCE, DESTINATION, AMOUNT);

        assertThat(first.id()).isNotEqualTo(second.id());
    }

    @Test
    void rejectsZeroAmount() {
        assertThatThrownBy(() -> PixTransaction.initiate(KEY, SOURCE, DESTINATION, Money.of(0)))
                .isInstanceOf(InvalidTransactionAmountException.class);
    }

    @Test
    void completesPendingTransaction() {
        PixTransaction transaction = pending();

        transaction.complete();

        assertThat(transaction.status()).isEqualTo(TransactionStatus.COMPLETED);
        assertThat(transaction.settledAt()).isNotNull();
        assertThat(transaction.failureReason()).isNull();
    }

    @Test
    void failsPendingTransactionWithReason() {
        PixTransaction transaction = pending();

        transaction.fail("INVALID_DESTINATION_KEY");

        assertThat(transaction.status()).isEqualTo(TransactionStatus.FAILED);
        assertThat(transaction.settledAt()).isNotNull();
        assertThat(transaction.failureReason()).isEqualTo("INVALID_DESTINATION_KEY");
    }

    @Test
    void failsPendingTransactionWithoutReason() {
        PixTransaction transaction = pending();

        transaction.fail(null);

        assertThat(transaction.status()).isEqualTo(TransactionStatus.FAILED);
        assertThat(transaction.failureReason()).isNull();
    }

    @Test
    void truncatesFailureReasonToColumnLength() {
        PixTransaction transaction = pending();

        transaction.fail("x".repeat(PixTransaction.MAX_FAILURE_REASON_LENGTH + 1));

        assertThat(transaction.failureReason()).hasSize(PixTransaction.MAX_FAILURE_REASON_LENGTH);
    }

    @Test
    void refusesToCompleteFailedTransactionAndKeepsItsState() {
        PixTransaction transaction = stored(TransactionStatus.FAILED, "INVALID_AMOUNT");

        assertThatThrownBy(transaction::complete).isInstanceOf(IllegalTransactionStateException.class);

        assertThat(transaction.status()).isEqualTo(TransactionStatus.FAILED);
        assertThat(transaction.failureReason()).isEqualTo("INVALID_AMOUNT");
    }

    @Test
    void refusesToFailCompletedTransactionAndKeepsItsState() {
        PixTransaction transaction = stored(TransactionStatus.COMPLETED, null);

        assertThatThrownBy(() -> transaction.fail("late rejection"))
                .isInstanceOf(IllegalTransactionStateException.class);

        assertThat(transaction.status()).isEqualTo(TransactionStatus.COMPLETED);
        assertThat(transaction.failureReason()).isNull();
    }

    @Test
    void refusesToSettleTwice() {
        PixTransaction transaction = pending();
        transaction.complete();

        assertThatThrownBy(transaction::complete).isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void recognizesSameRequest() {
        PixTransaction transaction = pending();

        assertThat(transaction.isSameRequest(SOURCE, DESTINATION, AMOUNT)).isTrue();
        assertThat(transaction.isSameRequest(new AccountId(UUID.randomUUID()), DESTINATION, AMOUNT)).isFalse();
        assertThat(transaction.isSameRequest(SOURCE, new PixKey("ciclano@email.com", PixKeyType.EMAIL), AMOUNT))
                .isFalse();
        assertThat(transaction.isSameRequest(SOURCE, DESTINATION, Money.of("150.51"))).isFalse();
    }

    @Test
    void isIdentifiedOnlyByItsId() {
        PixTransaction original = stored(TransactionStatus.PENDING, null);
        PixTransaction sameIdSettled = PixTransaction.reconstitute(original.id(), KEY, SOURCE, DESTINATION,
                Money.of(1), original.createdAt(), TransactionStatus.COMPLETED, Instant.now(), null, 2L);

        assertThat(sameIdSettled).isEqualTo(original).hasSameHashCodeAs(original);
        assertThat(pending()).isNotEqualTo(original);
    }

    private static PixTransaction pending() {
        return PixTransaction.initiate(KEY, SOURCE, DESTINATION, AMOUNT);
    }

    private static PixTransaction stored(TransactionStatus status, String failureReason) {
        Instant settledAt = status == TransactionStatus.PENDING ? null : Instant.now();
        return PixTransaction.reconstitute(TransactionId.generate(), KEY, SOURCE, DESTINATION, AMOUNT,
                Instant.now(), status, settledAt, failureReason, 0L);
    }
}
