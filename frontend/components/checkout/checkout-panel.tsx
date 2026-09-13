"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { Elements } from "@stripe/react-stripe-js";
import { loadStripe, type Stripe } from "@stripe/stripe-js";
import { formatPrice } from "@/lib/format";
import { apiErrorMessage, apiStatus } from "@/lib/api";
import { cardFormFor, paymentFromError, paymentOutcome, type PaymentOutcome } from "@/lib/checkout";
import { useCancelOrder, useOrder, usePayOrder, usePaymentConfig } from "@/hooks/useBooking";
import { RequireAuth } from "@/components/auth/require-auth";
import { OrderSummary } from "./order-summary";
import { MockCardForm, type MockCard } from "./mock-card-form";
import { StripeCardForm } from "./stripe-card-form";
import { Button } from "@/components/ui/button";
import { ConfirmDialog } from "@/components/ui/confirm-dialog";
import type { Payment } from "@/lib/types";

/** Cache the Stripe.js loader per key so remounts don't refetch the script. */
const stripeLoaders = new Map<string, Promise<Stripe | null>>();
function stripeFor(publishableKey: string) {
  let loader = stripeLoaders.get(publishableKey);
  if (!loader) {
    loader = loadStripe(publishableKey);
    stripeLoaders.set(publishableKey, loader);
  }
  return loader;
}

export function CheckoutPanel({ orderId }: { orderId: string }) {
  return (
    <RequireAuth
      title="Sign in to finish checkout"
      description="Your seats are held while you sign in — you will land right back on this order."
      expiredDescription="Sign in again to finish paying. Your seats stay held until the order's timer runs out."
    >
      <Checkout orderId={orderId} />
    </RequireAuth>
  );
}

function Checkout({ orderId }: { orderId: string }) {
  const router = useRouter();
  const { data: order, isLoading, error, refetch } = useOrder(orderId);
  const config = usePaymentConfig();
  const pay = usePayOrder(orderId);
  const cancel = useCancelOrder(orderId);

  const [failure, setFailure] = useState<string | null>(null);
  const [confirmingCancel, setConfirmingCancel] = useState(false);
  const [now, setNow] = useState(() => Date.now());

  // Payment-window countdown; the backend expires the order at the same moment.
  useEffect(() => {
    const id = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(id);
  }, []);

  // A paid order has tickets — send the buyer to them.
  useEffect(() => {
    if (order?.status === "PAID") {
      router.replace(`/orders/${order.id}`);
    }
  }, [order?.status, order?.id, router]);

  // Recomputed every tick anyway, so there is nothing worth memoizing here.
  const secondsLeft =
    order?.expiresAt && order.status === "PENDING_PAYMENT"
      ? Math.max(0, Math.floor((new Date(order.expiresAt).getTime() - now) / 1000))
      : null;

  if (isLoading) {
    return (
      <div className="grid place-items-center py-24" aria-busy="true">
        <span className="h-7 w-7 animate-spin rounded-full border-2 border-line-2 border-t-accent" />
        <span className="sr-only">Loading your order…</span>
      </div>
    );
  }

  if (error || !order) {
    // A lapsed session is not this branch's to explain: it swaps the page for the sign-in prompt.
    const missing = !error || apiStatus(error) === 404;
    return (
      <div className="rounded-2xl border border-line bg-ink-2 p-8 text-center">
        <h2 className="font-display text-xl tracking-tight">
          {missing ? "Order not found" : "Could not load this order"}
        </h2>
        <p className="mx-auto mt-2 max-w-sm text-[0.86rem] text-muted">
          {missing
            ? "That order does not exist, or it is not yours."
            : apiErrorMessage(error, "The booking service did not answer. Try again in a moment.")}
        </p>
        <div className="mt-6 flex justify-center gap-3">
          {!missing && (
            <Button onClick={() => void refetch()} size="md" variant="outline">
              Try again
            </Button>
          )}
          <Button href="/events" size="md">
            Browse fixtures
          </Button>
        </div>
      </div>
    );
  }

  const totalLabel = formatPrice(order.total, order.currency, { keepMinorUnits: true });
  const form = cardFormFor(config.data, config.isError);
  const stripeKey = config.data?.provider === "stripe" ? config.data.stripePublishableKey : null;

  /**
   * Sends the attempt to the API and says what came of it. A decline answers with an error status
   * and the payment as its body, and the payment carries the real reason, so that is what is shown.
   */
  const submitPayment = async (input: {
    card?: MockCard;
    paymentMethodId?: string;
  }): Promise<PaymentOutcome | undefined> => {
    setFailure(null);
    let payment: Payment | undefined;
    try {
      payment = await pay.mutateAsync(input);
    } catch (err) {
      payment = paymentFromError(err);
      if (!payment) {
        if (apiStatus(err) !== 401) {
          setFailure(
            apiErrorMessage(err, "We could not reach the payment service. Try again: one attempt is never charged twice."),
          );
        }
        return undefined;
      }
    }
    const outcome = paymentOutcome(payment);
    if (outcome.kind === "paid" || outcome.kind === "pending") {
      // a pending charge is settled by Stripe's webhook, which the order page waits for
      router.push(`/orders/${order.id}`);
    } else if (outcome.kind === "declined" || outcome.kind === "refunded") {
      setFailure(outcome.message);
    }
    return outcome;
  };

  /**
   * Giving the order up is the only thing that puts its seats back on sale — the
   * seat map refuses to release seats an unpaid order still covers, and says so.
   */
  const cancelOrder = async () => {
    setFailure(null);
    try {
      await cancel.mutateAsync();
      setConfirmingCancel(false);
    } catch (err) {
      setConfirmingCancel(false);
      setFailure(apiErrorMessage(err, "We could not cancel that order."));
    }
  };

  const expired = order.status === "EXPIRED" || order.status === "CANCELLED";

  return (
    <div className="grid gap-10 lg:grid-cols-12 lg:gap-12">
      <div className="lg:col-span-7">
        <div className="rounded-2xl border border-line bg-ink-2 p-6">
          <h2 className="font-display text-lg font-semibold tracking-tight">Payment</h2>

          {/* Above both branches: a charge refunded because the seats went has also closed the order. */}
          {failure && (
            <p
              role="alert"
              className="mt-4 rounded-lg border border-[#ff6b6b]/40 bg-[#ff6b6b]/10 px-3 py-2 text-[0.8rem] text-[#ff6b6b]"
            >
              {failure}
            </p>
          )}

          {expired ? (
            <div className="py-10 text-center">
              <p className="text-[0.9rem] text-muted">
                This order {order.status === "EXPIRED" ? "expired" : "was cancelled"} and its
                seats went back on sale.
              </p>
              <div className="mt-6 flex justify-center">
                <Button href={`/events/${order.eventSlug}/seats`} size="md">
                  Pick seats again
                </Button>
              </div>
            </div>
          ) : (
            <>
              <p className="mt-1 text-[0.82rem] text-muted">
                {form === "stripe" ? (
                  <>
                    Paying with <span className="text-bone">Stripe</span>.
                  </>
                ) : form === "mock" ? (
                  <>
                    Paying with <span className="text-bone">the demo gateway</span>.
                  </>
                ) : form === "loading" ? (
                  "Loading the payment options…"
                ) : (
                  "Card payments are unavailable right now."
                )}
              </p>

              <div className="mt-6">
                {form === "stripe" && stripeKey ? (
                  <Elements stripe={stripeFor(stripeKey)}>
                    <StripeCardForm
                      total={totalLabel}
                      submitting={pay.isPending}
                      onPaymentMethod={(paymentMethodId) => submitPayment({ paymentMethodId })}
                      onAuthenticated={() => router.push(`/orders/${order.id}`)}
                    />
                  </Elements>
                ) : form === "mock" ? (
                  <MockCardForm
                    total={totalLabel}
                    submitting={pay.isPending}
                    onSubmit={(card) => void submitPayment({ card })}
                  />
                ) : form === "loading" ? (
                  <div className="grid place-items-center py-10" aria-busy="true">
                    <span className="h-6 w-6 animate-spin rounded-full border-2 border-line-2 border-t-accent" />
                    <span className="sr-only">Loading the payment options…</span>
                  </div>
                ) : (
                  <div role="alert" className="rounded-lg border border-line-2 px-4 py-6 text-center">
                    <p className="text-[0.84rem] text-muted">
                      The payment options could not be loaded, so no card form is shown.
                    </p>
                    <div className="mt-4 flex justify-center">
                      <Button onClick={() => void config.refetch()} size="sm" variant="outline">
                        Try again
                      </Button>
                    </div>
                  </div>
                )}
              </div>

              <p className="mt-4 text-center text-[0.72rem] text-faint">
                Your seats stay held until the timer runs out.
              </p>

              <div className="mt-5 border-t border-line pt-4 text-center">
                <button
                  type="button"
                  onClick={() => setConfirmingCancel(true)}
                  disabled={cancel.isPending}
                  className="text-[0.72rem] text-faint underline-offset-4 transition-colors hover:text-bone hover:underline disabled:opacity-60"
                >
                  {cancel.isPending ? "Cancelling…" : "Cancel this order and free the seats"}
                </button>
              </div>
            </>
          )}
        </div>
      </div>

      <ConfirmDialog
        open={confirmingCancel}
        title="Cancel this order?"
        description={
          <>
            The {order.items.length} seat{order.items.length === 1 ? "" : "s"} on it go
            straight back on sale, and you will need to pick again. Nothing has been
            charged.
          </>
        }
        confirmLabel="Cancel order"
        cancelLabel="Keep it"
        tone="danger"
        busy={cancel.isPending}
        onConfirm={cancelOrder}
        onCancel={() => setConfirmingCancel(false)}
      />

      <aside className="lg:col-span-5">
        <div className="lg:sticky lg:top-28">
          <OrderSummary order={order} secondsLeft={secondsLeft} />
        </div>
      </aside>
    </div>
  );
}
