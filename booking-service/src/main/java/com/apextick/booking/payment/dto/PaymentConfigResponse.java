package com.apextick.booking.payment.dto;

import java.util.List;

/**
 * Public configuration for the storefront: which payment provider is active, which are wired, the Stripe
 * publishable key the browser needs to initialise Stripe.js, and what the site may honestly say about itself.
 *
 * @param testMode no real money can be taken: the mock gateway, or Stripe with test keys. The storefront only
 *                 tells a buyer that no real money is charged when this is true.
 * @param demo     the database was seeded with the demo season and its shared account (the {@code demo}
 *                 Liquibase context), so fixtures, prices and seats are sample data.
 */
public record PaymentConfigResponse(
        String provider, List<String> enabledProviders, String stripePublishableKey, boolean testMode,
        boolean demo) {
}
