package com.apextick.booking.payment;

import com.apextick.booking.config.AppProperties;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static com.apextick.booking.payment.PaymentPublicController.seedsDemo;
import static com.apextick.booking.payment.PaymentPublicController.testMode;
import static org.assertj.core.api.Assertions.assertThat;

/** What the storefront may say about money and sample data, decided from configuration alone. */
class PaymentConfigModeTest {

    private static AppProperties.Payment.Stripe stripe(String secretKey) {
        return new AppProperties.Payment.Stripe(secretKey, "pk_anything", "whsec_anything", Set.of());
    }

    @Test
    void the_mock_gateway_never_takes_real_money() {
        assertThat(testMode(PaymentProvider.MOCK, stripe(""))).isTrue();
    }

    @Test
    void stripe_counts_as_test_mode_only_with_a_test_key() {
        assertThat(testMode(PaymentProvider.STRIPE, stripe("sk_test_51abc"))).isTrue();
        assertThat(testMode(PaymentProvider.STRIPE, stripe("rk_test_51abc"))).isTrue();
        assertThat(testMode(PaymentProvider.STRIPE, stripe("sk_live_51abc"))).isFalse();
        // a key it cannot read is not a promise it can make to a buyer
        assertThat(testMode(PaymentProvider.STRIPE, stripe(""))).isFalse();
        assertThat(testMode(PaymentProvider.STRIPE, null)).isFalse();
    }

    @Test
    void only_the_demo_seed_context_makes_a_demo() {
        assertThat(seedsDemo("demo")).isTrue();
        assertThat(seedsDemo("prod, demo")).isTrue();
        assertThat(seedsDemo("prod")).isFalse();
        assertThat(seedsDemo("")).isFalse();
        assertThat(seedsDemo(null)).isFalse();
    }
}
