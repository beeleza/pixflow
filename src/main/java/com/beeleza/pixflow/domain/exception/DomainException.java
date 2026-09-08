package com.beeleza.pixflow.domain.exception;

/**
 * Base type for every violation of a domain rule or invariant.
 */
public abstract class DomainException extends RuntimeException {

    protected DomainException(String message) {
        super(message);
    }

    protected DomainException(String message, Throwable cause) {
        super(message, cause);
    }
}
