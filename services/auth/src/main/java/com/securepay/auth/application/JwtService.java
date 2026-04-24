package com.securepay.auth.application;

import com.securepay.auth.domain.User;
import com.securepay.common.security.JwtProperties;
import com.securepay.common.security.RsaKeyLoader;
import io.jsonwebtoken.Jwts;
import org.springframework.stereotype.Service;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {

    private final JwtProperties props;
    private final RSAPrivateKey privateKey;
    private final RSAPublicKey publicKey;

    public JwtService(JwtProperties props) {
        this.props = props;
        this.privateKey = RsaKeyLoader.privateKey(props.privateKey());
        this.publicKey = RsaKeyLoader.publicKey(props.publicKey());
    }

    public IssuedAccess issueAccess(User user) {
        Instant now = Instant.now();
        Instant exp = now.plus(props.accessTtl());
        String jti = UUID.randomUUID().toString();
        String token = Jwts.builder()
                .issuer(props.issuer())
                .subject(user.getId().toString())
                .id(jti)
                .claim("role", user.getRole().name())
                .claim("email", user.getEmail())
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(privateKey, Jwts.SIG.RS256)
                .compact();
        return new IssuedAccess(token, jti, exp);
    }

    public record IssuedAccess(String token, String jti, Instant expiresAt) {}
}
