package com.securepay.transaction.infrastructure;

import com.securepay.transaction.domain.OutboxEvent;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;

import java.util.List;
import java.util.UUID;

public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@jakarta.persistence.QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("""
            SELECT e FROM OutboxEvent e
            WHERE e.sentAt IS NULL AND e.nextAttemptAt <= CURRENT_TIMESTAMP
            ORDER BY e.nextAttemptAt ASC
            """)
    List<OutboxEvent> findBatchForDispatch(Pageable page);
}
