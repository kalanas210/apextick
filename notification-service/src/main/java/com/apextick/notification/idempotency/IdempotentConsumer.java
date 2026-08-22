package com.apextick.notification.idempotency;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Runs an action at most once per event id. The claim + action share a transaction, so a
 *  failure rolls the claim back and the message is retried (then dead-lettered). */
@Service
public class IdempotentConsumer {

    private final ProcessedEventRepository processed;

    public IdempotentConsumer(ProcessedEventRepository processed) {
        this.processed = processed;
    }

    @Transactional
    public boolean runOnce(UUID eventId, String type, Runnable action) {
        if (processed.claim(eventId, type) == 0) {
            return false;
        }
        action.run();
        return true;
    }
}
