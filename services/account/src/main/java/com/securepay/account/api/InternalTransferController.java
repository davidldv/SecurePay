package com.securepay.account.api;

import com.securepay.account.application.TransferService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.UUID;

@RestController
@RequestMapping("/internal/transfers")
public class InternalTransferController {

    private final TransferService svc;

    public InternalTransferController(TransferService svc) {
        this.svc = svc;
    }

    @PostMapping
    public ResponseEntity<TransferService.Result> transfer(@Valid @RequestBody Req r) {
        return ResponseEntity.ok(svc.transfer(r.txId(), r.src(), r.dst(), r.amount(), r.currency()));
    }

    public record Req(
            @NotNull UUID txId,
            @NotNull UUID src,
            @NotNull UUID dst,
            @NotNull @DecimalMin(value = "0.0001", inclusive = true) BigDecimal amount,
            @NotBlank @Size(min = 3, max = 3) String currency) {}
}
