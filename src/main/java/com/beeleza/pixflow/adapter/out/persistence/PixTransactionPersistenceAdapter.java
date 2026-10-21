package com.beeleza.pixflow.adapter.out.persistence;

import com.beeleza.pixflow.adapter.out.persistence.mapper.PixTransactionMapper;
import com.beeleza.pixflow.application.port.in.ListPixTransactionsUseCase;
import com.beeleza.pixflow.application.port.out.LoadPixTransactionPort;
import com.beeleza.pixflow.application.port.out.SavePixTransactionPort;
import com.beeleza.pixflow.domain.model.AccountId;
import com.beeleza.pixflow.domain.model.IdempotencyKey;
import com.beeleza.pixflow.domain.model.PixTransaction;
import com.beeleza.pixflow.domain.model.TransactionId;
import com.beeleza.pixflow.domain.model.TransactionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class PixTransactionPersistenceAdapter implements LoadPixTransactionPort, SavePixTransactionPort {

    private final SpringDataPixTransactionRepository repository;
    private final PixTransactionMapper mapper;

    public PixTransactionPersistenceAdapter(SpringDataPixTransactionRepository repository,
                                            PixTransactionMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public Optional<PixTransaction> findById(TransactionId id) {
        return repository.findById(id.toString())
                .map(mapper::toDomain);
    }

    @Override
    public Optional<PixTransaction> findByIdempotencyKey(IdempotencyKey idempotencyKey) {
        return repository.findByIdempotencyKey(idempotencyKey.value())
                .map(mapper::toDomain);
    }

    @Override
    public ListPixTransactionsUseCase.PixTransactionPage findBySourceAccount(AccountId sourceAccount,
                                                                             TransactionStatus status,
                                                                             int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<PixTransactionEntity> result = status == null
                ? repository.findBySourceAccount(sourceAccount.toString(), pageable)
                : repository.findBySourceAccountAndStatus(sourceAccount.toString(), status, pageable);

        return new ListPixTransactionsUseCase.PixTransactionPage(
                result.map(mapper::toDomain).getContent(), page, size, result.getTotalElements());
    }

    @Override
    public PixTransaction save(PixTransaction transaction) {
        PixTransactionEntity saved = repository.save(mapper.toEntity(transaction));
        return mapper.toDomain(saved);
    }
}
