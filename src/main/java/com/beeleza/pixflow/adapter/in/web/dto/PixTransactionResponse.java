package com.beeleza.pixflow.adapter.in.web.dto;

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
}
