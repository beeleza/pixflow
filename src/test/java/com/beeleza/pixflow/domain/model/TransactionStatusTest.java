package com.beeleza.pixflow.domain.model;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionStatusTest {

    @ParameterizedTest
    @CsvSource({
            "PENDING,   PENDING,   false",
            "PENDING,   COMPLETED, true",
            "PENDING,   FAILED,    true",
            "COMPLETED, PENDING,   false",
            "COMPLETED, COMPLETED, false",
            "COMPLETED, FAILED,    false",
            "FAILED,    PENDING,   false",
            "FAILED,    COMPLETED, false",
            "FAILED,    FAILED,    false"
    })
    void allowsOnlyTransitionsOutOfPending(TransactionStatus from, TransactionStatus to, boolean allowed) {
        assertThat(from.canTransitionTo(to)).isEqualTo(allowed);
    }

    @ParameterizedTest
    @CsvSource({
            "PENDING,   false",
            "COMPLETED, true",
            "FAILED,    true"
    })
    void settledStatusesAreFinal(TransactionStatus status, boolean expected) {
        assertThat(status.isFinal()).isEqualTo(expected);
    }
}
