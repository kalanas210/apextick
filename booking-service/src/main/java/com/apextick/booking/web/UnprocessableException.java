package com.apextick.booking.web;

import java.util.Map;

/** Thrown when a request is well-formed but semantically invalid; mapped to HTTP 422. */
public class UnprocessableException extends RuntimeException {
    private final String code;
    private final transient Map<String, Object> properties;

    public UnprocessableException(String code, String message) {
        this(code, message, Map.of());
    }

    public UnprocessableException(String code, String message, Map<String, Object> properties) {
        super(message);
        this.code = code;
        this.properties = properties;
    }

    public String getCode() {
        return code;
    }

    public Map<String, Object> getProperties() {
        return properties;
    }
}
