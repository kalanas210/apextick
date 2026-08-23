"use client";

import { useState, type FormEvent } from "react";
import { CardElement, useElements, useStripe } from "@stripe/react-stripe-js";

/** Stripe Elements styled for the ApexTick dark palette. */
const CARD_STYLE = {
  style: {
    base: {
      color: "#f4f2ec",
      fontFamily: "var(--font-body), system-ui, sans-serif",
      fontSize: "15px",
      "::placeholder": { color: "#8a857b" },
      iconColor: "#c9f23f",
    },
    invalid: { color: "#ff6b6b", iconColor: "#ff6b6b" },
  },
} as const;

/**
 * Collects card details in a Stripe-hosted iframe and exchanges them for a
 * PaymentMethod id. The card number never touches our servers or this bundle —
 * the backend only ever sees `pm_…`, which it confirms server-side.
 */
export function StripeCardForm({
  onPaymentMethod,
  submitting,
  total,
}: {
  onPaymentMethod: (paymentMethodId: string) => void;
  submitting?: boolean;
  total: string;
}) {
  const stripe = useStripe();
  const elements = useElements();
  const [error, setError] = useState<string | null>(null);
  const [tokenising, setTokenising] = useState(false);

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    if (!stripe || !elements) return;
    const card = elements.getElement(CardElement);
    if (!card) return;

    setError(null);
    setTokenising(true);
    const result = await stripe.createPaymentMethod({ type: "card", card });
    setTokenising(false);

    if (result.error) {
      setError(result.error.message ?? "That card could not be used.");
      return;
    }
    onPaymentMethod(result.paymentMethod.id);
  };

  const busy = tokenising || submitting || !stripe;

  return (
    <form onSubmit={submit} className="space-y-4">
      <p className="text-[0.72rem] text-faint">
        Test mode — use <span className="tnum">4242 4242 4242 4242</span> with any
        future expiry and CVC.
      </p>

      <div className="rounded-lg border border-line-2 bg-ink px-3.5 py-3.5 transition-colors focus-within:border-bone">
        <CardElement options={CARD_STYLE} onChange={() => setError(null)} />
      </div>

      {error && (
        <p role="alert" className="text-[0.78rem] text-[#ff6b6b]">
          {error}
        </p>
      )}

      <button
        type="submit"
        disabled={busy}
        className="inline-flex h-12 w-full items-center justify-center gap-2 rounded-full bg-accent text-[0.92rem] font-medium text-accent-ink transition-[filter] hover:brightness-105 disabled:opacity-60"
      >
        {busy ? (
          <>
            <span className="h-4 w-4 animate-spin rounded-full border-2 border-accent-ink/30 border-t-accent-ink" />
            {tokenising ? "Checking your card…" : "Taking payment…"}
          </>
        ) : (
          <>Pay {total}</>
        )}
      </button>
    </form>
  );
}
