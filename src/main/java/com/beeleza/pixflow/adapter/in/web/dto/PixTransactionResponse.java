package com.beeleza.pixflow.adapter.in.web.dto;

import com.beeleza.pixflow.domain.model.PixTransaction;

import java.math.BigDecimal;
import java.time.Instant;

public record PixTransactionResponse(
        String id,
        String idempotencyKey,
        String sourceAccount,
        String destinationKey,
        String destinationKeyType,
        BigDecimal amount,
        Instant createdAt,
        String status,
        Instant settledAt,
        String failureReason
) {

    public static PixTransactionResponse from(PixTransaction transaction) {
        return new PixTransactionResponse(
                transaction.id().toString(),
                transaction.idempotencyKey().value(),
                transaction.sourceAccount().toString(),
                transaction.destinationKey().value(),
                transaction.destinationKey().type().name(),
                transaction.amount().amount(),
                transaction.createdAt(),
                transaction.status().name(),
                transaction.settledAt(),
                transaction.failureReason()
        );
    }
}
