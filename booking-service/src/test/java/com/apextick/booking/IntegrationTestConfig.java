package com.apextick.booking;

import com.apextick.booking.messaging.RabbitConfig;
import com.apextick.booking.outbox.EventTypes;
import com.apextick.booking.support.TestKeys;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;

import java.util.ArrayList;
import java.util.List;

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
     * Stands in for notification-service's queues, which production has and these tests don't. The outbox only
     * counts an event a customer is e-mailed about as published once a queue takes it (app.outbox.require-route),
     * so without this every test's confirmation would sit in the outbox, retrying. Capped, as nothing drains it.
     */
    @Bean
    Declarables notificationQueueStandIn() {
        Queue queue = QueueBuilder.durable("test.notification-stand-in").maxLength(10_000).build();
        TopicExchange events = new TopicExchange(RabbitConfig.EXCHANGE);
        List<Declarable> declarables = new ArrayList<>(List.of(queue));
        for (String type : List.of(EventTypes.BOOKING_CONFIRMED, EventTypes.ORDER_CANCELLED,
                EventTypes.PAYMENT_REFUNDED, EventTypes.PAYMENT_FAILED, EventTypes.EVENT_CANCELLED)) {
            declarables.add(BindingBuilder.bind(queue).to(events).with(type));
        }
        return new Declarables(declarables);
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
