package com.apextick.booking.payment.model;

import java.util.Map;

public record CallbackRequest(String rawBody, Map<String, String> headers, Map<String, String> formParams) {
}
