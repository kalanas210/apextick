package com.apextick.booking.payment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    List<Payment> findByOrderIdOrderByCreatedAtAsc(UUID orderId);

    /** Locate the payment a provider callback refers to (for Stripe, the PaymentIntent id). */
    Optional<Payment> findByProviderAndProviderRef(PaymentProvider provider, String providerRef);
}
