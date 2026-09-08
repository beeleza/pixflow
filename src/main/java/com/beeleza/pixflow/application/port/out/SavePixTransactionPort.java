package com.beeleza.pixflow.application.port.out;

import com.beeleza.pixflow.domain.model.PixTransaction;

/**
 * Persists a transaction and its later state changes.
 */
public interface SavePixTransactionPort {

    PixTransaction save(PixTransaction transaction);
}
