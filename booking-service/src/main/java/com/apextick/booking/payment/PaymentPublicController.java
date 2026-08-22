package com.apextick.booking.payment;

import com.apextick.booking.config.AppProperties;
import com.apextick.booking.payment.dto.PaymentConfigResponse;
import com.apextick.booking.payment.model.CallbackRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

    public PaymentPublicController(PaymentService payments, PaymentGatewayRegistry registry, AppProperties props) {
        this.payments = payments;
        this.registry = registry;
        this.props = props;
    }

    @GetMapping("/config")
    @Operation(summary = "Active payment provider + Stripe publishable key for the checkout UI")
    public PaymentConfigResponse config() {
        List<String> enabled = registry.providers().stream()
                .map(p -> p.name().toLowerCase(Locale.ROOT)).toList();
        String publishableKey = null;
        AppProperties.Payment.Stripe stripe = props.payment().stripe();
        if (registry.providers().contains(PaymentProvider.STRIPE)
                && stripe != null && stripe.publishableKey() != null && !stripe.publishableKey().isBlank()) {
            publishableKey = stripe.publishableKey();
        }
        return new PaymentConfigResponse(
                registry.defaultProvider().name().toLowerCase(Locale.ROOT), enabled, publishableKey, null);
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
