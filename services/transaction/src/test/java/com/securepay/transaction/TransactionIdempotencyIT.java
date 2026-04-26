package com.securepay.transaction;

import com.securepay.transaction.application.TransactionService;
import com.securepay.transaction.domain.OutboxEvent;
import com.securepay.transaction.domain.TxStatus;
import com.securepay.transaction.infrastructure.AccountClient;
import com.securepay.transaction.infrastructure.OutboxRepository;
import com.securepay.transaction.infrastructure.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.kafka.bootstrap-servers=localhost:1",
                "securepay.outbox.poll-ms=60000"
        })
@Testcontainers
class TransactionIdempotencyIT {

    @Container
    static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("txdb")
            .withUsername("tx")
            .withPassword("tx");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", pg::getJdbcUrl);
        r.add("spring.datasource.username", pg::getUsername);
        r.add("spring.datasource.password", pg::getPassword);
        r.add("spring.data.redis.url",
                () -> "redis://" + redis.getHost() + ":" + redis.getFirstMappedPort());
        r.add("securepay.account-service-url", () -> "http://localhost:1");
        r.add("securepay.service-token", () -> "test-token");
        r.add("securepay.jwt.issuer", () -> "test");
        r.add("securepay.jwt.access-ttl", () -> "PT15M");
        r.add("securepay.jwt.refresh-ttl", () -> "P7D");
        r.add("securepay.jwt.private-key", () -> "");
        r.add("securepay.jwt.public-key", TransactionIdempotencyIT::generatePublicKey);
    }

    private static String generatePublicKey() {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
            kpg.initialize(2048);
            KeyPair kp = kpg.generateKeyPair();
            return Base64.getEncoder().encodeToString(kp.getPublic().getEncoded());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @MockBean AccountClient accountClient;

    @MockBean
    @SuppressWarnings("rawtypes")
    KafkaTemplate kafkaTemplate;

    @Autowired TransactionService service;
    @Autowired TransactionRepository txRepo;
    @Autowired OutboxRepository outboxRepo;

    @Test
    @SuppressWarnings("unchecked")
    void concurrent_same_idempotency_key_yields_single_record_and_single_outbox_event()
            throws Exception {
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(Mockito.mock(SendResult.class)));

        UUID userId = UUID.randomUUID();
        UUID src = UUID.randomUUID();
        UUID dst = UUID.randomUUID();
        String key = "idem-" + UUID.randomUUID();
        BigDecimal amount = new BigDecimal("25.0000");

        int threads = 16;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger ok = new AtomicInteger();
        AtomicInteger fail = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    service.initiate(userId, key, src, dst, amount, "USD");
                    ok.incrementAndGet();
                } catch (Exception e) {
                    fail.incrementAndGet();
                }
            });
        }
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        assertThat(ok.get() + fail.get()).isEqualTo(threads);
        List<?> rows = txRepo.findAll();
        assertThat(rows).hasSize(1);

        var record = txRepo.findByIdempotencyKeyAndSourceAccount(key, src).orElseThrow();
        assertThat(record.getStatus()).isEqualTo(TxStatus.COMPLETED);

        verify(accountClient, atLeast(1))
                .transfer(any(UUID.class), any(UUID.class), any(UUID.class),
                        any(BigDecimal.class), anyString());

        List<OutboxEvent> events = outboxRepo.findAll();
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getTopic()).isEqualTo("tx.completed");
        assertThat(events.get(0).getSentAt()).isNull();
    }
}
