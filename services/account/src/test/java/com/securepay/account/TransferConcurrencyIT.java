package com.securepay.account;

import com.securepay.account.application.TransferService;
import com.securepay.account.domain.Account;
import com.securepay.account.infrastructure.AccountRepository;
import com.securepay.account.infrastructure.LedgerRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.autoconfigure.exclude=" +
                        "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration," +
                        "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration"
        })
@Testcontainers
class TransferConcurrencyIT {

    @Container
    static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("accountdb")
            .withUsername("acct")
            .withPassword("acct");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", pg::getJdbcUrl);
        r.add("spring.datasource.username", pg::getUsername);
        r.add("spring.datasource.password", pg::getPassword);
        r.add("securepay.service-token", () -> "test-token");
        r.add("securepay.jwt.issuer", () -> "test");
        r.add("securepay.jwt.access-ttl", () -> "PT15M");
        r.add("securepay.jwt.refresh-ttl", () -> "P7D");
        r.add("securepay.jwt.private-key", () -> "");
        r.add("securepay.jwt.public-key", TransferConcurrencyIT::generatePublicKey);
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

    @Autowired TransferService transfers;
    @Autowired AccountRepository accounts;
    @Autowired LedgerRepository ledger;

    @Test
    void concurrent_transfers_preserve_total_and_never_overdraft() throws Exception {
        Account src = accounts.saveAndFlush(seedAccount(new BigDecimal("1000.0000")));
        Account dst = accounts.saveAndFlush(seedAccount(BigDecimal.ZERO));

        int threads = 16;
        int perThread = 5;
        BigDecimal amount = new BigDecimal("10.0000");
        BigDecimal total = amount.multiply(BigDecimal.valueOf((long) threads * perThread));

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger ok = new AtomicInteger();
        AtomicInteger fail = new AtomicInteger();

        for (int t = 0; t < threads; t++) {
            pool.submit(() -> {
                try {
                    start.await();
                    for (int i = 0; i < perThread; i++) {
                        UUID txId = UUID.randomUUID();
                        try {
                            transfers.transfer(txId, src.getId(), dst.getId(), amount, "USD");
                            ok.incrementAndGet();
                        } catch (Exception e) {
                            fail.incrementAndGet();
                        }
                    }
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            });
        }
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(60, TimeUnit.SECONDS)).isTrue();

        Account refreshedSrc = accounts.findById(src.getId()).orElseThrow();
        Account refreshedDst = accounts.findById(dst.getId()).orElseThrow();

        assertThat(ok.get()).isEqualTo(threads * perThread);
        assertThat(fail.get()).isZero();
        assertThat(refreshedSrc.getBalance().add(refreshedDst.getBalance()))
                .isEqualByComparingTo(new BigDecimal("1000.0000"));
        assertThat(refreshedDst.getBalance()).isEqualByComparingTo(total);
        assertThat(refreshedSrc.getBalance().signum()).isGreaterThanOrEqualTo(0);
        assertThat(ledger.count()).isEqualTo(2L * threads * perThread);
    }

    @Test
    void duplicate_txId_is_rejected_via_ledger_unique_constraint() {
        Account src = accounts.saveAndFlush(seedAccount(new BigDecimal("100.0000")));
        Account dst = accounts.saveAndFlush(seedAccount(BigDecimal.ZERO));

        UUID txId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("10.0000");

        transfers.transfer(txId, src.getId(), dst.getId(), amount, "USD");
        assertThat(catchThrowable(() -> {
            transfers.transfer(txId, src.getId(), dst.getId(), amount, "USD");
        })).isInstanceOf(TransferService.DuplicateTransferException.class);

        long ledgerRows = ledger.count();
        assertThat(ledgerRows).isEqualTo(2L);
    }

    @Test
    void reverse_direction_pairs_do_not_deadlock() throws Exception {
        Account a = accounts.saveAndFlush(seedAccount(new BigDecimal("10000.0000")));
        Account b = accounts.saveAndFlush(seedAccount(new BigDecimal("10000.0000")));

        int iterations = 30;
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger errors = new AtomicInteger();

        Runnable aToB = () -> runMany(start, iterations, a, b, errors);
        Runnable bToA = () -> runMany(start, iterations, b, a, errors);
        pool.submit(aToB);
        pool.submit(bToA);
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(60, TimeUnit.SECONDS)).isTrue();

        assertThat(errors.get()).isZero();
    }

    private void runMany(CountDownLatch start, int n, Account from, Account to, AtomicInteger errors) {
        try {
            start.await();
            for (int i = 0; i < n; i++) {
                try {
                    transfers.transfer(UUID.randomUUID(), from.getId(), to.getId(),
                            new BigDecimal("1.0000"), "USD");
                } catch (Exception e) {
                    errors.incrementAndGet();
                }
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    private static Account seedAccount(BigDecimal balance) {
        Account a = new Account(UUID.randomUUID(), "ACCT-" + UUID.randomUUID(), "USD");
        if (balance.signum() > 0) a.credit(balance);
        return a;
    }
}
