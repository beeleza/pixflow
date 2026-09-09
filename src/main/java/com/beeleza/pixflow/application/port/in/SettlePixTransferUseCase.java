package com.beeleza.pixflow.application.port.in;

import com.beeleza.pixflow.domain.model.PixTransaction;

public interface SettlePixTransferUseCase {

    PixTransaction settle(Command command);

    record Command(
            String idempotencyKey,
            String transactionId,
            boolean accepted,
            String failureReason
    ) {}
}
