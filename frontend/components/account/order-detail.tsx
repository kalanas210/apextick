"use client";

import { useEffect } from "react";
import { useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import { formatPrice } from "@/lib/format";
import { apiErrorMessage } from "@/lib/api";
import { useOrder, useOrderTickets } from "@/hooks/useBooking";
import { RequireAuth } from "@/components/auth/require-auth";
import { TicketCard } from "./ticket-card";
import { Button } from "@/components/ui/button";
import { Check } from "@/components/ui/icons";
import { cn } from "@/lib/cn";
import type { OrderStatus } from "@/lib/types";

const STATUS_COPY: Record<OrderStatus, { label: string; tone: string; blurb: string }> = {
  PAID: {
    label: "Confirmed",
    tone: "border-accent/50 text-accent",
    blurb: "Your seats are booked. Tickets are below — bring the QR to the gate.",
  },
  PENDING_PAYMENT: {
    label: "Awaiting payment",
    tone: "border-line-2 text-muted",
    blurb: "These seats are held for you until the payment window closes.",
  },
  CANCELLED: {
    label: "Cancelled",
    tone: "border-line-2 text-faint",
    blurb: "This order was cancelled and the seats went back on sale.",
  },
  EXPIRED: {
    label: "Expired",
    tone: "border-line-2 text-faint",
    blurb: "The payment window closed, so the seats were released.",
  },
};

export function OrderDetail({ orderId }: { orderId: string }) {
  return (
    <RequireAuth
      title="Sign in to see this order"
      description="Orders and tickets live in your ApexTick account."
    >
      <Order orderId={orderId} />
    </RequireAuth>
  );
}

function Order({ orderId }: { orderId: string }) {
  const queryClient = useQueryClient();
  const { data: order, isLoading, error } = useOrder(orderId);
  const paid = order?.status === "PAID";
  const { data: tickets } = useOrderTickets(orderId, paid);

  // A card confirmed via 3-D Secure is finalised by Stripe's webhook, which can
  // land a beat after the redirect — poll briefly so the page settles itself.
  useEffect(() => {
    if (!order || order.status !== "PENDING_PAYMENT") return;
    const id = setInterval(() => {
      queryClient.invalidateQueries({ queryKey: ["order", orderId] });
    }, 4000);
    const stop = setTimeout(() => clearInterval(id), 40_000);
    return () => {
      clearInterval(id);
      clearTimeout(stop);
    };
  }, [order, orderId, queryClient]);

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
          <Button href="/account" size="md">
            My tickets
          </Button>
        </div>
      </div>
    );
  }

  const status = STATUS_COPY[order.status];

  return (
    <div className="grid gap-10 lg:grid-cols-12 lg:gap-12">
      <div className="lg:col-span-7">
        <div className="flex flex-wrap items-center gap-3">
          <span
            className={cn(
              "inline-flex items-center gap-1.5 rounded-full border px-3 py-1 font-mono text-[0.62rem] uppercase tracking-[0.14em]",
              status.tone,
            )}
          >
            {paid && <Check className="h-3.5 w-3.5" />}
            {status.label}
          </span>
          <span className="kicker text-bone/55">{order.orderNumber}</span>
        </div>

        <h1 className="display mt-5 text-[clamp(1.8rem,4vw,3rem)]">{order.eventName}</h1>
        <p className="mt-3 max-w-md text-[0.9rem] text-muted">{status.blurb}</p>

        {order.status === "PENDING_PAYMENT" && (
          <div className="mt-6">
            <Button href={`/checkout/${order.id}`} size="md" arrow>
              Finish payment
            </Button>
          </div>
        )}

        {(order.status === "CANCELLED" || order.status === "EXPIRED") && (
          <div className="mt-6">
            <Button href={`/events/${order.eventSlug}/seats`} size="md" arrow>
              Pick seats again
            </Button>
          </div>
        )}

        {paid && (
          <section className="mt-10">
            <h2 className="kicker mb-4">
              Tickets<span className="tnum"> ({order.items.length})</span>
            </h2>
            <div className="space-y-3">
              {tickets?.length
                ? tickets.map((ticket) => <TicketCard key={ticket.id} ticket={ticket} />)
                : (
                  <div className="rounded-2xl border border-line bg-ink-2 p-6 text-center">
                    <span className="mx-auto block h-5 w-5 animate-spin rounded-full border-2 border-line-2 border-t-accent" />
                    <p className="mt-3 text-[0.82rem] text-muted">Issuing your tickets…</p>
                  </div>
                )}
            </div>
          </section>
        )}
      </div>

      <aside className="lg:col-span-5">
        <div className="lg:sticky lg:top-28 rounded-2xl border border-line bg-ink-2 p-6">
          <h2 className="font-display text-lg font-semibold tracking-tight">Summary</h2>
          <ul className="mt-5 space-y-1.5">
            {order.items.map((item) => (
              <li
                key={item.id}
                className="flex items-center justify-between gap-3 rounded-lg border border-line bg-ink px-3 py-2.5"
              >
                <span className="text-[0.84rem] text-bone">
                  {item.sectionName}
                  <span className="tnum text-muted"> · {item.label}</span>
                </span>
                <span className="tnum text-[0.84rem] text-bone">
                  {formatPrice(item.unitPrice, order.currency, { keepMinorUnits: true })}
                </span>
              </li>
            ))}
          </ul>
          <dl className="mt-5 space-y-2 border-t border-line pt-5 text-[0.86rem]">
            <div className="flex justify-between text-muted">
              <dt>Subtotal</dt>
              <dd className="tnum text-bone">
                {formatPrice(order.subtotal, order.currency, { keepMinorUnits: true })}
              </dd>
            </div>
            <div className="flex justify-between text-muted">
              <dt>Booking fee</dt>
              <dd className="tnum text-bone">
                {formatPrice(order.fee, order.currency, { keepMinorUnits: true })}
              </dd>
            </div>
            <div className="mt-1 flex items-baseline justify-between border-t border-line pt-3">
              <dt className="font-display text-base text-bone">Total</dt>
              <dd className="tnum text-xl text-bone">
                {formatPrice(order.total, order.currency, { keepMinorUnits: true })}
              </dd>
            </div>
          </dl>
          <Link
            href="/account"
            className="mt-6 block text-center text-[0.76rem] text-faint underline-offset-4 transition-colors hover:text-bone hover:underline"
          >
            All my orders
          </Link>
        </div>
      </aside>
    </div>
  );
}
