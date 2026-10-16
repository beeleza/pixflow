package com.beeleza.pixflow.application.service.exception;

/**
 * Raised when a settlement response refers to a transaction other than the one
 * stored under its idempotency key. Retrying cannot fix it.
 */
public class TransactionMismatchException extends RuntimeException {
    public TransactionMismatchException(String message) {
        super(message);
    }
}
