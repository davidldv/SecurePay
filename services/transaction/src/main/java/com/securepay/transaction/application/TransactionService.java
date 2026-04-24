package com.securepay.transaction.application;

import com.securepay.transaction.domain.TransactionRecord;
import com.securepay.transaction.domain.TxStatus;
import com.securepay.transaction.infrastructure.AccountClient;
import com.securepay.transaction.infrastructure.TransactionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class TransactionService {

    private final TransactionRepository repo;
    private final TxRecordOps ops;
    private final AccountClient accountClient;
    private final IdempotencyStore idem;
    private final TxEventPublisher events;

    public TransactionService(TransactionRepository repo, TxRecordOps ops,
                              AccountClient accountClient, IdempotencyStore idem,
                              TxEventPublisher events) {
        this.repo = repo;
        this.ops = ops;
        this.accountClient = accountClient;
        this.idem = idem;
        this.events = events;
    }

    public TransactionRecord initiate(UUID userId, String idempotencyKey,
                                      UUID src, UUID dst, BigDecimal amount, String currency) {
        // 1. Redis fast dedupe.
        var cached = idem.existing(idempotencyKey, userId);
        if (cached.isPresent()) {
            return ops.byId(cached.get());
        }

        // 2. Insert PENDING (DB unique index is the hard guard).
        TransactionRecord pending = ops.insertPending(idempotencyKey, src, dst, amount, currency, userId);

        // If the row was already COMPLETED/FAILED from a prior attempt, return it.
        if (pending.getStatus() != TxStatus.PENDING) {
            idem.claim(idempotencyKey, userId, pending.getId());
            return pending;
        }

        idem.claim(idempotencyKey, userId, pending.getId());

        // 3. Execute transfer via account-service.
        try {
            accountClient.transfer(pending.getId(), src, dst, amount, currency);
            ops.markCompleted(pending.getId());
        } catch (AccountClient.AccountCallException e) {
            if (e.status == HttpStatus.CONFLICT) {
                // Duplicate replay recognised downstream = success idempotently.
                ops.markCompleted(pending.getId());
            } else {
                ops.markFailed(pending.getId(), "account-service " + e.status + ": " + e.body);
            }
        } catch (Exception e) {
            ops.markFailed(pending.getId(), e.getMessage());
        }

        TransactionRecord refreshed = ops.byId(pending.getId());
        if (refreshed.getStatus() == TxStatus.COMPLETED) events.publishCompleted(refreshed);
        else if (refreshed.getStatus() == TxStatus.FAILED) events.publishFailed(refreshed);
        return refreshed;
    }

    @Transactional(readOnly = true)
    public TransactionRecord get(UUID userId, UUID id) {
        TransactionRecord t = repo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("transaction not found"));
        if (!t.getInitiatorUser().equals(userId)) throw new SecurityException("forbidden");
        return t;
    }
}
