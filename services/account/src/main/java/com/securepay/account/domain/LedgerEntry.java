package com.securepay.account.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "account_ledger")
public class LedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_id", nullable = false, columnDefinition = "uuid")
    private UUID accountId;

    @Column(name = "tx_id", nullable = false, columnDefinition = "uuid")
    private UUID txId;

    @Column(nullable = false)
    private Character direction; // D or C

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "balance_after", nullable = false, precision = 19, scale = 4)
    private BigDecimal balanceAfter;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected LedgerEntry() {}

    public LedgerEntry(UUID accountId, UUID txId, String direction,
                       BigDecimal amount, BigDecimal balanceAfter) {
        this.accountId = accountId;
        this.txId = txId;
        this.direction = direction != null && !direction.isEmpty() ? direction.charAt(0) : null;
        this.amount = amount;
        this.balanceAfter = balanceAfter;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public UUID getAccountId() { return accountId; }
    public UUID getTxId() { return txId; }
    public String getDirection() { return direction == null ? null : direction.toString(); }
    public BigDecimal getAmount() { return amount; }
    public BigDecimal getBalanceAfter() { return balanceAfter; }
    public Instant getCreatedAt() { return createdAt; }
}
