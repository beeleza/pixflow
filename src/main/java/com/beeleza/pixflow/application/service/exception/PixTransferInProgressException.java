package com.beeleza.pixflow.application.service.exception;

public class PixTransferInProgressException extends RuntimeException {
    public PixTransferInProgressException(String message) {
        super(message);
    }
}
