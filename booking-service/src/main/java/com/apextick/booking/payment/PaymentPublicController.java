package com.apextick.booking.payment;

import com.apextick.booking.config.AppProperties;
import com.apextick.booking.payment.dto.PaymentConfigResponse;
import com.apextick.booking.payment.model.CallbackRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Public payment endpoints: checkout config for the UI and provider webhooks (no auth, signed). */
@RestController
@RequestMapping("/api/payments")
@Tag(name = "Payments")
public class PaymentPublicController {

    private final PaymentService payments;
    private final PaymentGatewayRegistry registry;
    private final AppProperties props;
    private final boolean demo;

    public PaymentPublicController(PaymentService payments, PaymentGatewayRegistry registry, AppProperties props,
                                   @Value("${spring.liquibase.contexts:}") String liquibaseContexts) {
        this.payments = payments;
        this.registry = registry;
        this.props = props;
        this.demo = seedsDemo(liquibaseContexts);
    }

    @GetMapping("/config")
    @Operation(summary = "Active payment provider, Stripe publishable key, and whether this deployment takes "
            + "test payments and is the demo")
    public PaymentConfigResponse config() {
        List<String> enabled = registry.providers().stream()
                .map(p -> p.name().toLowerCase(Locale.ROOT)).toList();
        String publishableKey = null;
        AppProperties.Payment.Stripe stripe = props.payment().stripe();
        if (registry.providers().contains(PaymentProvider.STRIPE)
                && stripe != null && stripe.publishableKey() != null && !stripe.publishableKey().isBlank()) {
            publishableKey = stripe.publishableKey();
        }
        PaymentProvider active = registry.defaultProvider();
        return new PaymentConfigResponse(active.name().toLowerCase(Locale.ROOT), enabled, publishableKey,
                testMode(active, stripe), demo);
    }

    /**
     * Whether no real money can move: the mock gateway never charges, and Stripe only does with live keys. A
     * Stripe deployment whose key is missing or unrecognised does not count -- "no real money is charged" is a
     * promise to a buyer, so it is only made when it is known to be true.
     */
    static boolean testMode(PaymentProvider active, AppProperties.Payment.Stripe stripe) {
        if (active == PaymentProvider.MOCK) {
            return true;
        }
        String key = stripe == null ? null : stripe.secretKey();
        return key != null && (key.startsWith("sk_test_") || key.startsWith("rk_test_"));
    }

    /** The demo season and its shared account are seeded only under the {@code demo} Liquibase context. */
    static boolean seedsDemo(String liquibaseContexts) {
        return liquibaseContexts != null && Arrays.stream(liquibaseContexts.split(","))
                .map(String::trim)
                .anyMatch("demo"::equalsIgnoreCase);
    }

    @PostMapping("/stripe/webhook")
    @Operation(summary = "Stripe webhook (signature-verified)")
    public ResponseEntity<String> stripeWebhook(
            @RequestBody String payload,
            @RequestHeader(name = "Stripe-Signature", required = false) String signature) {
        Map<String, String> headers = new HashMap<>();
        if (signature != null) {
            headers.put("Stripe-Signature", signature);
        }
        try {
            payments.handleWebhook(PaymentProvider.STRIPE, new CallbackRequest(payload, headers, Map.of()));
            return ResponseEntity.ok("ok");
        } catch (WebhookVerificationException e) {
            // Stripe only inspects the status code; 400 tells it not to treat this as delivered.
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
