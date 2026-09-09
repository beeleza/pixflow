package com.beeleza.pixflow.application.service;

import com.beeleza.pixflow.application.port.in.ProcessPixTransferUseCase;
import com.beeleza.pixflow.application.port.out.LoadPixTransactionPort;
import com.beeleza.pixflow.application.port.out.PixNetworkException;
import com.beeleza.pixflow.application.port.out.PixNetworkGateway;
import com.beeleza.pixflow.application.port.out.SavePixTransactionPort;
import com.beeleza.pixflow.domain.model.AccountId;
import com.beeleza.pixflow.domain.model.IdempotencyKey;
import com.beeleza.pixflow.domain.model.Money;
import com.beeleza.pixflow.domain.model.PixKey;
import com.beeleza.pixflow.domain.model.PixTransaction;
import org.springframework.stereotype.Service;

@Service
public class ProcessPixTransferService implements ProcessPixTransferUseCase {

    private final LoadPixTransactionPort loadPixTransaction;
    private final SavePixTransactionPort savePixTransaction;
    private final PixNetworkGateway pixNetworkGateway;

    public ProcessPixTransferService(LoadPixTransactionPort loadPixTransaction,
                                     SavePixTransactionPort savePixTransaction,
                                     PixNetworkGateway pixNetworkGateway) {
        this.loadPixTransaction = loadPixTransaction;
        this.savePixTransaction = savePixTransaction;
        this.pixNetworkGateway = pixNetworkGateway;
    }

    @Override
    public PixTransaction process(Command command) {
        IdempotencyKey idempotencyKey = IdempotencyKey.of(command.idempotencyKey());
        return loadPixTransaction.findByIdempotencyKey(idempotencyKey)
                .orElseGet(() -> settleNewTransfer(idempotencyKey, command));
    }

    private PixTransaction settleNewTransfer(IdempotencyKey idempotencyKey, Command command) {
        PixTransaction transaction = PixTransaction.initiate(
                idempotencyKey,
                new AccountId(command.sourceAccountId()),
                new PixKey(command.destinationKey(), command.destinationKeyType()),
                new Money(command.amount()));

        savePixTransaction.save(transaction);

        try {
            pixNetworkGateway.dispatch(transaction);
        } catch (PixNetworkException e) {
            transaction.fail(e.getMessage());
            savePixTransaction.save(transaction);
        }

        return transaction;
    }
}
