package com.beeleza.pixflow.domain.model;

import com.beeleza.pixflow.domain.exception.InvalidPixKeyException;

/**
 * Destination of a Pix transfer, identified by its key value and type.
 */
public record PixKey(String value, PixKeyType type) {

    public PixKey {
        if (type == null) {
            throw new InvalidPixKeyException("type must not be null");
        }
        if (value == null) {
            throw new InvalidPixKeyException("value must not be null");
        }
        value = value.strip();
        if (!type.matches(value)) {
            throw new InvalidPixKeyException("value does not match the format of a " + type + " Pix key");
        }
    }
}
