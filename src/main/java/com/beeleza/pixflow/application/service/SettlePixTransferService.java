package com.beeleza.pixflow.application.service;

import com.beeleza.pixflow.application.port.in.SettlePixTransferUseCase;
import com.beeleza.pixflow.application.port.out.LoadPixTransactionPort;
import com.beeleza.pixflow.application.port.out.SavePixTransactionPort;
import com.beeleza.pixflow.application.service.exception.TransactionNotFoundException;
import com.beeleza.pixflow.domain.model.IdempotencyKey;
import com.beeleza.pixflow.domain.model.PixTransaction;
import org.springframework.stereotype.Service;

@Service
public class SettlePixTransferService implements SettlePixTransferUseCase {

    private final LoadPixTransactionPort loadPixTransactionPort;
    private final SavePixTransactionPort savePixTransactionPort;

    public SettlePixTransferService(LoadPixTransactionPort loadPixTransactionPort, SavePixTransactionPort savePixTransactionPort) {
        this.loadPixTransactionPort = loadPixTransactionPort;
        this.savePixTransactionPort = savePixTransactionPort;
    }

    @Override
    public PixTransaction settle(Command command) {
        IdempotencyKey idempotencyKey = IdempotencyKey.of(command.idempotencyKey());
        PixTransaction pixTransaction = loadPixTransactionPort
                .findByIdempotencyKey(idempotencyKey)
                .orElseThrow(() ->
                        new TransactionNotFoundException(
                                "Transaction not found: " + idempotencyKey
                        ));

        if (command.accepted()) {
            pixTransaction.complete();
        } else {
            pixTransaction.fail(command.failureReason());
        }

        return savePixTransactionPort.save(pixTransaction);
    }
}
