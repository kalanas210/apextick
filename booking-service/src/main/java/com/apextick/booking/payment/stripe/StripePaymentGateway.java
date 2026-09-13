package com.apextick.booking.payment.stripe;

import com.apextick.booking.config.AppProperties;
import com.apextick.booking.payment.PaymentGateway;
import com.apextick.booking.payment.PaymentOutcome;
import com.apextick.booking.payment.PaymentProvider;
import com.apextick.booking.payment.WebhookVerificationException;
import com.apextick.booking.payment.model.CallbackRequest;
import com.apextick.booking.payment.model.PaymentContext;
import com.apextick.booking.payment.model.PaymentInitiation;
import com.apextick.booking.payment.model.PaymentResult;
import com.apextick.booking.payment.model.RefundResult;
import com.stripe.exception.CardException;
import com.stripe.exception.EventDataObjectDeserializationException;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Charge;
import com.stripe.model.Dispute;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Refund;
import com.stripe.model.StripeObject;
import com.stripe.net.RequestOptions;
import com.stripe.net.Webhook;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.RefundCreateParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Stripe adapter built on PaymentIntents. Two entry points to a charge:
 * <ul>
 *   <li><b>Client-confirm</b> (default): {@link #initiate} creates an intent and returns its
 *       {@code client_secret}; the browser confirms with Stripe.js so the PAN never reaches us,
 *       and the authoritative result arrives via {@code payment_intent.succeeded} on the webhook.</li>
 *   <li><b>Server-confirm</b>: when the request carries a {@code paymentMethodId}
 *       (e.g. {@code pm_card_visa} in tests, or a token minted by Stripe.js), the intent is
 *       created and confirmed in one call and resolves synchronously.</li>
 * </ul>
 * Raw card numbers are never sent to Stripe from the server. Only card brand + last4 are retained.
 */
@Component
public class StripePaymentGateway implements PaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(StripePaymentGateway.class);

    private final String secretKey;
    private final String webhookSecret;
    private final Set<String> supportedCurrencies;

    public StripePaymentGateway(AppProperties props) {
        AppProperties.Payment.Stripe cfg = props.payment().stripe();
        this.secretKey = cfg == null ? null : cfg.secretKey();
        this.webhookSecret = cfg == null ? null : cfg.webhookSecret();
        this.supportedCurrencies = cfg == null || cfg.supportedCurrencies() == null
                ? Set.of() : cfg.supportedCurrencies();
    }

    @Override
    public PaymentProvider provider() {
        return PaymentProvider.STRIPE;
    }

    @Override
    public boolean supports(String currency) {
        if (supportedCurrencies.isEmpty()) {
            return true;
        }
        return supportedCurrencies.stream().anyMatch(c -> c.equalsIgnoreCase(currency));
    }

    @Override
    public PaymentInitiation initiate(PaymentContext ctx) {
        long amount = StripeAmounts.toMinorUnits(ctx.amount(), ctx.currency());
        PaymentIntentCreateParams.Builder params = PaymentIntentCreateParams.builder()
                .setAmount(amount)
                .setCurrency(ctx.currency().toLowerCase(Locale.ROOT))
                .setDescription("ApexTick order " + ctx.orderNumber())
                .putMetadata("orderId", ctx.orderId().toString())
                .putMetadata("orderNumber", ctx.orderNumber())
                .putMetadata("paymentId", ctx.paymentId().toString())
                .putMetadata("sub", ctx.customer().sub())
                .setAutomaticPaymentMethods(PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                        .setEnabled(true)
                        // server-side flow can't handle a redirect, so keep it to no-redirect methods
                        .setAllowRedirects(PaymentIntentCreateParams.AutomaticPaymentMethods.AllowRedirects.NEVER)
                        .build());

        boolean serverConfirm = ctx.paymentMethodId() != null && !ctx.paymentMethodId().isBlank();
        if (serverConfirm) {
            params.setPaymentMethod(ctx.paymentMethodId()).setConfirm(true).addExpand("latest_charge");
        }

        try {
            PaymentIntent pi = PaymentIntent.create(params.build(), options(ctx.idempotencyKey()));
            return serverConfirm ? mapConfirmed(pi) : PaymentInitiation.requiresAction(pi.getId(), pi.getClientSecret());
        } catch (CardException e) {
            log.info("Stripe card declined for order {}: {}", ctx.orderNumber(), e.getCode());
            return PaymentInitiation.declined(null, null,
                    e.getDeclineCode() != null ? e.getDeclineCode() : e.getCode(), e.getMessage());
        } catch (StripeException e) {
            log.warn("Stripe error initiating payment for order {}", ctx.orderNumber(), e);
            return PaymentInitiation.failed("stripe_error", e.getMessage());
        }
    }

    private PaymentInitiation mapConfirmed(PaymentIntent pi) {
        String status = pi.getStatus();
        return switch (status) {
            case "succeeded" -> {
                String[] card = cardOf(pi);
                yield PaymentInitiation.succeeded(pi.getId(), card[0], card[1]);
            }
            // 3-D Secure or an async method still settling -> let the client / webhook finish it
            case "requires_action", "processing", "requires_confirmation" ->
                    PaymentInitiation.requiresAction(pi.getId(), pi.getClientSecret());
            default -> { // requires_payment_method, canceled -> the card was declined at confirm time
                com.stripe.model.StripeError err = pi.getLastPaymentError();
                String code = err == null ? "card_declined"
                        : (err.getDeclineCode() != null ? err.getDeclineCode() : err.getCode());
                String message = err == null ? "Card was declined" : err.getMessage();
                String[] card = cardOf(pi);
                yield PaymentInitiation.declined(card[0], card[1], code, message);
            }
        };
    }

    @Override
    public Optional<PaymentResult> verifyCallback(CallbackRequest req) {
        if (webhookSecret == null || webhookSecret.isBlank()) {
            // mock mode: the endpoint is still public, but nothing can be verified
            throw new WebhookVerificationException("Stripe webhooks are not configured");
        }
        String signature = header(req, "Stripe-Signature");
        if (signature == null || signature.isBlank()) {
            throw new WebhookVerificationException("Missing Stripe-Signature header");
        }
        // constructEvent parses the body before it checks the signature, so an
        // unsigned non-JSON body would surface as a parse error (500); verify first.
        try {
            Webhook.Signature.verifyHeader(req.rawBody(), signature, webhookSecret, Webhook.DEFAULT_TOLERANCE);
        } catch (SignatureVerificationException e) {
            throw new WebhookVerificationException("Invalid Stripe signature");
        } catch (RuntimeException e) {
            // stripe-java parses the header without guarding it: "t" or "t=1,v1" throw
            // ArrayIndexOutOfBounds, "t=abc" NumberFormat
            throw new WebhookVerificationException("Malformed Stripe-Signature header");
        }
        Event event;
        try {
            event = Webhook.constructEvent(req.rawBody(), signature, webhookSecret);
        } catch (SignatureVerificationException e) {
            throw new WebhookVerificationException("Invalid Stripe signature");
        }

        return switch (event.getType()) {
            case "payment_intent.succeeded" -> intentResult(event, req, PaymentOutcome.SUCCEEDED);
            case "payment_intent.payment_failed" -> intentResult(event, req, PaymentOutcome.FAILED);
            // an intent given up on: a 3-D Secure challenge abandoned, or one cancelled in the dashboard
            case "payment_intent.canceled" -> intentResult(event, req, PaymentOutcome.CANCELLED);
            case "charge.refunded" -> refundResult(event, req);
            case "charge.dispute.created" -> disputeResult(event, req);
            default -> Optional.empty(); // an event type we don't act on -> acknowledge with 200, no state change
        };
    }

    private Optional<PaymentResult> intentResult(Event event, CallbackRequest req, PaymentOutcome outcome) {
        if (!(dataObject(event) instanceof PaymentIntent pi)) {
            log.warn("Stripe {} event {} carried no PaymentIntent", event.getType(), event.getId());
            return Optional.empty();
        }
        String currency = pi.getCurrency() == null ? null : pi.getCurrency().toUpperCase(Locale.ROOT);
        Long minor = pi.getAmountReceived() != null && pi.getAmountReceived() > 0
                ? pi.getAmountReceived() : pi.getAmount();
        String[] card = cardOf(pi);
        String failureCode = null;
        if (outcome == PaymentOutcome.FAILED && pi.getLastPaymentError() != null) {
            com.stripe.model.StripeError err = pi.getLastPaymentError();
            failureCode = err.getDeclineCode() != null ? err.getDeclineCode() : err.getCode();
        } else if (outcome == PaymentOutcome.CANCELLED) {
            failureCode = pi.getCancellationReason() == null ? "canceled" : pi.getCancellationReason();
        }
        // stamped on the intent by initiate(), so a callback can name its payment even before the
        // intent's id has been recorded against it
        String paymentId = pi.getMetadata() == null ? null : pi.getMetadata().get("paymentId");
        return Optional.of(new PaymentResult(event.getId(), pi.getId(), paymentId, outcome, amountOf(minor, currency),
                currency, card[0], card[1], failureCode, req.rawBody()));
    }

    /**
     * A charge refunded in Stripe itself: in the dashboard, or the refund this service asked for,
     * reported back. Only a charge refunded in full is passed on. A partial refund in the dashboard
     * is a goodwill gesture, not the end of the order.
     */
    private Optional<PaymentResult> refundResult(Event event, CallbackRequest req) {
        if (!(dataObject(event) instanceof Charge charge) || charge.getPaymentIntent() == null) {
            log.warn("Stripe {} event {} carried no charge of a PaymentIntent", event.getType(), event.getId());
            return Optional.empty();
        }
        if (!Boolean.TRUE.equals(charge.getRefunded())) {
            log.info("Stripe charge {} of intent {} was partly refunded ({} of {}); its order stands",
                    charge.getId(), charge.getPaymentIntent(), charge.getAmountRefunded(), charge.getAmount());
            return Optional.empty();
        }
        String currency = charge.getCurrency() == null ? null : charge.getCurrency().toUpperCase(Locale.ROOT);
        return Optional.of(new PaymentResult(event.getId(), charge.getPaymentIntent(), null, PaymentOutcome.REFUNDED,
                amountOf(charge.getAmountRefunded(), currency), currency, null, null, null, req.rawBody()));
    }

    /** The cardholder disputed a charge with their bank: the money is being clawed back. */
    private Optional<PaymentResult> disputeResult(Event event, CallbackRequest req) {
        if (!(dataObject(event) instanceof Dispute dispute) || dispute.getPaymentIntent() == null) {
            log.warn("Stripe {} event {} carried no dispute of a PaymentIntent", event.getType(), event.getId());
            return Optional.empty();
        }
        String currency = dispute.getCurrency() == null ? null : dispute.getCurrency().toUpperCase(Locale.ROOT);
        return Optional.of(new PaymentResult(event.getId(), dispute.getPaymentIntent(), null,
                PaymentOutcome.CHARGEBACK, amountOf(dispute.getAmount(), currency), currency, null, null,
                dispute.getReason(), req.rawBody()));
    }

    @Override
    public RefundResult refund(String providerRef, BigDecimal amount, String currency, String idempotencyKey) {
        try {
            RefundCreateParams params = RefundCreateParams.builder()
                    .setPaymentIntent(providerRef)
                    .setAmount(StripeAmounts.toMinorUnits(amount, currency))
                    .build();
            // the caller's key names this refund, so asking again can only return the same refund
            Refund refund = Refund.create(params, options(idempotencyKey));
            return new RefundResult(true, refund.getId(), null);
        } catch (StripeException e) {
            if ("charge_already_refunded".equals(e.getCode())) {
                // refunded already -- in the dashboard, or by an ask whose answer never arrived: the
                // money is back either way, and asking again forever would not change that
                log.info("Stripe intent {} was already refunded", providerRef);
                return new RefundResult(true, null, null);
            }
            log.error("Stripe refund failed for intent {}", providerRef, e);
            return new RefundResult(false, null, e.getMessage());
        }
    }

    private static BigDecimal amountOf(Long minor, String currency) {
        return minor == null || currency == null ? null : StripeAmounts.fromMinorUnits(minor, currency);
    }

    /** brand (upper-case) + last4 from the intent's latest charge, or {nulls} when unavailable. */
    private static String[] cardOf(PaymentIntent pi) {
        Charge charge = pi.getLatestChargeObject();
        if (charge != null && charge.getPaymentMethodDetails() != null
                && charge.getPaymentMethodDetails().getCard() != null) {
            Charge.PaymentMethodDetails.Card c = charge.getPaymentMethodDetails().getCard();
            String brand = c.getBrand() == null ? null : c.getBrand().toUpperCase(Locale.ROOT);
            return new String[]{brand, c.getLast4()};
        }
        return new String[]{null, null};
    }

    private static StripeObject dataObject(Event event) {
        EventDataObjectDeserializer deserializer = event.getDataObjectDeserializer();
        StripeObject obj = deserializer.getObject().orElse(null);
        if (obj == null) {
            // API-version drift between the event and the pinned library; deserialize best-effort
            try {
                obj = deserializer.deserializeUnsafe();
            } catch (EventDataObjectDeserializationException e) {
                return null;
            }
        }
        return obj;
    }

    private RequestOptions options(String idempotencyKey) {
        RequestOptions.RequestOptionsBuilder b = RequestOptions.builder().setApiKey(requireSecretKey());
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            b.setIdempotencyKey(idempotencyKey);
        }
        return b.build();
    }

    private String requireSecretKey() {
        if (secretKey == null || secretKey.isBlank()) {
            throw new IllegalStateException("Stripe is selected but STRIPE_SECRET_KEY is not configured");
        }
        return secretKey;
    }

    private static String header(CallbackRequest req, String name) {
        if (req.headers() == null) {
            return null;
        }
        return req.headers().entrySet().stream()
                .filter(e -> e.getKey() != null && e.getKey().equalsIgnoreCase(name))
                .map(java.util.Map.Entry::getValue).findFirst().orElse(null);
    }
}
