package com.apextick.booking.payment;

import com.apextick.booking.order.Order;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payments")
@Getter
@Setter
@NoArgsConstructor
public class Payment {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentProvider provider;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    private String currency;

    @Column(name = "idempotency_key")
    private String idempotencyKey;

    @Column(name = "provider_ref")
    private String providerRef;

    @Column(name = "client_secret")
    private String clientSecret;

    @Column(name = "card_brand")
    private String cardBrand;

    @Column(name = "card_last4")
    private String cardLast4;

    @Column(name = "failure_code")
    private String failureCode;

    @Column(name = "failure_message")
    private String failureMessage;

    @Column(name = "raw_result", columnDefinition = "text")
    private String rawResult;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    /** The provider's id for the refund of this charge, once one has been accepted. */
    @Column(name = "refund_ref")
    private String refundRef;

    @Column(name = "refunded_at")
    private Instant refundedAt;

    /**
     * What is owed back, once a refund has been asked for: the whole charge, or -- for a charge
     * that took a sum other than the payment's -- exactly what it took.
     */
    @Column(name = "refund_amount", precision = 12, scale = 2)
    private BigDecimal refundAmount;

    @Column(name = "refund_currency")
    private String refundCurrency;

    /** How many times the provider has been asked for this refund. */
    @Column(name = "refund_attempts", nullable = false)
    private int refundAttempts;

    @Column(name = "refund_last_attempt_at")
    private Instant refundLastAttemptAt;

    /** The provider's answer the last time it refused the refund. */
    @Column(name = "refund_error")
    private String refundError;
}
