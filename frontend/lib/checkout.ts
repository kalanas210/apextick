import axios from "axios";
import type { Payment, PaymentConfig } from "./types";

/**
 * The card form a checkout can show. Nothing until the payment config has said which gateway is
 * live: the mock form takes a raw card number, and showing it while a Stripe config is still on its
 * way would post a real card to the booking service under a "Paying with Stripe" label.
 */
export type CardForm = "loading" | "mock" | "stripe" | "unavailable";

export function cardFormFor(config: PaymentConfig | undefined, failed: boolean): CardForm {
  if (config?.provider === "mock") return "mock";
  if (config?.provider === "stripe") return config.stripePublishableKey ? "stripe" : "unavailable";
  if (config) return "unavailable";
  return failed ? "unavailable" : "loading";
}

/**
 * The payment behind a failed pay request. A decline (402) or a charge refunded because the order
 * could not take it (409) answers with the payment itself rather than a problem detail, and the
 * payment is where the real reason is.
 */
export function paymentFromError(error: unknown): Payment | undefined {
  if (!axios.isAxiosError(error)) return undefined;
  const body = error.response?.data as Partial<Payment> | undefined;
  return body && typeof body.paymentId === "string" && typeof body.status === "string"
    ? (body as Payment)
    : undefined;
}

export type PaymentOutcome =
  | { kind: "paid" }
  | { kind: "authenticate"; clientSecret: string }
  | { kind: "pending" }
  | { kind: "declined"; message: string }
  | { kind: "refunded"; message: string };

/** What a payment answer means for the buyer who is still on the checkout page. */
export function paymentOutcome(payment: Payment): PaymentOutcome {
  switch (payment.status) {
    case "SUCCEEDED":
      return { kind: "paid" };
    case "REQUIRES_ACTION":
      // 3-D Secure: the card's bank wants the buyer to confirm, in the page, before anything is charged
      return payment.clientSecret
        ? { kind: "authenticate", clientSecret: payment.clientSecret }
        : { kind: "pending" };
    case "INITIATED":
    case "REDIRECTED":
      return { kind: "pending" };
    case "REFUNDED":
    case "REFUND_REQUIRED":
      return { kind: "refunded", message: refundMessage(payment.failureCode) };
    default:
      return { kind: "declined", message: declineMessage(payment) };
  }
}

/** The provider's own reason when it gave one, instead of one line for every way a card can fail. */
export function declineMessage(payment: Pick<Payment, "failureCode" | "failureMessage">): string {
  if (payment.failureMessage) return payment.failureMessage;
  switch (payment.failureCode) {
    case "insufficient_funds":
      return "Your card has insufficient funds.";
    case "expired_card":
      return "Your card has expired.";
    case "incorrect_cvc":
      return "The card's security code is wrong.";
    case "card_declined":
      return "Your card was declined.";
    default:
      return "That payment did not go through. Try another card.";
  }
}

function refundMessage(reason: string | null): string {
  switch (reason) {
    case "seats_lost":
      return "Your seats were taken before the payment went through, so it is being refunded in full. Pick seats again to try once more.";
    case "duplicate_charge":
      return "This order had already been paid, so this second payment is being refunded in full.";
    case "order_closed":
      return "The order closed before the payment went through, so it is being refunded in full.";
    default:
      return "That payment could not buy the order, so it is being refunded in full.";
  }
}
