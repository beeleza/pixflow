package com.beeleza.pixflow.application.port.out;

import com.beeleza.pixflow.domain.model.IdempotencyKey;
import com.beeleza.pixflow.domain.model.PixTransaction;

import java.util.Optional;

/**
 * Reads previously stored transactions, used to make {@code process} idempotent.
 */
public interface LoadPixTransactionPort {

    Optional<PixTransaction> findByIdempotencyKey(IdempotencyKey idempotencyKey);
}
