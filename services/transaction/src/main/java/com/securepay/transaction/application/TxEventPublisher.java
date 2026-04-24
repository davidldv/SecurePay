package com.securepay.transaction.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.securepay.transaction.domain.TransactionRecord;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class TxEventPublisher {

    public static final String TOPIC_COMPLETED = "tx.completed";
    public static final String TOPIC_FAILED = "tx.failed";

    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper mapper;

    public TxEventPublisher(KafkaTemplate<String, String> kafka, ObjectMapper mapper) {
        this.kafka = kafka;
        this.mapper = mapper;
    }

    public void publishCompleted(TransactionRecord tx) {
        publish(TOPIC_COMPLETED, tx.getId().toString(), Map.of(
                "txId", tx.getId().toString(),
                "source", tx.getSourceAccount().toString(),
                "dest", tx.getDestAccount().toString(),
                "amount", tx.getAmount().toPlainString(),
                "currency", tx.getCurrency(),
                "initiator", tx.getInitiatorUser().toString(),
                "completedAt", String.valueOf(tx.getCompletedAt())
        ));
    }

    public void publishFailed(TransactionRecord tx) {
        publish(TOPIC_FAILED, tx.getId().toString(), Map.of(
                "txId", tx.getId().toString(),
                "reason", String.valueOf(tx.getFailureReason())
        ));
    }

    private void publish(String topic, String key, Map<String, String> payload) {
        try {
            kafka.send(topic, key, mapper.writeValueAsString(payload));
        } catch (Exception e) {
            throw new IllegalStateException("kafka publish failed", e);
        }
    }
}
