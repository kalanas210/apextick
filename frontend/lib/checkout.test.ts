import { AxiosError, AxiosHeaders } from "axios";
import { describe, expect, it } from "vitest";
import { cardFormFor, declineMessage, paymentFromError, paymentOutcome } from "./checkout";
import type { Payment, PaymentConfig } from "./types";

const payment = (over: Partial<Payment>): Payment => ({
  paymentId: "p-1",
  orderId: "o-1",
  provider: "STRIPE",
  status: "SUCCEEDED",
  cardBrand: null,
  cardLast4: null,
  failureCode: null,
  failureMessage: null,
  clientSecret: null,
  order: {} as Payment["order"],
  ...over,
});

function answered(status: number, data: unknown) {
  const headers = new AxiosHeaders();
  const error = new AxiosError("Request failed", "ERR_BAD_REQUEST", { headers });
  error.response = { status, data, statusText: "", headers: {}, config: { headers } };
  return error;
}

const stripe = (key: string | null): PaymentConfig => ({
  provider: "stripe",
  enabledProviders: ["stripe"],
  stripePublishableKey: key,
  testMode: true,
  demo: false,
});

describe("cardFormFor", () => {
  it("shows no card form while the payment config is on its way", () => {
    // the raw-card mock form used to render here, under a "Paying with Stripe" label
    expect(cardFormFor(undefined, false)).toBe("loading");
  });

  it("shows the gateway the config names, and nothing when it cannot", () => {
    const mock: PaymentConfig = {
      provider: "mock",
      enabledProviders: ["mock"],
      stripePublishableKey: null,
      testMode: true,
      demo: true,
    };
    expect(cardFormFor(mock, false)).toBe("mock");
    expect(cardFormFor(stripe("pk_test_123"), false)).toBe("stripe");
    expect(cardFormFor(stripe(null), false)).toBe("unavailable");
    expect(cardFormFor(undefined, true)).toBe("unavailable");
  });
});

describe("paymentFromError", () => {
  it("recovers the declined payment a 402 carries", () => {
    const declined = payment({ status: "FAILED", failureMessage: "Your card has insufficient funds." });

    expect(paymentFromError(answered(402, declined))).toEqual(declined);
  });

  it("leaves a problem detail, or no answer at all, to the error handling it already has", () => {
    expect(paymentFromError(answered(409, { code: "PAYMENT_IN_PROGRESS", detail: "Still processing" }))).toBeUndefined();
    expect(paymentFromError(new Error("offline"))).toBeUndefined();
  });
});

describe("paymentOutcome", () => {
  it("sends a charged order to its tickets, and a pending one to wait for the webhook", () => {
    expect(paymentOutcome(payment({ status: "SUCCEEDED" }))).toEqual({ kind: "paid" });
    expect(paymentOutcome(payment({ status: "REQUIRES_ACTION" }))).toEqual({ kind: "pending" });
  });

  it("hands a 3-D Secure challenge to the card form, with the secret it needs", () => {
    expect(paymentOutcome(payment({ status: "REQUIRES_ACTION", clientSecret: "pi_1_secret_2" }))).toEqual({
      kind: "authenticate",
      clientSecret: "pi_1_secret_2",
    });
  });

  it("says why a card was declined, in the provider's words", () => {
    expect(paymentOutcome(payment({ status: "FAILED", failureMessage: "Card was declined" }))).toEqual({
      kind: "declined",
      message: "Card was declined",
    });
  });

  it("explains a charge that went back because the seats were lost", () => {
    const outcome = paymentOutcome(payment({ status: "REFUNDED", failureCode: "seats_lost" }));

    expect(outcome.kind).toBe("refunded");
    expect(outcome.kind === "refunded" && outcome.message).toContain("seats were taken");
  });
});

describe("declineMessage", () => {
  it("names a known decline code when the provider sent no message", () => {
    expect(declineMessage({ failureCode: "insufficient_funds", failureMessage: null })).toBe(
      "Your card has insufficient funds.",
    );
    expect(declineMessage({ failureCode: "something_new", failureMessage: null })).toContain("Try another card");
  });
});
