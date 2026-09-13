package com.apextick.booking.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Set;

/** Strongly-typed {@code app.*} configuration. */
@ConfigurationProperties("app")
public record AppProperties(Hold hold, Outbox outbox, Order order, Payment payment, RateLimit rateLimit) {

    public record Hold(Duration duration, int maxSeats, Duration expiryTolerance, int sweeperBatch) {
    }

    /**
     * @param requireRoute event types a queue must take before they count as published. The broker acks a
     *                     message no queue is bound for, so an ack alone would mark such an event sent.
     */
    public record Outbox(Duration pollInterval, int batchSize, int maxAttempts, Duration confirmTimeout,
                         Set<String> requireRoute) {
    }

    public record Order(BigDecimal feePercent, Duration paymentWindow) {
    }

    public record Payment(String provider, Stripe stripe) {

        /**
         * Stripe test/live credentials. Only read when {@code app.payment.provider=stripe};
         * the bean still constructs with blank values so the mock provider needs no keys.
         * {@code supportedCurrencies} empty means "accept every currency".
         */
        public record Stripe(String secretKey, String publishableKey, String webhookSecret,
                             Set<String> supportedCurrencies) {
        }
    }

    public record RateLimit(boolean enabled, Bucket hold, Bucket order) {
    }

    public record Bucket(int limit, Duration window) {
    }
}
