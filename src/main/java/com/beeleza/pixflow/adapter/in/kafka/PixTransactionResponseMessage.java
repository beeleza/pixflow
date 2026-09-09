package com.beeleza.pixflow.adapter.in.kafka;

public record PixTransactionResponseMessage(
        String transactionId,
        String idempotencyKey,
        boolean accepted,
        String failureReason
) {
}