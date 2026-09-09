package com.beeleza.pixflow.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SpringDataPixTransactionRepository extends JpaRepository<PixTransactionEntity, String> {
    Optional<PixTransactionEntity> findByIdempotencyKey(String idempotencyKey);
}
