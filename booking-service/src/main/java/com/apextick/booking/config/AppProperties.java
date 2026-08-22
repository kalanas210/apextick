package com.apextick.booking.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.time.Duration;

/** Strongly-typed {@code app.*} configuration. */
@ConfigurationProperties("app")
public record AppProperties(Hold hold, Outbox outbox, Order order) {

    public record Hold(Duration duration, int maxSeats, Duration expiryTolerance, int sweeperBatch) {
    }

    public record Outbox(Duration pollInterval, int batchSize, int maxAttempts, Duration confirmTimeout) {
    }

    public record Order(BigDecimal feePercent, Duration paymentWindow) {
    }
}
