package com.beeleza.pixflow.domain.model;

import com.beeleza.pixflow.domain.exception.InvalidIdempotencyKeyException;

/**
 * Client-supplied key that makes a transfer request safe to retry.
 */
public record IdempotencyKey(String value) {

    public IdempotencyKey {
        if (value == null) {
            throw new InvalidIdempotencyKeyException("value must not be null");
        }
        value = value.strip();
        if (value.isEmpty()) {
            throw new InvalidIdempotencyKeyException("value must not be blank");
        }
    }

    public static IdempotencyKey of(String value) {
        return new IdempotencyKey(value);
    }
}
