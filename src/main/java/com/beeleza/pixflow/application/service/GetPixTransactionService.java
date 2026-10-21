package com.beeleza.pixflow.application.service;

import com.beeleza.pixflow.application.port.in.GetPixTransactionUseCase;
import com.beeleza.pixflow.application.port.out.LoadPixTransactionPort;
import com.beeleza.pixflow.application.service.exception.TransactionNotFoundException;
import com.beeleza.pixflow.domain.model.PixTransaction;
import com.beeleza.pixflow.domain.model.TransactionId;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class GetPixTransactionService implements GetPixTransactionUseCase {

    private final LoadPixTransactionPort loadPixTransaction;

    public GetPixTransactionService(LoadPixTransactionPort loadPixTransaction) {
        this.loadPixTransaction = loadPixTransaction;
    }

    @Override
    public PixTransaction get(UUID transactionId) {
        TransactionId id = new TransactionId(transactionId);
        return loadPixTransaction.findById(id)
                .orElseThrow(() -> new TransactionNotFoundException("Transaction not found: " + id));
    }
}
