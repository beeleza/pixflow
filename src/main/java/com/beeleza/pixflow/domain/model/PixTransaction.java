package com.beeleza.pixflow.domain.model;

import com.beeleza.pixflow.domain.exception.IllegalTransactionStateException;
import com.beeleza.pixflow.domain.exception.InvalidTransactionAmountException;

import java.time.Instant;
import java.util.Objects;

public class PixTransaction {

    private final TransactionId id;
    private final IdempotencyKey idempotencyKey;
    private final AccountId sourceAccount;
    private final PixKey destinationKey;
    private final Money amount;
    private final Instant createdAt;

    private TransactionStatus status;
    private Instant settledAt;
    private String failureReason;

    private PixTransaction(TransactionId id, IdempotencyKey idempotencyKey, AccountId sourceAccount,
                           PixKey destinationKey, Money amount, Instant createdAt,
                           TransactionStatus status, Instant settledAt, String failureReason) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.idempotencyKey = Objects.requireNonNull(idempotencyKey, "idempotencyKey must not be null");
        this.sourceAccount = Objects.requireNonNull(sourceAccount, "sourceAccount must not be null");
        this.destinationKey = Objects.requireNonNull(destinationKey, "destinationKey must not be null");
        this.amount = Objects.requireNonNull(amount, "amount must not be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.settledAt = settledAt;
        this.failureReason = failureReason;
    }

    public static PixTransaction initiate(IdempotencyKey idempotencyKey, AccountId sourceAccount,
                                          PixKey destinationKey, Money amount) {
        if (!amount.isPositive()) {
            throw new InvalidTransactionAmountException("amount must be positive: " + amount.amount());
        }
        return new PixTransaction(TransactionId.generate(), idempotencyKey, sourceAccount,
                destinationKey, amount, Instant.now(), TransactionStatus.PENDING, null, null);
    }

    /**
     * Rebuilds a transaction from persisted state, bypassing the initiation rules.
     */
    public static PixTransaction reconstitute(TransactionId id, IdempotencyKey idempotencyKey,
                                              AccountId sourceAccount, PixKey destinationKey, Money amount,
                                              Instant createdAt, TransactionStatus status,
                                              Instant settledAt, String failureReason) {
        return new PixTransaction(id, idempotencyKey, sourceAccount, destinationKey, amount,
                createdAt, status, settledAt, failureReason);
    }

    public void complete() {
        transitionTo(TransactionStatus.COMPLETED);
    }

    public void fail(String reason) {
        transitionTo(TransactionStatus.FAILED);
        this.failureReason = reason;
    }

    private void transitionTo(TransactionStatus target) {
        if (!status.canTransitionTo(target)) {
            throw new IllegalTransactionStateException(
                    "transaction " + id + " cannot move from " + status + " to " + target);
        }
        this.status = target;
        this.settledAt = Instant.now();
    }

    public TransactionId id() {
        return id;
    }

    public IdempotencyKey idempotencyKey() {
        return idempotencyKey;
    }

    public AccountId sourceAccount() {
        return sourceAccount;
    }

    public PixKey destinationKey() {
        return destinationKey;
    }

    public Money amount() {
        return amount;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public TransactionStatus status() {
        return status;
    }

    public Instant settledAt() {
        return settledAt;
    }

    public String failureReason() {
        return failureReason;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof PixTransaction other && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
