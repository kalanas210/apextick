package com.apextick.booking.support;

import com.apextick.booking.IntegrationTestConfig;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Composed annotation for full-stack integration tests: boots the app on a random
 * port against the shared Testcontainers (Postgres + RabbitMQ + Redis) with the
 * {@code test} profile and MockMvc available.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(IntegrationTestConfig.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
public @interface IntegrationTest {
}
