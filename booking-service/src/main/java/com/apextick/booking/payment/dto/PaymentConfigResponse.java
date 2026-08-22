package com.apextick.booking.payment.dto;

import java.util.List;

/**
 * Public payment configuration for the checkout UI: which provider is active, which are wired,
 * and the Stripe publishable key the browser needs to initialise Stripe.js.
 */
public record PaymentConfigResponse(
        String provider, List<String> enabledProviders, String stripePublishableKey, Boolean payhereSandbox) {
}
