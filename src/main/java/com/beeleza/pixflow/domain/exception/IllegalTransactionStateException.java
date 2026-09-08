package com.beeleza.pixflow.domain.exception;

public class IllegalTransactionStateException extends DomainException {

    public IllegalTransactionStateException(String message) {
        super(message);
    }
}
