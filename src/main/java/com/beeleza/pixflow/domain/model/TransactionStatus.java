package com.beeleza.pixflow.domain.model;

import java.util.Set;

public enum TransactionStatus {

    PENDING {
        @Override
        Set<TransactionStatus> allowedNext() {
            return Set.of(COMPLETED, FAILED);
        }
    },
    COMPLETED,
    FAILED;

    Set<TransactionStatus> allowedNext() {
        return Set.of();
    }

    public boolean isFinal() {
        return allowedNext().isEmpty();
    }

    public boolean canTransitionTo(TransactionStatus target) {
        return allowedNext().contains(target);
    }
}
