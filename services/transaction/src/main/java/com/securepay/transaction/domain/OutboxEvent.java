package com.securepay.transaction.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox_event")
public class OutboxEvent {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "aggregate_id", nullable = false, columnDefinition = "uuid")
    private UUID aggregateId;

    @Column(nullable = false, length = 64)
    private String topic;

    @Column(name = "msg_key", nullable = false, length = 128)
    private String msgKey;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "last_error")
    private String lastError;

    protected OutboxEvent() {}

    public OutboxEvent(UUID aggregateId, String topic, String msgKey, String payload) {
        this.aggregateId = aggregateId;
        this.topic = topic;
        this.msgKey = msgKey;
        this.payload = payload;
        this.createdAt = Instant.now();
        this.nextAttemptAt = this.createdAt;
        this.attempts = 0;
    }

    public void markSent() {
        this.sentAt = Instant.now();
        this.lastError = null;
    }

    public void recordFailure(String error, Instant retryAt) {
        this.attempts += 1;
        this.lastError = error;
        this.nextAttemptAt = retryAt;
    }

    public UUID getId() { return id; }
    public UUID getAggregateId() { return aggregateId; }
    public String getTopic() { return topic; }
    public String getMsgKey() { return msgKey; }
    public String getPayload() { return payload; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getSentAt() { return sentAt; }
    public int getAttempts() { return attempts; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
    public String getLastError() { return lastError; }
}
