package com.beeleza.pixflow.adapter.in.web.dto;

import java.math.BigDecimal;

public record CreatePixTransferRequest(
        String idempotencyKey,
        String sourceAccountId,
        String destinationKey,
        String destinationKeyType,
        BigDecimal amount
) {
}
