package com.beeleza.pixflow.application.port.out;

import com.beeleza.pixflow.domain.model.PixTransaction;

/**
 * Submits a transfer to the Pix network / core banking system.
 */
public interface PixNetworkGateway {
    void dispatch(PixTransaction transaction);
}
