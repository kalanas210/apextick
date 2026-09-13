package com.apextick.booking.payment;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    List<Payment> findByOrderIdOrderByCreatedAtAsc(UUID orderId);

    /**
     * The payment, with its row locked until the transaction ends. The pay request and a webhook
     * for the same charge both take this lock before they settle it, so one of them settles and
     * the other finds it settled.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.id = :id")
    Optional<Payment> findByIdForUpdate(@Param("id") UUID id);

    /** Locate, and lock, the payment a provider callback refers to (for Stripe, the PaymentIntent id). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.provider = :provider and p.providerRef = :providerRef")
    Optional<Payment> findByProviderAndProviderRefForUpdate(@Param("provider") PaymentProvider provider,
                                                            @Param("providerRef") String providerRef);

    /** The attempt an {@code Idempotency-Key} already made on this order, if it made one. */
    Optional<Payment> findByOrderIdAndIdempotencyKey(UUID orderId, String idempotencyKey);

    List<Payment> findByOrderIdAndStatus(UUID orderId, PaymentStatus status);

    boolean existsByOrderIdAndStatusAndIdNot(UUID orderId, PaymentStatus status, UUID id);
}
