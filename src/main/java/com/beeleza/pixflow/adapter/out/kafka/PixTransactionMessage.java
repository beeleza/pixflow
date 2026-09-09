package com.beeleza.pixflow.adapter.out.kafka;

import java.math.BigDecimal;

public record PixTransactionMessage(
        String transactionId,
        String idempotencyKey,
        String sourceAccount,
        String destinationKey,
        String destinationKeyType,
        BigDecimal amount
) {
}
