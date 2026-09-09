package com.beeleza.pixflow.adapter.out.persistence;

import com.beeleza.pixflow.adapter.out.persistence.mapper.PixTransactionMapper;
import com.beeleza.pixflow.application.port.out.LoadPixTransactionPort;
import com.beeleza.pixflow.application.port.out.SavePixTransactionPort;
import com.beeleza.pixflow.domain.model.IdempotencyKey;
import com.beeleza.pixflow.domain.model.PixTransaction;
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
    public Optional<PixTransaction> findByIdempotencyKey(IdempotencyKey idempotencyKey) {
        return repository.findByIdempotencyKey(idempotencyKey.value())
                .map(mapper::toDomain);
    }

    @Override
    public PixTransaction save(PixTransaction transaction) {
        PixTransactionEntity saved = repository.save(mapper.toEntity(transaction));
        return mapper.toDomain(saved);
    }
}
