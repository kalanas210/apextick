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
