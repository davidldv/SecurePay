package com.securepay.transaction.application;

import com.securepay.transaction.domain.TransactionRecord;
import com.securepay.transaction.infrastructure.TransactionRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class TxRecordOps {

    private final TransactionRepository repo;

    public TxRecordOps(TransactionRepository repo) { this.repo = repo; }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public TransactionRecord insertPending(String key, UUID src, UUID dst,
                                           BigDecimal amount, String currency, UUID user) {
        try {
            return repo.saveAndFlush(new TransactionRecord(key, src, dst, amount, currency, user));
        } catch (DataIntegrityViolationException dup) {
            return repo.findByIdempotencyKeyAndSourceAccount(key, src)
                    .orElseThrow(() -> new IllegalStateException("unique violation without existing row"));
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markCompleted(UUID id) {
        repo.findById(id).ifPresent(TransactionRecord::markCompleted);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(UUID id, String reason) {
        repo.findById(id).ifPresent(t -> t.markFailed(reason));
    }

    @Transactional(readOnly = true)
    public TransactionRecord byId(UUID id) {
        return repo.findById(id).orElseThrow(() -> new IllegalArgumentException("tx not found: " + id));
    }
}
