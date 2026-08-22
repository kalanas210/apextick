package com.apextick.booking.payment.stripe;

import com.apextick.booking.config.AppProperties;
import com.apextick.booking.payment.PaymentOutcome;
import com.apextick.booking.payment.WebhookVerificationException;
import com.apextick.booking.payment.model.CallbackRequest;
import com.apextick.booking.payment.model.Customer;
import com.apextick.booking.payment.model.PaymentContext;
import com.apextick.booking.payment.model.PaymentInitiation;
import com.apextick.booking.payment.model.PaymentResult;
import com.stripe.Stripe;
import com.stripe.model.Charge;
import com.stripe.model.PaymentIntent;
import com.stripe.model.StripeError;
import com.stripe.net.RequestOptions;
import com.stripe.param.PaymentIntentCreateParams;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/** Offline unit tests for the Stripe adapter: real signature verification + a mocked Stripe API. */
class StripePaymentGatewayTest {

    private static final String WHSEC = "whsec_test_secret_key";

    private StripePaymentGateway gateway(Set<String> currencies) {
        AppProperties.Payment.Stripe stripe =
                new AppProperties.Payment.Stripe("sk_test_key", "pk_test_key", WHSEC, currencies);
        AppProperties props = new AppProperties(null, null, null,
                new AppProperties.Payment("stripe", stripe), null);
        return new StripePaymentGateway(props);
    }

    private PaymentContext ctx(String paymentMethodId) {
        return new PaymentContext(UUID.randomUUID(), UUID.randomUUID(), "ORD-1",
                new BigDecimal("150.00"), "USD", new Customer("sub-1", "buyer@apextick.local", "Buyer"),
                "idem-1", null, null, null, paymentMethodId);
    }

    @Test
    void supports_accepts_everything_when_no_allow_list_is_configured() {
        StripePaymentGateway g = gateway(Set.of());
        assertThat(g.supports("USD")).isTrue();
        assertThat(g.supports("XYZ")).isTrue();
    }

    @Test
    void supports_honours_a_configured_allow_list_case_insensitively() {
        StripePaymentGateway g = gateway(Set.of("usd", "gbp"));
        assertThat(g.supports("USD")).isTrue();
        assertThat(g.supports("gbp")).isTrue();
        assertThat(g.supports("INR")).isFalse();
    }

    @Test
    void server_confirm_success_maps_to_SUCCEEDED_with_card_details() {
        Charge.PaymentMethodDetails.Card card = mock(Charge.PaymentMethodDetails.Card.class);
        when(card.getBrand()).thenReturn("visa");
        when(card.getLast4()).thenReturn("4242");
        Charge.PaymentMethodDetails pmd = mock(Charge.PaymentMethodDetails.class);
        when(pmd.getCard()).thenReturn(card);
        Charge charge = mock(Charge.class);
        when(charge.getPaymentMethodDetails()).thenReturn(pmd);

        PaymentIntent pi = mock(PaymentIntent.class);
        when(pi.getId()).thenReturn("pi_success");
        when(pi.getStatus()).thenReturn("succeeded");
        when(pi.getLatestChargeObject()).thenReturn(charge);

        try (MockedStatic<PaymentIntent> mocked = mockStatic(PaymentIntent.class)) {
            mocked.when(() -> PaymentIntent.create(any(PaymentIntentCreateParams.class), any(RequestOptions.class)))
                    .thenReturn(pi);

            PaymentInitiation init = gateway(Set.of()).initiate(ctx("pm_card_visa"));

            assertThat(init.outcome()).isEqualTo(PaymentOutcome.SUCCEEDED);
            assertThat(init.providerRef()).isEqualTo("pi_success");
            assertThat(init.cardBrand()).isEqualTo("VISA");
            assertThat(init.cardLast4()).isEqualTo("4242");
        }
    }

    @Test
    void client_confirm_without_a_payment_method_returns_client_secret_for_the_browser() {
        PaymentIntent pi = mock(PaymentIntent.class);
        when(pi.getId()).thenReturn("pi_client");
        when(pi.getClientSecret()).thenReturn("pi_client_secret_abc");

        try (MockedStatic<PaymentIntent> mocked = mockStatic(PaymentIntent.class)) {
            mocked.when(() -> PaymentIntent.create(any(PaymentIntentCreateParams.class), any(RequestOptions.class)))
                    .thenReturn(pi);

            PaymentInitiation init = gateway(Set.of()).initiate(ctx(null));

            assertThat(init.outcome()).isEqualTo(PaymentOutcome.REQUIRES_ACTION);
            assertThat(init.providerRef()).isEqualTo("pi_client");
            assertThat(init.clientSecret()).isEqualTo("pi_client_secret_abc");
        }
    }

    @Test
    void server_confirm_declined_maps_to_DECLINED_with_the_stripe_code() {
        StripeError err = mock(StripeError.class);
        when(err.getCode()).thenReturn("card_declined");
        when(err.getDeclineCode()).thenReturn("insufficient_funds");
        when(err.getMessage()).thenReturn("Your card has insufficient funds.");

        PaymentIntent pi = mock(PaymentIntent.class);
        when(pi.getId()).thenReturn("pi_declined");
        when(pi.getStatus()).thenReturn("requires_payment_method");
        when(pi.getLastPaymentError()).thenReturn(err);

        try (MockedStatic<PaymentIntent> mocked = mockStatic(PaymentIntent.class)) {
            mocked.when(() -> PaymentIntent.create(any(PaymentIntentCreateParams.class), any(RequestOptions.class)))
                    .thenReturn(pi);

            PaymentInitiation init = gateway(Set.of()).initiate(ctx("pm_card_chargeDeclined"));

            assertThat(init.outcome()).isEqualTo(PaymentOutcome.DECLINED);
            assertThat(init.failureCode()).isEqualTo("insufficient_funds");
        }
    }

    @Test
    void verifyCallback_accepts_a_correctly_signed_success_event() throws Exception {
        String payload = eventJson("payment_intent.succeeded");
        String header = sign(payload, WHSEC, System.currentTimeMillis() / 1000);

        Optional<PaymentResult> result = gateway(Set.of())
                .verifyCallback(new CallbackRequest(payload, Map.of("Stripe-Signature", header), Map.of()));

        assertThat(result).isPresent();
        assertThat(result.get().outcome()).isEqualTo(PaymentOutcome.SUCCEEDED);
        assertThat(result.get().providerRef()).isEqualTo("pi_test_1");
        assertThat(result.get().externalEventId()).isEqualTo("evt_1");
        assertThat(result.get().currency()).isEqualTo("USD");
        assertThat(result.get().amount()).isEqualByComparingTo("150.00");
    }

    @Test
    void verifyCallback_ignores_event_types_we_do_not_act_on() throws Exception {
        String payload = eventJson("customer.created");
        String header = sign(payload, WHSEC, System.currentTimeMillis() / 1000);

        Optional<PaymentResult> result = gateway(Set.of())
                .verifyCallback(new CallbackRequest(payload, Map.of("Stripe-Signature", header), Map.of()));

        assertThat(result).isEmpty();
    }

    @Test
    void verifyCallback_rejects_a_bad_signature() {
        String payload = eventJson("payment_intent.succeeded");
        String header = "t=" + (System.currentTimeMillis() / 1000) + ",v1=deadbeef";

        assertThatThrownBy(() -> gateway(Set.of())
                .verifyCallback(new CallbackRequest(payload, Map.of("Stripe-Signature", header), Map.of())))
                .isInstanceOf(WebhookVerificationException.class);
    }

    private static String eventJson(String type) {
        return String.format(
                "{\"id\":\"evt_1\",\"object\":\"event\",\"api_version\":\"%s\",\"type\":\"%s\","
                        + "\"data\":{\"object\":{\"id\":\"pi_test_1\",\"object\":\"payment_intent\","
                        + "\"amount\":15000,\"amount_received\":15000,\"currency\":\"usd\",\"status\":\"succeeded\"}}}",
                Stripe.API_VERSION, type);
    }

    private static String sign(String payload, String secret, long timestamp) throws Exception {
        String signedPayload = timestamp + "." + payload;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] hash = mac.doFinal(signedPayload.getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder(hash.length * 2);
        for (byte b : hash) {
            hex.append(String.format("%02x", b & 0xff));
        }
        return "t=" + timestamp + ",v1=" + hex;
    }
}
