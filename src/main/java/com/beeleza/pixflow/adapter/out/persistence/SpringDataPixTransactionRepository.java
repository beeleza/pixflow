package com.beeleza.pixflow.adapter.out.persistence;

import com.beeleza.pixflow.domain.model.TransactionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SpringDataPixTransactionRepository extends JpaRepository<PixTransactionEntity, String> {
    Optional<PixTransactionEntity> findByIdempotencyKey(String idempotencyKey);

    Page<PixTransactionEntity> findBySourceAccount(String sourceAccount, Pageable pageable);

    Page<PixTransactionEntity> findBySourceAccountAndStatus(String sourceAccount, TransactionStatus status,
                                                            Pageable pageable);
}
