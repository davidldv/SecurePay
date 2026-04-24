package com.securepay.auth.api;

import com.securepay.auth.application.AuthService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthService.TokenPair> register(@Valid @RequestBody RegisterReq req) {
        return ResponseEntity.ok(auth.register(req.email(), req.password()));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthService.TokenPair> login(@Valid @RequestBody LoginReq req) {
        return ResponseEntity.ok(auth.login(req.email(), req.password()));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthService.TokenPair> refresh(@Valid @RequestBody RefreshReq req) {
        return ResponseEntity.ok(auth.refresh(req.refreshToken()));
    }

    public record RegisterReq(
            @Email @NotBlank String email,
            @NotBlank @Size(min = 8, max = 128) String password) {}

    public record LoginReq(
            @Email @NotBlank String email,
            @NotBlank String password) {}

    public record RefreshReq(@NotBlank String refreshToken) {}
}
