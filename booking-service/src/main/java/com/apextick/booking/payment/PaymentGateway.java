package com.apextick.booking.payment;

import com.apextick.booking.payment.model.CallbackRequest;
import com.apextick.booking.payment.model.PaymentContext;
import com.apextick.booking.payment.model.PaymentInitiation;
import com.apextick.booking.payment.model.PaymentResult;
import com.apextick.booking.payment.model.RefundResult;

import java.math.BigDecimal;
import java.util.Optional;

/** A payment provider. Implementations must never persist or log full card numbers. */
public interface PaymentGateway {

    PaymentProvider provider();

    boolean supports(String currency);

    /** Start a charge. For the mock this resolves synchronously; external gateways may return REQUIRES_ACTION/REDIRECT. */
    PaymentInitiation initiate(PaymentContext ctx);

    /** Verify + normalise a provider callback (webhook/notify). Empty if this gateway has no callbacks. */
    Optional<PaymentResult> verifyCallback(CallbackRequest req);

    RefundResult refund(String providerRef, BigDecimal amount, String currency, String idempotencyKey);
}
