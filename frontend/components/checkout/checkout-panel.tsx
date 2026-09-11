"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { Elements } from "@stripe/react-stripe-js";
import { loadStripe, type Stripe } from "@stripe/stripe-js";
import { formatPrice } from "@/lib/format";
import { apiErrorMessage } from "@/lib/api";
import { useOrder, usePayOrder, usePaymentConfig } from "@/hooks/useBooking";
import { RequireAuth } from "@/components/auth/require-auth";
import { OrderSummary } from "./order-summary";
import { MockCardForm, type MockCard } from "./mock-card-form";
import { StripeCardForm } from "./stripe-card-form";
import { Button } from "@/components/ui/button";

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
    >
      <Checkout orderId={orderId} />
    </RequireAuth>
  );
}

function Checkout({ orderId }: { orderId: string }) {
  const router = useRouter();
  const { data: order, isLoading, error } = useOrder(orderId);
  const { data: config } = usePaymentConfig();
  const pay = usePayOrder(orderId);

  const [failure, setFailure] = useState<string | null>(null);
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
    return (
      <div className="rounded-2xl border border-line bg-ink-2 p-8 text-center">
        <h2 className="font-display text-xl tracking-tight">Order not found</h2>
        <p className="mx-auto mt-2 max-w-sm text-[0.86rem] text-muted">
          {apiErrorMessage(error, "That order does not exist, or it is not yours.")}
        </p>
        <div className="mt-6 flex justify-center">
          <Button href="/events" size="md">
            Browse fixtures
          </Button>
        </div>
      </div>
    );
  }

  const totalLabel = formatPrice(order.total, order.currency, { keepMinorUnits: true });

  /** Sends the attempt to the API and interprets the payment status it returns. */
  const submitPayment = async (input: { card?: MockCard; paymentMethodId?: string }) => {
    setFailure(null);
    try {
      const payment = await pay.mutateAsync(input);
      if (payment.status === "SUCCEEDED") {
        router.push(`/orders/${order.id}`);
        return;
      }
      if (payment.status === "REQUIRES_ACTION") {
        // 3-D Secure: the webhook is authoritative, so land on the order and let
        // it settle there rather than pretending we know the outcome here.
        router.push(`/orders/${order.id}`);
        return;
      }
      setFailure(payment.failureMessage ?? "That payment did not go through.");
    } catch (err) {
      setFailure(apiErrorMessage(err, "We could not take that payment."));
    }
  };

  const expired = order.status === "EXPIRED" || order.status === "CANCELLED";

  return (
    <div className="grid gap-10 lg:grid-cols-12 lg:gap-12">
      <div className="lg:col-span-7">
        <div className="rounded-2xl border border-line bg-ink-2 p-6">
          <h2 className="font-display text-lg font-semibold tracking-tight">Payment</h2>

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
                Paying with{" "}
                <span className="text-bone">
                  {config?.provider === "stripe" ? "Stripe" : "the demo gateway"}
                </span>
                .
              </p>

              {failure && (
                <p
                  role="alert"
                  className="mt-4 rounded-lg border border-[#ff6b6b]/40 bg-[#ff6b6b]/10 px-3 py-2 text-[0.8rem] text-[#ff6b6b]"
                >
                  {failure}
                </p>
              )}

              <div className="mt-6">
                {config?.provider === "stripe" && config.stripePublishableKey ? (
                  <Elements stripe={stripeFor(config.stripePublishableKey)}>
                    <StripeCardForm
                      total={totalLabel}
                      submitting={pay.isPending}
                      onPaymentMethod={(paymentMethodId) => submitPayment({ paymentMethodId })}
                    />
                  </Elements>
                ) : (
                  <MockCardForm
                    total={totalLabel}
                    submitting={pay.isPending}
                    onSubmit={(card) => submitPayment({ card })}
                  />
                )}
              </div>

              <p className="mt-4 text-center text-[0.72rem] text-faint">
                Your seats stay held until the timer runs out.
              </p>
            </>
          )}
        </div>
      </div>

      <aside className="lg:col-span-5">
        <div className="lg:sticky lg:top-28">
          <OrderSummary order={order} secondsLeft={secondsLeft} />
        </div>
      </aside>
    </div>
  );
}
