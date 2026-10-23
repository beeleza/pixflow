package com.beeleza.pixflow.adapter.out.persistence.mapper;

import com.beeleza.pixflow.adapter.out.persistence.PixTransactionEntity;
import com.beeleza.pixflow.domain.model.AccountId;
import com.beeleza.pixflow.domain.model.IdempotencyKey;
import com.beeleza.pixflow.domain.model.Money;
import com.beeleza.pixflow.domain.model.PixKey;
import com.beeleza.pixflow.domain.model.PixKeyType;
import com.beeleza.pixflow.domain.model.PixTransaction;
import com.beeleza.pixflow.domain.model.TransactionId;
import com.beeleza.pixflow.domain.model.TransactionStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PixTransactionMapperTest {

    private final PixTransactionMapper mapper = new PixTransactionMapper();

    @Test
    void keepsEveryFieldOnRoundTrip() {
        PixTransaction original = PixTransaction.reconstitute(
                TransactionId.generate(),
                IdempotencyKey.of("key-1"),
                new AccountId(UUID.randomUUID()),
                new PixKey("+5511987654321", PixKeyType.PHONE),
                Money.of("42.10"),
                Instant.parse("2026-10-21T14:30:00Z"),
                TransactionStatus.FAILED,
                Instant.parse("2026-10-21T14:30:05Z"),
                "INVALID_DESTINATION_KEY",
                3L);

        PixTransaction copy = mapper.toDomain(mapper.toEntity(original));

        assertThat(copy).usingRecursiveComparison().isEqualTo(original);
    }

    @Test
    void writesColumnsInTheirStoredFormat() {
        PixTransaction transaction = PixTransaction.initiate(
                IdempotencyKey.of("key-1"),
                new AccountId(UUID.randomUUID()),
                new PixKey("fulano@email.com", PixKeyType.EMAIL),
                Money.of("10.5"));

        PixTransactionEntity entity = mapper.toEntity(transaction);

        assertThat(entity.getId()).isEqualTo(transaction.id().toString());
        assertThat(entity.getSourceAccount()).isEqualTo(transaction.sourceAccount().toString());
        assertThat(entity.getDestinationKeyType()).isEqualTo(PixKeyType.EMAIL);
        assertThat(entity.getAmount()).isEqualByComparingTo("10.50");
        assertThat(entity.getStatus()).isEqualTo(TransactionStatus.PENDING);
        assertThat(entity.getVersion()).isNull();
    }

    @Test
    void mapsNullToNull() {
        assertThat(mapper.toDomain(null)).isNull();
        assertThat(mapper.toEntity(null)).isNull();
    }
}
