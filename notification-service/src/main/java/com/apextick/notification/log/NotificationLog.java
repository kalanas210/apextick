package com.apextick.notification.log;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notifications")
public class NotificationLog {

    @Id
    private UUID id;

    @Column(name = "event_id")
    private UUID eventId;

    private String type;
    private String recipient;
    private String subject;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationStatus status;

    @Column(columnDefinition = "text")
    private String error;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected NotificationLog() {
    }

    public NotificationLog(UUID eventId, String type, String recipient, String subject,
                           NotificationStatus status, String error) {
        this.id = UUID.randomUUID();
        this.eventId = eventId;
        this.type = type;
        this.recipient = recipient;
        this.subject = subject;
        this.status = status;
        this.error = error;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public NotificationStatus getStatus() {
        return status;
    }

    public String getRecipient() {
        return recipient;
    }
}
