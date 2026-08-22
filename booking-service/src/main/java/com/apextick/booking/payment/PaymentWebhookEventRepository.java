package com.apextick.booking.payment;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentWebhookEventRepository extends JpaRepository<PaymentWebhookEvent, Long> {
    boolean existsByProviderAndExternalEventId(String provider, String externalEventId);
}
