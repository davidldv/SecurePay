package com.securepay.auth.application;

import com.securepay.auth.domain.RefreshToken;
import com.securepay.auth.infrastructure.RefreshTokenRepository;
import com.securepay.common.security.JwtProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

@Service
public class RefreshTokenService {

    private static final SecureRandom RNG = new SecureRandom();

    private final RefreshTokenRepository repo;
    private final JwtProperties props;

    public RefreshTokenService(RefreshTokenRepository repo, JwtProperties props) {
        this.repo = repo;
        this.props = props;
    }

    @Transactional
    public Issued issue(UUID userId) {
        byte[] raw = new byte[48];
        RNG.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        Instant exp = Instant.now().plus(props.refreshTtl());
        repo.save(new RefreshToken(userId, hash(token), exp));
        return new Issued(token, exp);
    }

    @Transactional
    public RefreshToken consume(String token) {
        RefreshToken rt = repo.findByTokenHash(hash(token))
                .orElseThrow(() -> new IllegalArgumentException("invalid refresh token"));
        if (rt.isRevoked() || rt.getExpiresAt().isBefore(Instant.now())) {
            throw new IllegalArgumentException("invalid refresh token");
        }
        rt.revoke();
        return rt;
    }

    public static String hash(String token) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(d);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public record Issued(String token, Instant expiresAt) {}
}
