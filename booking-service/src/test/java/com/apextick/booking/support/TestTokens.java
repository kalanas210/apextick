package com.apextick.booking.support;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

/**
 * Mints RS256-signed JWTs shaped like Keycloak access tokens ({@code sub},
 * {@code preferred_username}, {@code email}, {@code realm_access.roles}).
 */
public final class TestTokens {

    public static final String ISSUER = "http://localhost:8180/realms/apextick";

    private static final JwtEncoder ENCODER = new NimbusJwtEncoder(
            new ImmutableJWKSet<>(new JWKSet(
                    new RSAKey.Builder(TestKeys.PUBLIC)
                            .privateKey(TestKeys.PRIVATE)
                            .keyID("test")
                            .build())));

    public static String user(String sub, String username, String email) {
        return withRoles(sub, username, email, "user");
    }

    public static String admin(String sub, String username, String email) {
        return withRoles(sub, username, email, "user", "admin");
    }

    public static String withRoles(String sub, String username, String email, String... roles) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .issuedAt(now)
                .expiresAt(now.plus(1, ChronoUnit.HOURS))
                .subject(sub)
                .claim("preferred_username", username)
                .claim("email", email)
                .claim("realm_access", Map.of("roles", List.of(roles)))
                .build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).keyId("test").build();
        return ENCODER.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    private TestTokens() {
    }
}
