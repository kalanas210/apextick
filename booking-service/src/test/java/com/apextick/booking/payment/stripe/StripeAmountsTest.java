package com.apextick.booking.payment.stripe;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class StripeAmountsTest {

    @Test
    void two_decimal_currencies_are_scaled_to_minor_units() {
        assertThat(StripeAmounts.toMinorUnits(new BigDecimal("150.00"), "USD")).isEqualTo(15000L);
        assertThat(StripeAmounts.toMinorUnits(new BigDecimal("2499.99"), "GBP")).isEqualTo(249999L);
        assertThat(StripeAmounts.toMinorUnits(new BigDecimal("500.00"), "INR")).isEqualTo(50000L);
    }

    @Test
    void zero_decimal_currencies_are_sent_as_is() {
        assertThat(StripeAmounts.isZeroDecimal("JPY")).isTrue();
        assertThat(StripeAmounts.toMinorUnits(new BigDecimal("1000"), "JPY")).isEqualTo(1000L);
        assertThat(StripeAmounts.toMinorUnits(new BigDecimal("1000.00"), "jpy")).isEqualTo(1000L);
    }

    @Test
    void round_trips_back_to_major_units() {
        assertThat(StripeAmounts.fromMinorUnits(15000L, "USD")).isEqualByComparingTo("150.00");
        assertThat(StripeAmounts.fromMinorUnits(1000L, "JPY")).isEqualByComparingTo("1000");
    }
}
