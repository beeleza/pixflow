package com.beeleza.pixflow.application.service;

import com.beeleza.pixflow.application.port.in.ListPixTransactionsUseCase;
import com.beeleza.pixflow.application.port.out.LoadPixTransactionPort;
import com.beeleza.pixflow.domain.model.AccountId;
import org.springframework.stereotype.Service;

@Service
public class ListPixTransactionsService implements ListPixTransactionsUseCase {

    private final LoadPixTransactionPort loadPixTransaction;

    public ListPixTransactionsService(LoadPixTransactionPort loadPixTransaction) {
        this.loadPixTransaction = loadPixTransaction;
    }

    @Override
    public PixTransactionPage list(Query query) {
        return loadPixTransaction.findBySourceAccount(
                new AccountId(query.sourceAccountId()), query.status(), query.page(), query.size());
    }
}
