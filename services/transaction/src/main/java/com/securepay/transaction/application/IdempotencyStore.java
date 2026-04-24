package com.securepay.transaction.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

@Component
public class IdempotencyStore {

    private static final Duration TTL = Duration.ofHours(24);

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;

    public IdempotencyStore(StringRedisTemplate redis, ObjectMapper mapper) {
        this.redis = redis;
        this.mapper = mapper;
    }

    public boolean claim(String key, UUID userId, UUID txId) {
        String k = keyFor(key, userId);
        Boolean ok = redis.opsForValue().setIfAbsent(k, txId.toString(), TTL);
        return Boolean.TRUE.equals(ok);
    }

    public Optional<UUID> existing(String key, UUID userId) {
        String v = redis.opsForValue().get(keyFor(key, userId));
        return Optional.ofNullable(v).map(UUID::fromString);
    }

    public void cacheResult(String key, UUID userId, Object body) {
        try {
            redis.opsForValue().set(keyFor(key, userId) + ":res", mapper.writeValueAsString(body), TTL);
        } catch (Exception ignore) {
            // non-fatal: next retry will hit DB
        }
    }

    public <T> Optional<T> getResult(String key, UUID userId, Class<T> type) {
        String v = redis.opsForValue().get(keyFor(key, userId) + ":res");
        if (v == null) return Optional.empty();
        try {
            return Optional.of(mapper.readValue(v, type));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private String keyFor(String key, UUID userId) {
        return "idem:" + userId + ":" + key;
    }
}
