package com.securepay.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class TxEventListener {

    private static final Logger log = LoggerFactory.getLogger(TxEventListener.class);

    @KafkaListener(topics = "tx.completed", groupId = "notification-svc")
    public void onCompleted(String payload) {
        log.info("[EMAIL-MOCK] tx.completed → {}", payload);
    }

    @KafkaListener(topics = "tx.failed", groupId = "notification-svc")
    public void onFailed(String payload) {
        log.warn("[EMAIL-MOCK] tx.failed → {}", payload);
    }
}
