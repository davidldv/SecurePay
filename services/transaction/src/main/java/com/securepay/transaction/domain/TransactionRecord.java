package com.securepay.transaction.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "transactions",
       uniqueConstraints = @UniqueConstraint(name = "uq_txn_idem_src",
               columnNames = {"idempotency_key", "source_account"}))
public class TransactionRecord {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "idempotency_key", nullable = false, length = 64)
    private String idempotencyKey;

    @Column(name = "source_account", nullable = false, columnDefinition = "uuid")
    private UUID sourceAccount;

    @Column(name = "dest_account", nullable = false, columnDefinition = "uuid")
    private UUID destAccount;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private TxStatus status;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "initiator_user", nullable = false, columnDefinition = "uuid")
    private UUID initiatorUser;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected TransactionRecord() {}

    public TransactionRecord(String idempotencyKey, UUID sourceAccount, UUID destAccount,
                             BigDecimal amount, String currency, UUID initiatorUser) {
        this.idempotencyKey = idempotencyKey;
        this.sourceAccount = sourceAccount;
        this.destAccount = destAccount;
        this.amount = amount;
        this.currency = currency;
        this.initiatorUser = initiatorUser;
        this.status = TxStatus.PENDING;
        this.createdAt = Instant.now();
    }

    public void markCompleted() {
        this.status = TxStatus.COMPLETED;
        this.completedAt = Instant.now();
    }

    public void markFailed(String reason) {
        this.status = TxStatus.FAILED;
        this.failureReason = reason;
        this.completedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public UUID getSourceAccount() { return sourceAccount; }
    public UUID getDestAccount() { return destAccount; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public TxStatus getStatus() { return status; }
    public String getFailureReason() { return failureReason; }
    public UUID getInitiatorUser() { return initiatorUser; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getCompletedAt() { return completedAt; }
}
