package com.beeleza.pixflow.application.port.in;

import com.beeleza.pixflow.application.port.in.ListPixTransactionsUseCase.PixTransactionPage;
import com.beeleza.pixflow.application.port.in.ListPixTransactionsUseCase.Query;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ListPixTransactionsUseCaseTest {

    private static final UUID ACCOUNT = UUID.randomUUID();

    @Test
    void requiresSourceAccount() {
        assertThatThrownBy(() -> new Query(null, null, 0, 20)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsNegativePage() {
        assertThatThrownBy(() -> new Query(ACCOUNT, null, -1, 20)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, Query.MAX_SIZE + 1})
    void rejectsSizeOutOfRange(int size) {
        assertThatThrownBy(() -> new Query(ACCOUNT, null, 0, size)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, Query.MAX_SIZE})
    void acceptsSizeAtTheLimits(int size) {
        assertThatCode(() -> new Query(ACCOUNT, null, 0, size)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @CsvSource({
            "0,  20, 0",
            "1,  20, 1",
            "20, 20, 1",
            "21, 20, 2",
            "100, 10, 10"
    })
    void computesTotalPages(long totalElements, int size, int expectedPages) {
        assertThat(new PixTransactionPage(List.of(), 0, size, totalElements).totalPages()).isEqualTo(expectedPages);
    }
}
