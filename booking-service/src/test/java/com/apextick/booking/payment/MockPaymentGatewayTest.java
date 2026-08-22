package com.apextick.booking.payment;

import com.apextick.booking.payment.mock.MockPaymentGateway;
import com.apextick.booking.payment.model.Customer;
import com.apextick.booking.payment.model.PaymentCard;
import com.apextick.booking.payment.model.PaymentContext;
import com.apextick.booking.payment.model.PaymentInitiation;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MockPaymentGatewayTest {

    private final MockPaymentGateway gateway = new MockPaymentGateway();

    private PaymentContext ctx(String number) {
        return new PaymentContext(UUID.randomUUID(), UUID.randomUUID(), "APX-TEST",
                new BigDecimal("10.00"), "USD", new Customer("s", "e@x", "N"), "k",
                null, null, new PaymentCard(number, 12, 2030, "123", "Holder"));
    }

    @Test
    void visa_test_card_succeeds() {
        PaymentInitiation r = gateway.initiate(ctx("4242424242424242"));
        assertThat(r.outcome()).isEqualTo(PaymentOutcome.SUCCEEDED);
        assertThat(r.cardBrand()).isEqualTo("VISA");
        assertThat(r.cardLast4()).isEqualTo("4242");
        assertThat(r.providerRef()).startsWith("mock_");
    }

    @Test
    void declined_card_is_declined() {
        PaymentInitiation r = gateway.initiate(ctx("4000000000000002"));
        assertThat(r.outcome()).isEqualTo(PaymentOutcome.DECLINED);
        assertThat(r.failureCode()).isEqualTo("card_declined");
    }

    @Test
    void insufficient_funds_card_is_declined() {
        PaymentInitiation r = gateway.initiate(ctx("4000000000009995"));
        assertThat(r.outcome()).isEqualTo(PaymentOutcome.DECLINED);
        assertThat(r.failureCode()).isEqualTo("insufficient_funds");
    }

    @Test
    void non_luhn_card_is_invalid() {
        PaymentInitiation r = gateway.initiate(ctx("1234"));
        assertThat(r.outcome()).isEqualTo(PaymentOutcome.DECLINED);
        assertThat(r.failureCode()).isEqualTo("invalid_card");
    }
}
