package com.apextick.booking.payment.model;

import java.util.Map;

/** Hosted-checkout parameters (e.g. PayHere): the client auto-submits {@code fields} to {@code action}. */
public record CheckoutForm(String action, String method, Map<String, String> fields) {
}
