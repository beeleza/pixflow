package com.beeleza.pixflow.application.service;

import com.beeleza.pixflow.application.port.in.ProcessPixTransferUseCase;
import com.beeleza.pixflow.application.port.out.LoadPixTransactionPort;
import com.beeleza.pixflow.application.port.out.PixNetworkException;
import com.beeleza.pixflow.application.port.out.PixNetworkGateway;
import com.beeleza.pixflow.application.port.out.SavePixTransactionPort;
import com.beeleza.pixflow.application.port.out.TransferLockPort;
import com.beeleza.pixflow.application.service.exception.PixTransferInProgressException;
import com.beeleza.pixflow.domain.model.AccountId;
import com.beeleza.pixflow.domain.model.IdempotencyKey;
import com.beeleza.pixflow.domain.model.Money;
import com.beeleza.pixflow.domain.model.PixKey;
import com.beeleza.pixflow.domain.model.PixTransaction;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class ProcessPixTransferService implements ProcessPixTransferUseCase {

    private final LoadPixTransactionPort loadPixTransaction;
    private final SavePixTransactionPort savePixTransaction;
    private final PixNetworkGateway pixNetworkGateway;
    private final TransferLockPort transferLock;

    public ProcessPixTransferService(LoadPixTransactionPort loadPixTransaction,
                                     SavePixTransactionPort savePixTransaction,
                                     PixNetworkGateway pixNetworkGateway,
                                     TransferLockPort transferLock) {
        this.loadPixTransaction = loadPixTransaction;
        this.savePixTransaction = savePixTransaction;
        this.pixNetworkGateway = pixNetworkGateway;
        this.transferLock = transferLock;
    }

    @Override
    public PixTransaction process(Command command) {
        IdempotencyKey idempotencyKey = IdempotencyKey.of(command.idempotencyKey());

        Optional<String> lockToken = transferLock.acquire(idempotencyKey);
        if (lockToken.isEmpty()) {
            return loadPixTransaction.findByIdempotencyKey(idempotencyKey)
                    .orElseThrow(() -> new PixTransferInProgressException(
                            "Transfer already being processed: " + idempotencyKey.value()));
        }

        try {
            return loadPixTransaction.findByIdempotencyKey(idempotencyKey)
                    .orElseGet(() -> settleNewTransfer(idempotencyKey, command));
        } finally {
            transferLock.release(idempotencyKey, lockToken.get());
        }
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
