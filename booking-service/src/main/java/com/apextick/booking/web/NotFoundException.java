package com.apextick.booking.web;

/** Thrown when a requested resource does not exist; mapped to HTTP 404. */
public class NotFoundException extends RuntimeException {
    public NotFoundException(String resource, Object id) {
        super(resource + " " + id + " not found");
    }
    public NotFoundException(String message) {
        super(message);
    }
}
