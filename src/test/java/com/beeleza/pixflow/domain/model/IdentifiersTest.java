package com.beeleza.pixflow.domain.model;

import com.beeleza.pixflow.domain.exception.InvalidIdempotencyKeyException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdentifiersTest {

    private static final String UUID_TEXT = "a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d";

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   ", "\t\n"})
    void rejectsMissingIdempotencyKey(String value) {
        assertThatThrownBy(() -> IdempotencyKey.of(value)).isInstanceOf(InvalidIdempotencyKeyException.class);
    }

    @Test
    void stripsIdempotencyKey() {
        assertThat(IdempotencyKey.of("  abc  ")).isEqualTo(IdempotencyKey.of("abc"));
    }

    @Test
    void parsesAccountIdAndPrintsItBack() {
        AccountId id = AccountId.of(UUID_TEXT);

        assertThat(id.value()).isEqualTo(UUID.fromString(UUID_TEXT));
        assertThat(id).hasToString(UUID_TEXT);
    }

    @Test
    void parsesTransactionIdAndPrintsItBack() {
        TransactionId id = TransactionId.of(UUID_TEXT);

        assertThat(id.value()).isEqualTo(UUID.fromString(UUID_TEXT));
        assertThat(id).hasToString(UUID_TEXT);
    }

    // Malformed ids are not domain exceptions, so the HTTP layer answers 400 instead of 422.
    @Test
    void rejectsMalformedIdsWithIllegalArgument() {
        assertThatThrownBy(() -> AccountId.of("not-a-uuid")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TransactionId.of("not-a-uuid")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNullIds() {
        assertThatThrownBy(() -> new AccountId(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new TransactionId(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void generatesDistinctTransactionIds() {
        assertThat(TransactionId.generate()).isNotEqualTo(TransactionId.generate());
    }
}
