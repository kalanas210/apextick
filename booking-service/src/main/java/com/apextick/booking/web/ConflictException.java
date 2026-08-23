package com.apextick.booking.web;

import java.util.Map;

/** Thrown on a business-rule conflict; mapped to HTTP 409 with a {@code code} and extra members. */
public class ConflictException extends RuntimeException {
    private final String code;
    private final transient Map<String, Object> properties;

    public ConflictException(String code, String message) {
        this(code, message, Map.of());
    }

    public ConflictException(String code, String message, Map<String, Object> properties) {
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
