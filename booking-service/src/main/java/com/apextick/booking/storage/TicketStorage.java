package com.apextick.booking.storage;

import java.util.Optional;

/**
 * Object storage for rendered ticket PDFs (MinIO locally, S3 in production).
 * Stored objects are a cache of a derived artefact: anything in here can be
 * regenerated from the database, so a miss is never fatal.
 */
public interface TicketStorage {

    void put(String key, byte[] content, String contentType);

    Optional<byte[]> get(String key);
}
