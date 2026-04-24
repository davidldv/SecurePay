package com.securepay.account.api;

import com.securepay.account.application.AccountService;
import com.securepay.account.domain.Account;
import com.securepay.account.domain.LedgerEntry;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/accounts")
public class AccountController {

    private final AccountService svc;

    public AccountController(AccountService svc) {
        this.svc = svc;
    }

    @PostMapping
    public ResponseEntity<AccountDto> create(@AuthenticationPrincipal Jwt jwt,
                                             @Valid @RequestBody CreateReq req) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(AccountDto.from(svc.create(userId, req.currency())));
    }

    @GetMapping
    public List<AccountDto> list(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return svc.listForUser(userId).stream().map(AccountDto::from).toList();
    }

    @GetMapping("/{id}")
    public AccountDto get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return AccountDto.from(svc.get(userId, id));
    }

    @GetMapping("/{id}/balance")
    public BalanceDto balance(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        UUID userId = UUID.fromString(jwt.getSubject());
        Account a = svc.get(userId, id);
        return new BalanceDto(a.getBalance(), a.getCurrency());
    }

    @GetMapping("/{id}/history")
    public Page<LedgerDto> history(@AuthenticationPrincipal Jwt jwt,
                                   @PathVariable UUID id,
                                   @RequestParam(defaultValue = "0") int page,
                                   @RequestParam(defaultValue = "20") int size) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return svc.history(userId, id, PageRequest.of(page, Math.min(size, 100))).map(LedgerDto::from);
    }

    public record CreateReq(@NotBlank @Size(min = 3, max = 3) String currency) {}

    public record AccountDto(UUID id, String accountNumber, BigDecimal balance,
                             String currency, String status, Instant createdAt) {
        public static AccountDto from(Account a) {
            return new AccountDto(a.getId(), a.getAccountNumber(), a.getBalance(),
                    a.getCurrency(), a.getStatus().name(), a.getCreatedAt());
        }
    }

    public record BalanceDto(BigDecimal balance, String currency) {}

    public record LedgerDto(UUID txId, String direction, BigDecimal amount,
                            BigDecimal balanceAfter, Instant createdAt) {
        public static LedgerDto from(LedgerEntry e) {
            return new LedgerDto(e.getTxId(), e.getDirection(), e.getAmount(),
                    e.getBalanceAfter(), e.getCreatedAt());
        }
    }
}
