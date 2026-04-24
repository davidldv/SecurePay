package com.securepay.account.application;

import com.securepay.account.domain.Account;
import com.securepay.account.domain.LedgerEntry;
import com.securepay.account.infrastructure.AccountRepository;
import com.securepay.account.infrastructure.LedgerRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.List;
import java.util.UUID;

@Service
public class AccountService {

    private static final SecureRandom RNG = new SecureRandom();

    private final AccountRepository accounts;
    private final LedgerRepository ledger;

    public AccountService(AccountRepository accounts, LedgerRepository ledger) {
        this.accounts = accounts;
        this.ledger = ledger;
    }

    @Transactional
    public Account create(UUID userId, String currency) {
        String number = generateAccountNumber();
        return accounts.save(new Account(userId, number, currency));
    }

    @Transactional(readOnly = true)
    public Account get(UUID userId, UUID accountId) {
        Account a = accounts.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("account not found"));
        if (!a.getUserId().equals(userId)) {
            throw new SecurityException("forbidden");
        }
        return a;
    }

    @Transactional(readOnly = true)
    public List<Account> listForUser(UUID userId) {
        return accounts.findAllByUserId(userId);
    }

    @Transactional(readOnly = true)
    public Page<LedgerEntry> history(UUID userId, UUID accountId, Pageable pageable) {
        get(userId, accountId);
        return ledger.findByAccountIdOrderByCreatedAtDesc(accountId, pageable);
    }

    private String generateAccountNumber() {
        StringBuilder sb = new StringBuilder(16);
        for (int i = 0; i < 16; i++) sb.append(RNG.nextInt(10));
        return sb.toString();
    }
}
