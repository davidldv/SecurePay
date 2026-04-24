package com.securepay.auth.application;

import com.securepay.auth.domain.RefreshToken;
import com.securepay.auth.domain.Role;
import com.securepay.auth.domain.User;
import com.securepay.auth.infrastructure.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final RefreshTokenService refresh;

    public AuthService(UserRepository users, PasswordEncoder encoder,
                       JwtService jwt, RefreshTokenService refresh) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
        this.refresh = refresh;
    }

    @Transactional
    public TokenPair register(String email, String rawPassword) {
        if (users.existsByEmail(email)) {
            throw new IllegalStateException("email already registered");
        }
        User u = users.save(new User(email, encoder.encode(rawPassword), Role.USER));
        return issueTokens(u);
    }

    @Transactional
    public TokenPair login(String email, String rawPassword) {
        User u = users.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("invalid credentials"));
        if (!encoder.matches(rawPassword, u.getPasswordHash())) {
            throw new IllegalArgumentException("invalid credentials");
        }
        return issueTokens(u);
    }

    @Transactional
    public TokenPair refresh(String refreshToken) {
        RefreshToken rt = refresh.consume(refreshToken);
        User u = users.findById(rt.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("user not found"));
        return issueTokens(u);
    }

    private TokenPair issueTokens(User u) {
        JwtService.IssuedAccess access = jwt.issueAccess(u);
        RefreshTokenService.Issued r = refresh.issue(u.getId());
        return new TokenPair(access.token(), r.token(), access.expiresAt().toEpochMilli());
    }

    public record TokenPair(String accessToken, String refreshToken, long accessExpiresAt) {}
}
