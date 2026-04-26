package com.securepay.transaction.application;

import com.securepay.transaction.domain.OutboxEvent;
import com.securepay.transaction.infrastructure.OutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxRepository repo;
    private final KafkaTemplate<String, String> kafka;
    private final int batchSize;
    private final Duration sendTimeout;

    public OutboxRelay(OutboxRepository repo,
                       KafkaTemplate<String, String> kafka,
                       @Value("${securepay.outbox.batch-size:50}") int batchSize,
                       @Value("${securepay.outbox.send-timeout-ms:5000}") long sendTimeoutMs) {
        this.repo = repo;
        this.kafka = kafka;
        this.batchSize = batchSize;
        this.sendTimeout = Duration.ofMillis(sendTimeoutMs);
    }

    @Scheduled(fixedDelayString = "${securepay.outbox.poll-ms:1000}")
    @Transactional
    public void dispatch() {
        List<OutboxEvent> batch = repo.findBatchForDispatch(PageRequest.of(0, batchSize));
        if (batch.isEmpty()) return;

        for (OutboxEvent e : batch) {
            try {
                kafka.send(e.getTopic(), e.getMsgKey(), e.getPayload())
                        .get(sendTimeout.toMillis(), TimeUnit.MILLISECONDS);
                e.markSent();
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                e.recordFailure("interrupted", nextRetry(e.getAttempts() + 1));
                break;
            } catch (ExecutionException | TimeoutException ex) {
                String reason = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
                log.warn("outbox dispatch failed id={} attempts={} err={}", e.getId(), e.getAttempts() + 1, reason);
                e.recordFailure(truncate(reason), nextRetry(e.getAttempts() + 1));
            }
        }
    }

    private static Instant nextRetry(int attempts) {
        long backoffSec = (long) Math.min(300, Math.pow(2, Math.min(attempts, 8)));
        return Instant.now().plusSeconds(backoffSec);
    }

    private static String truncate(String s) {
        if (s == null) return null;
        return s.length() > 500 ? s.substring(0, 500) : s;
    }
}
