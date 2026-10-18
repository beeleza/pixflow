package com.beeleza.pixflow.application.port.out;

import com.beeleza.pixflow.domain.model.PixTransaction;

/**
 * Records that a transfer was requested, so it can be delivered to the PIX network later
 */
public interface PixTransferEventsPort {

    void transferRequested(PixTransaction pixTransaction);
}
