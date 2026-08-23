package com.apextick.booking;

import com.apextick.booking.support.TestKeys;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;

/**
 * Shared Testcontainers + a real (test-key) {@link JwtDecoder} so integration
 * tests can validate the RS256 tokens minted by
 * {@link com.apextick.booking.support.TestTokens}.
 */
@TestConfiguration(proxyBeanMethods = false)
public class IntegrationTestConfig {

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgresContainer() {
        return new PostgreSQLContainer<>("postgres:18-alpine");
    }

    @Bean
    @ServiceConnection
    RabbitMQContainer rabbitContainer() {
        return new RabbitMQContainer("rabbitmq:4-management");
    }

    @Bean
    @ServiceConnection(name = "redis")
    GenericContainer<?> redisContainer() {
        return new GenericContainer<>("redis:7-alpine")
                .withCommand("redis-server", "--notify-keyspace-events", "Ex")
                .withExposedPorts(6379);
    }

    /**
     * Validates test tokens with the public half of {@link TestKeys}. Signature
     * only — issuer/audience checks aren't needed for tests, and providing this
     * bean keeps the resource server from trying to reach the real issuer.
     */
    @Bean
    JwtDecoder jwtDecoder() {
        return NimbusJwtDecoder.withPublicKey(TestKeys.PUBLIC).build();
    }
}
