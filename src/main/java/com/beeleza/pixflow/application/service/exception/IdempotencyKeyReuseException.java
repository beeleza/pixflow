package com.beeleza.pixflow.application.service.exception;

/**
 * Raised when an idempotency key that already produced a transaction is sent
 * again with a different transfer request.
 */
public class IdempotencyKeyReuseException extends RuntimeException {
    public IdempotencyKeyReuseException(String message) {
        super(message);
    }
}
