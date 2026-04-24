package com.securepay.account.infrastructure;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.security.MessageDigest;
import java.util.Objects;

@Component
public class ServiceTokenFilter extends OncePerRequestFilter {

    private final byte[] expectedDigest;

    public ServiceTokenFilter(@Value("${securepay.service-token:}") String token) {
        this.expectedDigest = sha256(token);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        if (req.getRequestURI().startsWith("/internal/")) {
            String provided = req.getHeader("X-Service-Token");
            if (provided == null || !MessageDigest.isEqual(sha256(provided), expectedDigest)
                    || expectedDigest.length == 0) {
                res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                return;
            }
        }
        chain.doFilter(req, res);
    }

    private static byte[] sha256(String s) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(Objects.requireNonNullElse(s, "").getBytes());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
