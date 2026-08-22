package com.apextick.notification.log;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class NotificationLogService {

    private final NotificationLogRepository logs;

    public NotificationLogService(NotificationLogRepository logs) {
        this.logs = logs;
    }

    public void recordSent(UUID eventId, String type, String recipient, String subject) {
        logs.save(new NotificationLog(eventId, type, recipient, subject, NotificationStatus.SENT, null));
    }

    public void recordSkipped(UUID eventId, String type, String reason) {
        logs.save(new NotificationLog(eventId, type, null, null, NotificationStatus.SKIPPED, reason));
    }

    /** Persisted in its own transaction so it survives the rolled-back consumer transaction. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailed(UUID eventId, String type, String recipient, String error) {
        logs.save(new NotificationLog(eventId, type, recipient, null, NotificationStatus.FAILED, error));
    }
}
