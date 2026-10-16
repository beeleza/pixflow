package com.beeleza.pixflow.application.service;

import com.beeleza.pixflow.application.port.in.ProcessPixTransferUseCase;
import com.beeleza.pixflow.application.port.out.LoadPixTransactionPort;
import com.beeleza.pixflow.application.port.out.PixNetworkException;
import com.beeleza.pixflow.application.port.out.PixNetworkGateway;
import com.beeleza.pixflow.application.port.out.SavePixTransactionPort;
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

        try {
            return loadPixTransaction.findByIdempotencyKey(idempotencyKey)
                    .map(existing -> ensureSameRequest(existing, sourceAccount, destinationKey, amount))
                    .orElseGet(() -> settleNewTransfer(idempotencyKey, sourceAccount, destinationKey, amount));
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
        // Keep the instance returned by save: it carries the version needed for the next update.
        PixTransaction transaction = savePixTransaction.save(PixTransaction.initiate(
                idempotencyKey, sourceAccount, destinationKey, amount));

        try {
            pixNetworkGateway.dispatch(transaction);
        } catch (PixNetworkException e) {
            transaction.fail(e.getMessage());
            transaction = savePixTransaction.save(transaction);
        }

        return transaction;
    }
}
