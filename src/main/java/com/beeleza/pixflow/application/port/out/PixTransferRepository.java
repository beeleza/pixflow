package com.beeleza.pixflow.application.port.out;

import com.beeleza.pixflow.domain.model.PixTransaction;

import java.util.Optional;

public interface PixTransferRepository {

    Optional<PixTransaction> findByIdempotencyKey(String idempotencyKey);

    PixTransaction save(PixTransaction transaction);
}
