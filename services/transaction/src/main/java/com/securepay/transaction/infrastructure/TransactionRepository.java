package com.securepay.transaction.infrastructure;

import com.securepay.transaction.domain.TransactionRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<TransactionRecord, UUID> {
    Optional<TransactionRecord> findByIdempotencyKeyAndSourceAccount(String key, UUID src);
}
