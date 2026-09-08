package com.beeleza.pixflow.application.port.out;

import com.beeleza.pixflow.domain.model.PixTransaction;

/**
 * Submits a transfer to the Pix network / core banking system.
 */
public interface PixNetworkGateway {

    /**
     * Sends {@code transaction} for settlement.
     *
     * @return {@link Receipt.Accepted} once the network takes responsibility for
     *         the transfer, or {@link Receipt.Rejected} when it declines it for a
     *         business reason (insufficient funds, unknown key, limit exceeded...).
     * @throws PixNetworkException if the network cannot be reached or fails
     *         technically, leaving the outcome unknown.
     */
    Receipt dispatch(PixTransaction transaction);

    sealed interface Receipt {

        record Accepted() implements Receipt {}

        record Rejected(String reason) implements Receipt {}
    }
}
