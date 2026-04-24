package com.securepay.transaction.api;

import com.securepay.transaction.application.TransactionService;
import com.securepay.transaction.domain.TransactionRecord;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/transactions")
public class TransactionController {

    private final TransactionService svc;

    public TransactionController(TransactionService svc) {
        this.svc = svc;
    }

    @PostMapping
    public ResponseEntity<TxDto> create(@AuthenticationPrincipal Jwt jwt,
                                        @RequestHeader("Idempotency-Key") @NotBlank @Size(min = 8, max = 64) String idemKey,
                                        @Valid @RequestBody TransferReq req) {
        UUID userId = UUID.fromString(jwt.getSubject());
        TransactionRecord r = svc.initiate(userId, idemKey, req.sourceAccount(), req.destAccount(),
                req.amount(), req.currency());
        return ResponseEntity.ok(TxDto.from(r));
    }

    @GetMapping("/{id}")
    public TxDto get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return TxDto.from(svc.get(userId, id));
    }

    public record TransferReq(
            @NotNull UUID sourceAccount,
            @NotNull UUID destAccount,
            @NotNull @DecimalMin(value = "0.0001", inclusive = true) BigDecimal amount,
            @NotBlank @Size(min = 3, max = 3) String currency) {}

    public record TxDto(UUID id, UUID sourceAccount, UUID destAccount, BigDecimal amount,
                        String currency, String status, String failureReason,
                        Instant createdAt, Instant completedAt) {
        public static TxDto from(TransactionRecord r) {
            return new TxDto(r.getId(), r.getSourceAccount(), r.getDestAccount(), r.getAmount(),
                    r.getCurrency(), r.getStatus().name(), r.getFailureReason(),
                    r.getCreatedAt(), r.getCompletedAt());
        }
    }
}
