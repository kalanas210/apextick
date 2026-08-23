package com.apextick.booking.payment.mock;

import com.apextick.booking.payment.PaymentGateway;
import com.apextick.booking.payment.PaymentProvider;
import com.apextick.booking.payment.model.CallbackRequest;
import com.apextick.booking.payment.model.PaymentContext;
import com.apextick.booking.payment.model.PaymentInitiation;
import com.apextick.booking.payment.model.PaymentResult;
import com.apextick.booking.payment.model.RefundResult;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/**
 * Deterministic offline gateway for demos/tests. Test cards:
 *  4242 4242 4242 4242 -> success, 4000 0000 0000 0002 -> declined,
 *  4000 0000 0000 9995 -> insufficient funds. Only brand + last4 are ever retained.
 */
@Component
public class MockPaymentGateway implements PaymentGateway {

    @Override
    public PaymentProvider provider() {
        return PaymentProvider.MOCK;
    }

    @Override
    public boolean supports(String currency) {
        return true;
    }

    @Override
    public PaymentInitiation initiate(PaymentContext ctx) {
        String number = ctx.card() == null ? "" : ctx.card().number().replaceAll("\\s", "");
        String last4 = number.length() >= 4 ? number.substring(number.length() - 4) : "0000";
        String brand = brandOf(number);
        if (number.isBlank() || !luhnValid(number)) {
            return PaymentInitiation.declined(brand, last4, "invalid_card", "Card number is invalid");
        }
        return switch (number) {
            case "4000000000000002" -> PaymentInitiation.declined(brand, last4, "card_declined", "Card was declined");
            case "4000000000009995" -> PaymentInitiation.declined(brand, last4, "insufficient_funds", "Insufficient funds");
            default -> PaymentInitiation.succeeded("mock_" + UUID.randomUUID(), brand, last4);
        };
    }

    @Override
    public Optional<PaymentResult> verifyCallback(CallbackRequest req) {
        return Optional.empty();
    }

    @Override
    public RefundResult refund(String providerRef, BigDecimal amount, String currency, String idempotencyKey) {
        return new RefundResult(true, "mock_refund_" + UUID.randomUUID(), null);
    }

    private static String brandOf(String number) {
        if (number.startsWith("4")) {
            return "VISA";
        }
        if (number.startsWith("5") || number.startsWith("2")) {
            return "MASTERCARD";
        }
        if (number.startsWith("34") || number.startsWith("37")) {
            return "AMEX";
        }
        return "CARD";
    }

    private static boolean luhnValid(String number) {
        if (!number.matches("\\d{12,19}")) {
            return false;
        }
        int sum = 0;
        boolean alt = false;
        for (int i = number.length() - 1; i >= 0; i--) {
            int d = number.charAt(i) - '0';
            if (alt) {
                d *= 2;
                if (d > 9) {
                    d -= 9;
                }
            }
            sum += d;
            alt = !alt;
        }
        return sum % 10 == 0;
    }
}
