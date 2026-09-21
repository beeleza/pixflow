package com.beeleza.pixflow.application.port.out;

import com.beeleza.pixflow.domain.model.IdempotencyKey;

import java.util.Optional;

public interface TransferLockPort {

    Optional<String> acquire(IdempotencyKey idempotencyKey);

    void release(IdempotencyKey idempotencyKey, String lockToken);
}
