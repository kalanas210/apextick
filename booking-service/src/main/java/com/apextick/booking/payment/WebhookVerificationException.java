package com.apextick.booking.payment;

/** Raised when a provider webhook fails signature verification; mapped to HTTP 400. */
public class WebhookVerificationException extends RuntimeException {
    public WebhookVerificationException(String message) {
        super(message);
    }
}
