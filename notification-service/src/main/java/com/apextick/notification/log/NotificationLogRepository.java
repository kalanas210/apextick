package com.apextick.notification.log;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface NotificationLogRepository extends JpaRepository<NotificationLog, UUID> {
    List<NotificationLog> findByEventId(UUID eventId);
    long countByStatus(NotificationStatus status);
}
