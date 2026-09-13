package com.apextick.booking.payment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    List<Payment> findByOrderIdOrderByCreatedAtAsc(UUID orderId);

    /** Locate the payment a provider callback refers to (for Stripe, the PaymentIntent id). */
    Optional<Payment> findByProviderAndProviderRef(PaymentProvider provider, String providerRef);

    /** The attempt an {@code Idempotency-Key} already made on this order, if it made one. */
    Optional<Payment> findByOrderIdAndIdempotencyKey(UUID orderId, String idempotencyKey);

    List<Payment> findByOrderIdAndStatus(UUID orderId, PaymentStatus status);
}
