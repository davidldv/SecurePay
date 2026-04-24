package com.securepay.transaction.infrastructure;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.UUID;

@Component
public class AccountClient {

    private final RestClient client;

    public AccountClient(@Value("${securepay.account-service-url}") String baseUrl,
                         @Value("${securepay.service-token}") String serviceToken) {
        this.client = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("X-Service-Token", serviceToken)
                .build();
    }

    public TransferResult transfer(UUID txId, UUID src, UUID dst, BigDecimal amount, String currency) {
        try {
            return client.post()
                    .uri("/internal/transfers")
                    .body(new TransferReq(txId, src, dst, amount, currency))
                    .retrieve()
                    .body(TransferResult.class);
        } catch (HttpStatusCodeException e) {
            throw new AccountCallException(e.getStatusCode(), e.getResponseBodyAsString(), e);
        }
    }

    public record TransferReq(UUID txId, UUID src, UUID dst, BigDecimal amount, String currency) {}
    public record TransferResult(UUID txId, BigDecimal sourceBalance, BigDecimal destBalance) {}

    public static class AccountCallException extends RuntimeException {
        public final HttpStatusCode status;
        public final String body;
        public AccountCallException(HttpStatusCode s, String b, Throwable cause) {
            super("account-service " + s + ": " + b, cause);
            this.status = s; this.body = b;
        }
    }
}
