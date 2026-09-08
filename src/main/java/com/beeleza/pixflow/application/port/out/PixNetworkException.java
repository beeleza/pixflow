package com.beeleza.pixflow.application.port.out;

/**
 * Raised by a {@link PixNetworkGateway} when the transfer could not be submitted
 * for a technical reason and its outcome is therefore undefined.
 */
public class PixNetworkException extends RuntimeException {

    public PixNetworkException(String message) {
        super(message);
    }

    public PixNetworkException(String message, Throwable cause) {
        super(message, cause);
    }
}
