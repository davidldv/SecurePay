package com.securepay.common.security;

import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

public final class RsaKeyLoader {

    private RsaKeyLoader() {}

    public static RSAPublicKey publicKey(String pem) {
        try {
            byte[] der = Base64.getDecoder().decode(stripPem(pem));
            return (RSAPublicKey) KeyFactory.getInstance("RSA")
                    .generatePublic(new X509EncodedKeySpec(der));
        } catch (Exception e) {
            throw new IllegalStateException("Invalid RSA public key", e);
        }
    }

    public static RSAPrivateKey privateKey(String pem) {
        try {
            byte[] der = Base64.getDecoder().decode(stripPem(pem));
            return (RSAPrivateKey) KeyFactory.getInstance("RSA")
                    .generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (Exception e) {
            throw new IllegalStateException("Invalid RSA private key", e);
        }
    }

    private static String stripPem(String pem) {
        return pem.replaceAll("-----BEGIN (.*?)-----", "")
                .replaceAll("-----END (.*?)-----", "")
                .replaceAll("\\s", "");
    }
}
