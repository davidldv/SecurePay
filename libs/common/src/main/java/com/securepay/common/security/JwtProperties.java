package com.securepay.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "securepay.jwt")
public record JwtProperties(
        String issuer,
        String publicKey,
        String privateKey,
        Duration accessTtl,
        Duration refreshTtl
) {
}
