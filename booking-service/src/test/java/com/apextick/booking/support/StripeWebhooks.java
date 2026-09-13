package com.apextick.booking.support;

import com.stripe.Stripe;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Stripe webhook deliveries, built and signed the way Stripe builds and signs them, so a test
 * goes through the real signature check instead of around it.
 */
public final class StripeWebhooks {

    private StripeWebhooks() {
    }

    /**
     * A {@code payment_intent.*} event carrying one PaymentIntent.
     *
     * @param paymentId the ApexTick payment id stamped on the intent's metadata, or {@code null}
     *                  for an intent that carries none
     */
    public static String paymentIntentEvent(String eventId, String type, String intentId,
                                            long amountMinor, String currency, String paymentId) {
        boolean succeeded = type.equals("payment_intent.succeeded");
        String metadata = paymentId == null ? "{}" : "{\"paymentId\":\"" + paymentId + "\"}";
        return String.format(
                "{\"id\":\"%s\",\"object\":\"event\",\"api_version\":\"%s\",\"type\":\"%s\","
                        + "\"data\":{\"object\":{\"id\":\"%s\",\"object\":\"payment_intent\","
                        + "\"amount\":%d,\"amount_received\":%d,\"currency\":\"%s\",\"status\":\"%s\","
                        + "\"metadata\":%s}}}",
                eventId, Stripe.API_VERSION, type, intentId, amountMinor, succeeded ? amountMinor : 0,
                currency.toLowerCase(Locale.ROOT), succeeded ? "succeeded" : "requires_payment_method", metadata);
    }

    /**
     * A {@code charge.refunded} event for the charge of {@code intentId}: {@code refundedMinor} of its
     * {@code amountMinor} given back, which Stripe marks {@code refunded} only once it is all of it.
     */
    public static String chargeRefundedEvent(String eventId, String intentId, long amountMinor, long refundedMinor,
                                             String currency) {
        return String.format(
                "{\"id\":\"%s\",\"object\":\"event\",\"api_version\":\"%s\",\"type\":\"charge.refunded\","
                        + "\"data\":{\"object\":{\"id\":\"ch_%s\",\"object\":\"charge\",\"payment_intent\":\"%s\","
                        + "\"amount\":%d,\"amount_refunded\":%d,\"refunded\":%b,\"currency\":\"%s\",\"metadata\":{}}}}",
                eventId, Stripe.API_VERSION, intentId.substring(3), intentId, amountMinor, refundedMinor,
                refundedMinor >= amountMinor, currency.toLowerCase(Locale.ROOT));
    }

    /** A {@code charge.dispute.created} event: the cardholder disputed the charge of {@code intentId}. */
    public static String disputeCreatedEvent(String eventId, String intentId, long amountMinor, String currency,
                                             String reason) {
        return String.format(
                "{\"id\":\"%s\",\"object\":\"event\",\"api_version\":\"%s\",\"type\":\"charge.dispute.created\","
                        + "\"data\":{\"object\":{\"id\":\"dp_%s\",\"object\":\"dispute\",\"payment_intent\":\"%s\","
                        + "\"charge\":\"ch_%s\",\"amount\":%d,\"currency\":\"%s\",\"reason\":\"%s\","
                        + "\"status\":\"needs_response\"}}}",
                eventId, Stripe.API_VERSION, intentId.substring(3), intentId, intentId.substring(3), amountMinor,
                currency.toLowerCase(Locale.ROOT), reason);
    }

    /** The {@code Stripe-Signature} header for {@code payload}, signed now. */
    public static String signatureHeader(String payload, String secret) {
        return signatureHeader(payload, secret, Instant.now().getEpochSecond());
    }

    public static String signatureHeader(String payload, String secret, long timestamp) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal((timestamp + "." + payload).getBytes(StandardCharsets.UTF_8));
            return "t=" + timestamp + ",v1=" + HexFormat.of().formatHex(hash);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 is unavailable", e);
        }
    }
}
