package com.beeleza.pixflow.adapter.out.persistence;

import com.beeleza.pixflow.domain.model.PixKeyType;
import com.beeleza.pixflow.domain.model.TransactionStatus;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "pix_transactions")
public class PixTransactionEntity {

    @Id
    private String id;

    @Column(name = "idempotency_key", nullable = false, unique = true)
    private String idempotencyKey;

    @Column(name = "source_account", nullable = false)
    private String sourceAccount;

    @Column(name = "destination_key", nullable = false)
    private String destinationKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "destination_key_type", nullable = false)
    private PixKeyType destinationKeyType;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private TransactionStatus status;

    @Column(name = "settled_at")
    private Instant settledAt;

    @Column(name = "failure_reason")
    private String failureReason;

    protected PixTransactionEntity() {
    }

    public PixTransactionEntity(String id, String idempotencyKey, String sourceAccount, String destinationKey,
                                PixKeyType destinationKeyType, BigDecimal amount, Instant createdAt,
                                TransactionStatus status, Instant settledAt, String failureReason) {
        this.id = id;
        this.idempotencyKey = idempotencyKey;
        this.sourceAccount = sourceAccount;
        this.destinationKey = destinationKey;
        this.destinationKeyType = destinationKeyType;
        this.amount = amount;
        this.createdAt = createdAt;
        this.status = status;
        this.settledAt = settledAt;
        this.failureReason = failureReason;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public String getSourceAccount() {
        return sourceAccount;
    }

    public void setSourceAccount(String sourceAccount) {
        this.sourceAccount = sourceAccount;
    }

    public String getDestinationKey() {
        return destinationKey;
    }

    public void setDestinationKey(String destinationKey) {
        this.destinationKey = destinationKey;
    }

    public PixKeyType getDestinationKeyType() {
        return destinationKeyType;
    }

    public void setDestinationKeyType(PixKeyType destinationKeyType) {
        this.destinationKeyType = destinationKeyType;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public TransactionStatus getStatus() {
        return status;
    }

    public void setStatus(TransactionStatus status) {
        this.status = status;
    }

    public Instant getSettledAt() {
        return settledAt;
    }

    public void setSettledAt(Instant settledAt) {
        this.settledAt = settledAt;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }
}
