package com.beeleza.pixflow.application.port.in;

import com.beeleza.pixflow.domain.model.PixKeyType;
import com.beeleza.pixflow.domain.model.PixTransaction;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * Initiates a Pix transfer from a source account to a destination key.
 * The call is idempotent on {@link Command#idempotencyKey()}: repeating a
 * command that already produced a transaction returns that same transaction.
 */
public interface ProcessPixTransferUseCase {

    PixTransaction process(Command command);

    /**
     * Raw transfer request as it crosses the boundary. Field formats are only
     * checked for presence here; turning them into domain value objects (and
     * rejecting malformed input) is the use case's job.
     */
    record Command(
            String idempotencyKey,
            UUID sourceAccountId,
            PixKeyType destinationKeyType,
            String destinationKey,
            BigDecimal amount
    ) {

        public Command {
            Objects.requireNonNull(idempotencyKey, "idempotencyKey must not be null");
            Objects.requireNonNull(sourceAccountId, "sourceAccountId must not be null");
            Objects.requireNonNull(destinationKeyType, "destinationKeyType must not be null");
            Objects.requireNonNull(destinationKey, "destinationKey must not be null");
            Objects.requireNonNull(amount, "amount must not be null");
        }
    }
}
