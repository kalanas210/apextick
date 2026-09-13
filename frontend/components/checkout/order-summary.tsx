"use client";

import { formatPrice } from "@/lib/format";
import { mmss } from "@/components/seatmap/parts";
import { Clock } from "@/components/ui/icons";
import { cn } from "@/lib/cn";
import type { Order } from "@/lib/types";

/** Line items, totals, and the payment-window countdown for an order. */
export function OrderSummary({
  order,
  secondsLeft,
}: {
  order: Order;
  secondsLeft?: number | null;
}) {
  const money = (value: number) =>
    formatPrice(value, order.currency, { keepMinorUnits: true });

  return (
    <div className="rounded-2xl border border-line bg-ink-2 p-6">
      <div className="flex items-center justify-between">
        <h2 className="font-display text-lg font-semibold tracking-tight">
          Order {order.orderNumber}
        </h2>
        {typeof secondsLeft === "number" && (
          <span
            role="timer"
            aria-label={`${mmss(secondsLeft)} left to pay`}
            className={cn(
              "inline-flex items-center gap-1.5 rounded-full border px-2.5 py-1 font-mono text-[0.66rem] tabular-nums tracking-[0.08em]",
              secondsLeft <= 60 ? "border-accent/50 text-accent" : "border-line-2 text-muted",
            )}
          >
            <Clock className="h-3.5 w-3.5" />
            {mmss(secondsLeft)}
          </span>
        )}
      </div>

      <p className="mt-1 text-[0.82rem] text-muted">{order.eventName}</p>

      <ul className="mt-5 space-y-1.5">
        {order.items.map((item) => (
          <li
            key={item.id}
            className="flex items-center justify-between gap-3 rounded-lg border border-line bg-ink px-3 py-2.5"
          >
            <span className="text-[0.84rem] text-bone">
              {item.sectionName}
              <span className="tnum text-muted"> · {item.label}</span>
              <span className="ml-2 text-[0.72rem] text-faint">{item.tierName}</span>
            </span>
            <span className="tnum text-[0.84rem] text-bone">
              {money(item.unitPrice)}
            </span>
          </li>
        ))}
      </ul>

      <dl className="mt-5 space-y-2 border-t border-line pt-5 text-[0.86rem]">
        <div className="flex justify-between text-muted">
          <dt>
            Subtotal<span className="tnum"> ({order.items.length})</span>
          </dt>
          <dd className="tnum text-bone">{money(order.subtotal)}</dd>
        </div>
        <div className="flex justify-between text-muted">
          <dt>Booking fee</dt>
          <dd className="tnum text-bone">{money(order.fee)}</dd>
        </div>
        <div className="mt-1 flex items-baseline justify-between border-t border-line pt-3">
          <dt className="font-display text-base text-bone">Total</dt>
          <dd className="tnum text-xl text-bone">{money(order.total)}</dd>
        </div>
      </dl>
    </div>
  );
}
