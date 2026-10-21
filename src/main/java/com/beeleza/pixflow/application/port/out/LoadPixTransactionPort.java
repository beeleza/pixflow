package com.beeleza.pixflow.application.port.out;

import com.beeleza.pixflow.application.port.in.ListPixTransactionsUseCase;
import com.beeleza.pixflow.domain.model.AccountId;
import com.beeleza.pixflow.domain.model.IdempotencyKey;
import com.beeleza.pixflow.domain.model.PixTransaction;
import com.beeleza.pixflow.domain.model.TransactionId;
import com.beeleza.pixflow.domain.model.TransactionStatus;

import java.util.Optional;

/**
 * Reads previously stored transactions, both to make {@code process} idempotent and to serve queries.
 */
public interface LoadPixTransactionPort {

    Optional<PixTransaction> findById(TransactionId id);

    Optional<PixTransaction> findByIdempotencyKey(IdempotencyKey idempotencyKey);

    ListPixTransactionsUseCase.PixTransactionPage findBySourceAccount(AccountId sourceAccount, TransactionStatus status, int page, int size);
}
