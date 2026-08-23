package com.apextick.booking.payment.stripe;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Set;

/**
 * Converts between our {@link BigDecimal} major-unit amounts and the integer minor units
 * Stripe charges in. Most currencies use 2 decimal places; a handful are zero-decimal and
 * must be sent as-is (e.g. JPY 1000 = ¥1000, not ¥10.00).
 *
 * @see <a href="https://docs.stripe.com/currencies#zero-decimal">Stripe zero-decimal currencies</a>
 */
public final class StripeAmounts {

    private static final Set<String> ZERO_DECIMAL = Set.of(
            "BIF", "CLP", "DJF", "GNF", "JPY", "KMF", "KRW", "MGA",
            "PYG", "RWF", "UGX", "VND", "VUV", "XAF", "XOF", "XPF");

    private StripeAmounts() {
    }

    public static boolean isZeroDecimal(String currency) {
        return ZERO_DECIMAL.contains(currency.toUpperCase(Locale.ROOT));
    }

    /** Major units -> Stripe minor units (e.g. 150.00 USD -> 15000, 1000 JPY -> 1000). */
    public static long toMinorUnits(BigDecimal amount, String currency) {
        if (isZeroDecimal(currency)) {
            return amount.setScale(0, RoundingMode.HALF_UP).longValueExact();
        }
        return amount.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    /** Stripe minor units -> major units (e.g. 15000 USD -> 150.00, 1000 JPY -> 1000). */
    public static BigDecimal fromMinorUnits(long minor, String currency) {
        return isZeroDecimal(currency) ? BigDecimal.valueOf(minor) : BigDecimal.valueOf(minor, 2);
    }
}
