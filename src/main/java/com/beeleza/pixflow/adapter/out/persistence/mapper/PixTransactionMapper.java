package com.beeleza.pixflow.adapter.out.persistence.mapper;

import com.beeleza.pixflow.adapter.out.persistence.PixTransactionEntity;
import com.beeleza.pixflow.domain.model.AccountId;
import com.beeleza.pixflow.domain.model.IdempotencyKey;
import com.beeleza.pixflow.domain.model.Money;
import com.beeleza.pixflow.domain.model.PixKey;
import com.beeleza.pixflow.domain.model.PixTransaction;
import com.beeleza.pixflow.domain.model.TransactionId;
import org.springframework.stereotype.Component;

@Component
public class PixTransactionMapper {

    public PixTransaction toDomain(PixTransactionEntity entity) {
        if (entity == null) {
            return null;
        }

        return PixTransaction.reconstitute(
                TransactionId.of(entity.getId()),
                IdempotencyKey.of(entity.getIdempotencyKey()),
                AccountId.of(entity.getSourceAccount()),
                new PixKey(entity.getDestinationKey(), entity.getDestinationKeyType()),
                new Money(entity.getAmount()),
                entity.getCreatedAt(),
                entity.getStatus(),
                entity.getSettledAt(),
                entity.getFailureReason()
        );
    }

    public PixTransactionEntity toEntity(PixTransaction domain) {
        if (domain == null) {
            return null;
        }

        return new PixTransactionEntity(
                domain.id().value().toString(),
                domain.idempotencyKey().value(),
                domain.sourceAccount().value().toString(),
                domain.destinationKey().value(),
                domain.destinationKey().type(),
                domain.amount().amount(),
                domain.createdAt(),
                domain.status(),
                domain.settledAt(),
                domain.failureReason()
        );
    }
}
