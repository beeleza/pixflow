package com.beeleza.pixflow.application.service;

import com.beeleza.pixflow.application.port.in.ProcessPixTransferUseCase;
import com.beeleza.pixflow.application.port.out.LoadPixTransactionPort;
import com.beeleza.pixflow.application.port.out.PixTransferEventsPort;
import com.beeleza.pixflow.application.port.out.SavePixTransactionPort;
import com.beeleza.pixflow.application.port.out.TransactionRunner;
import com.beeleza.pixflow.application.port.out.TransferLockPort;
import com.beeleza.pixflow.application.service.exception.IdempotencyKeyReuseException;
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
    private final PixTransferEventsPort pixTransferEvents;
    private final TransferLockPort transferLock;
    private final TransactionRunner transactionRunner;

    public ProcessPixTransferService(LoadPixTransactionPort loadPixTransaction,
                                     SavePixTransactionPort savePixTransaction,
                                     PixTransferEventsPort pixTransferEvents,
                                     TransferLockPort transferLock,
                                     TransactionRunner transactionRunner) {
        this.loadPixTransaction = loadPixTransaction;
        this.savePixTransaction = savePixTransaction;
        this.pixTransferEvents = pixTransferEvents;
        this.transferLock = transferLock;
        this.transactionRunner = transactionRunner;
    }

    @Override
    public PixTransaction process(Command command) {
        IdempotencyKey idempotencyKey = IdempotencyKey.of(command.idempotencyKey());
        // Built up front so a replay is compared on normalized values (stripped key, 2-decimal amount).
        AccountId sourceAccount = new AccountId(command.sourceAccountId());
        PixKey destinationKey = new PixKey(command.destinationKey(), command.destinationKeyType());
        Money amount = new Money(command.amount());

        Optional<String> lockToken = transferLock.acquire(idempotencyKey);
        if (lockToken.isEmpty()) {
            return loadPixTransaction.findByIdempotencyKey(idempotencyKey)
                    .map(existing -> ensureSameRequest(existing, sourceAccount, destinationKey, amount))
                    .orElseThrow(() -> new PixTransferInProgressException(
                            "Transfer already being processed: " + idempotencyKey.value()));
        }

        // The transaction commits before the lock is released, so the next lock holder sees this transfer.
        try {
            return transactionRunner.run(() -> loadPixTransaction.findByIdempotencyKey(idempotencyKey)
                    .map(existing -> ensureSameRequest(existing, sourceAccount, destinationKey, amount))
                    .orElseGet(() -> settleNewTransfer(idempotencyKey, sourceAccount, destinationKey, amount)));
        } finally {
            transferLock.release(idempotencyKey, lockToken.get());
        }
    }

    private static PixTransaction ensureSameRequest(PixTransaction existing, AccountId sourceAccount,
                                                    PixKey destinationKey, Money amount) {
        if (!existing.isSameRequest(sourceAccount, destinationKey, amount)) {
            throw new IdempotencyKeyReuseException(
                    "Idempotency key " + existing.idempotencyKey().value()
                            + " was already used for a different transfer");
        }
        return existing;
    }

    private PixTransaction settleNewTransfer(IdempotencyKey idempotencyKey, AccountId sourceAccount,
                                             PixKey destinationKey, Money amount) {
        PixTransaction transaction = savePixTransaction.save(PixTransaction.initiate(
                idempotencyKey, sourceAccount, destinationKey, amount));

        pixTransferEvents.transferRequested(transaction);

        return transaction;
    }
}
