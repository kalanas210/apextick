package com.apextick.booking.support;

import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

/**
 * A single RSA key pair generated once per JVM. {@link TestTokens} signs test
 * JWTs with the private key; the test {@code JwtDecoder} validates them with the
 * public key, so integration tests can mint valid bearer tokens without Keycloak.
 */
public final class TestKeys {

    public static final RSAPublicKey PUBLIC;
    public static final RSAPrivateKey PRIVATE;

    static {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            var pair = generator.generateKeyPair();
            PUBLIC = (RSAPublicKey) pair.getPublic();
            PRIVATE = (RSAPrivateKey) pair.getPrivate();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to generate test RSA key pair", e);
        }
    }

    private TestKeys() {
    }
}
