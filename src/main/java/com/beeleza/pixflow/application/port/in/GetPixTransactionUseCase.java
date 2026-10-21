package com.beeleza.pixflow.application.port.in;

import com.beeleza.pixflow.domain.model.PixTransaction;

import java.util.UUID;

/**
 * Looks up a single transaction, letting clients follow a transfer until it settles.
 */
public interface GetPixTransactionUseCase {

    PixTransaction get(UUID transactionId);
}
