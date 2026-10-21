package com.beeleza.pixflow.application.port.in;

import com.beeleza.pixflow.domain.model.PixTransaction;
import com.beeleza.pixflow.domain.model.TransactionStatus;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public interface ListPixTransactionsUseCase {

    PixTransactionPage list(Query query);

    record Query(UUID sourceAccountId, TransactionStatus status, int page, int size) {

        public static final int MAX_SIZE = 100;

        public Query {
            Objects.requireNonNull(sourceAccountId, "sourceAccountId must not be null");
            if (page < 0) throw new IllegalArgumentException("page must be >= 0");
            if (size < 1 || size > MAX_SIZE) throw new IllegalArgumentException("size must be between 1 and " + MAX_SIZE);
        }
    }

    record PixTransactionPage(List<PixTransaction> items, int page, int size, long totalElements) {
        public int totalPages() {
            return (int) Math.ceil((double) totalElements / size);
        }
    }
}
