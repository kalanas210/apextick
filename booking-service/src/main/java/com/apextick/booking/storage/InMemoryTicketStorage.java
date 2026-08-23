package com.apextick.booking.storage;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fallback storage used when no object store is configured (tests, offline demos).
 * PDFs live only for the lifetime of the JVM; the download endpoint regenerates
 * anything it cannot find, so nothing is lost on restart.
 */
public class InMemoryTicketStorage implements TicketStorage {

    private final Map<String, byte[]> objects = new ConcurrentHashMap<>();

    @Override
    public void put(String key, byte[] content, String contentType) {
        objects.put(key, content);
    }

    @Override
    public Optional<byte[]> get(String key) {
        return Optional.ofNullable(objects.get(key));
    }
}
