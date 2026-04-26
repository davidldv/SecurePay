package com.securepay.transaction.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.securepay.transaction.domain.OutboxEvent;
import com.securepay.transaction.domain.TransactionRecord;
import com.securepay.transaction.infrastructure.OutboxRepository;
import com.securepay.transaction.infrastructure.TransactionRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class TxRecordOps {

    public static final String TOPIC_COMPLETED = "tx.completed";
    public static final String TOPIC_FAILED = "tx.failed";

    private final TransactionRepository repo;
    private final OutboxRepository outbox;
    private final ObjectMapper mapper;

    public TxRecordOps(TransactionRepository repo, OutboxRepository outbox, ObjectMapper mapper) {
        this.repo = repo;
        this.outbox = outbox;
        this.mapper = mapper;
    }

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
        repo.findById(id).ifPresent(t -> {
            t.markCompleted();
            Map<String, String> payload = new LinkedHashMap<>();
            payload.put("txId", t.getId().toString());
            payload.put("source", t.getSourceAccount().toString());
            payload.put("dest", t.getDestAccount().toString());
            payload.put("amount", t.getAmount().toPlainString());
            payload.put("currency", t.getCurrency());
            payload.put("initiator", t.getInitiatorUser().toString());
            payload.put("completedAt", String.valueOf(t.getCompletedAt()));
            outbox.save(new OutboxEvent(t.getId(), TOPIC_COMPLETED, t.getId().toString(), toJson(payload)));
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(UUID id, String reason) {
        repo.findById(id).ifPresent(t -> {
            t.markFailed(reason);
            Map<String, String> payload = new LinkedHashMap<>();
            payload.put("txId", t.getId().toString());
            payload.put("reason", String.valueOf(t.getFailureReason()));
            outbox.save(new OutboxEvent(t.getId(), TOPIC_FAILED, t.getId().toString(), toJson(payload)));
        });
    }

    @Transactional(readOnly = true)
    public TransactionRecord byId(UUID id) {
        return repo.findById(id).orElseThrow(() -> new IllegalArgumentException("tx not found: " + id));
    }

    private String toJson(Map<String, String> payload) {
        try {
            return mapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("payload serialization failed", e);
        }
    }
}
