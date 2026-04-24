package com.securepay.account.application;

import com.securepay.account.domain.Account;
import com.securepay.account.domain.LedgerEntry;
import com.securepay.account.infrastructure.AccountRepository;
import com.securepay.account.infrastructure.LedgerRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class TransferService {

    private final AccountRepository accounts;
    private final LedgerRepository ledger;

    public TransferService(AccountRepository accounts, LedgerRepository ledger) {
        this.accounts = accounts;
        this.ledger = ledger;
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ, timeout = 10)
    public Result transfer(UUID txId, UUID src, UUID dst, BigDecimal amount, String currency) {
        if (src.equals(dst)) throw new IllegalArgumentException("src and dst must differ");
        if (amount.signum() <= 0) throw new IllegalArgumentException("amount must be positive");

        // Lock in deterministic order to avoid deadlocks.
        List<UUID> ordered = java.util.stream.Stream.of(src, dst)
                .sorted(Comparator.naturalOrder()).toList();
        Account first = accounts.findForUpdate(ordered.get(0))
                .orElseThrow(() -> new IllegalArgumentException("account not found: " + ordered.get(0)));
        Account second = accounts.findForUpdate(ordered.get(1))
                .orElseThrow(() -> new IllegalArgumentException("account not found: " + ordered.get(1)));

        Account source = first.getId().equals(src) ? first : second;
        Account dest   = first.getId().equals(dst) ? first : second;

        if (!source.getCurrency().equals(currency) || !dest.getCurrency().equals(currency)) {
            throw new IllegalArgumentException("currency mismatch");
        }
        if (!"ACTIVE".equals(source.getStatus().name()) || !"ACTIVE".equals(dest.getStatus().name())) {
            throw new IllegalStateException("account not active");
        }

        source.debit(amount);
        dest.credit(amount);

        try {
            ledger.save(new LedgerEntry(source.getId(), txId, "D", amount, source.getBalance()));
            ledger.save(new LedgerEntry(dest.getId(),   txId, "C", amount, dest.getBalance()));
            ledger.flush();
        } catch (DataIntegrityViolationException dup) {
            // Duplicate (tx_id, account_id, direction) — transfer already recorded; treat as success replay.
            throw new DuplicateTransferException(txId);
        }

        return new Result(txId, source.getBalance(), dest.getBalance());
    }

    public record Result(UUID txId, BigDecimal sourceBalance, BigDecimal destBalance) {}

    public static class DuplicateTransferException extends RuntimeException {
        public final UUID txId;
        public DuplicateTransferException(UUID txId) { super("duplicate tx " + txId); this.txId = txId; }
    }
}
