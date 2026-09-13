package com.apextick.booking.payment;

import com.apextick.booking.payment.mock.MockPaymentGateway;
import com.apextick.booking.payment.model.RefundResult;
import com.apextick.booking.payment.stripe.StripePaymentGateway;
import com.apextick.booking.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

/**
 * Base for integration tests that watch or steer a payment gateway. The spies wrap the real
 * gateway beans, so nothing is faked until a test stubs it, and a Stripe webhook secret is set so
 * deliveries are signed and verified for real.
 *
 * <p>Every override lives here, once, on purpose: a test class that declared its own spies or
 * properties would get an application context of its own, and with it its own containers.
 */
@IntegrationTest
@TestPropertySource(properties = "app.payment.stripe.webhook-secret=" + PaymentGatewaySpies.WEBHOOK_SECRET)
public abstract class PaymentGatewaySpies {

    public static final String WEBHOOK_SECRET = "whsec_apextick_integration";

    @MockitoSpyBean
    protected StripePaymentGateway stripe;

    @MockitoSpyBean
    protected MockPaymentGateway mockGateway;

    /** No test may reach the real Stripe API: a refund is accepted unless a test says otherwise. */
    @BeforeEach
    void acceptRefunds() {
        doReturn(new RefundResult(true, "re_test_accepted", null))
                .when(stripe).refund(any(), any(), any(), any());
    }
}
