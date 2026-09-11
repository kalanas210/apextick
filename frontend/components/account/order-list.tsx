"use client";

import Link from "next/link";
import { formatPrice } from "@/lib/format";
import { apiErrorMessage } from "@/lib/api";
import { useMyOrders } from "@/hooks/useBooking";
import { useSession } from "@/hooks/useSession";
import { RequireAuth } from "@/components/auth/require-auth";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/cn";
import type { Order, OrderStatus } from "@/lib/types";

const TONE: Record<OrderStatus, string> = {
  PAID: "border-accent/50 text-accent",
  PENDING_PAYMENT: "border-line-2 text-bone",
  CANCELLED: "border-line-2 text-faint",
  EXPIRED: "border-line-2 text-faint",
};

const LABEL: Record<OrderStatus, string> = {
  PAID: "Confirmed",
  PENDING_PAYMENT: "Awaiting payment",
  CANCELLED: "Cancelled",
  EXPIRED: "Expired",
};

export function AccountOrders() {
  return (
    <RequireAuth
      title="Sign in to see your tickets"
      description="Your orders and tickets are tied to your ApexTick account."
    >
      <Orders />
    </RequireAuth>
  );
}

function Orders() {
  const { profile, signOut } = useSession();
  const { data: orders, isLoading, error } = useMyOrders();

  const name =
    (typeof profile?.name === "string" && profile.name) ||
    (typeof profile?.preferred_username === "string" && profile.preferred_username) ||
    "your account";

  return (
    <>
      <header className="flex flex-wrap items-end justify-between gap-4 border-b border-line pb-6">
        <div>
          <span className="kicker">Account</span>
          <h1 className="display mt-3 text-[clamp(1.9rem,4vw,3rem)]">{name}</h1>
          {typeof profile?.email === "string" && (
            <p className="mt-2 text-[0.86rem] text-muted">{profile.email}</p>
          )}
        </div>
        <button
          type="button"
          onClick={signOut}
          className="rounded-full border border-line-2 px-5 py-2.5 text-[0.82rem] text-muted transition-colors hover:border-bone hover:text-bone"
        >
          Sign out
        </button>
      </header>

      <section className="mt-10">
        <h2 className="kicker mb-4">Orders</h2>

        {isLoading ? (
          <div className="grid place-items-center py-16" aria-busy="true">
            <span className="h-6 w-6 animate-spin rounded-full border-2 border-line-2 border-t-accent" />
            <span className="sr-only">Loading your orders…</span>
          </div>
        ) : error ? (
          <p className="rounded-2xl border border-line bg-ink-2 p-6 text-[0.86rem] text-muted">
            {apiErrorMessage(error, "Could not load your orders.")}
          </p>
        ) : !orders?.length ? (
          <div className="rounded-2xl border border-line bg-ink-2 p-10 text-center">
            <p className="text-[0.9rem] text-muted">No orders yet.</p>
            <p className="mt-2 text-[0.78rem] text-faint">
              Pick a fixture and your tickets will land here.
            </p>
            <div className="mt-6 flex justify-center">
              <Button href="/events" size="md" arrow>
                Browse fixtures
              </Button>
            </div>
          </div>
        ) : (
          <ul className="space-y-3">
            {orders.map((order: Order) => (
              <li key={order.id}>
                <Link
                  href={`/orders/${order.id}`}
                  className="group flex flex-wrap items-center justify-between gap-4 rounded-2xl border border-line bg-ink-2 p-5 transition-colors hover:border-line-strong"
                >
                  <div className="min-w-0">
                    <p className="font-display text-base font-semibold tracking-tight text-bone">
                      {order.eventName}
                    </p>
                    <p className="mt-1 text-[0.78rem] text-muted">
                      <span className="tnum">{order.orderNumber}</span>
                      <span className="text-faint">
                        {" "}
                        · {order.items.length} seat{order.items.length === 1 ? "" : "s"}
                      </span>
                    </p>
                  </div>
                  <div className="flex items-center gap-4">
                    <span className="tnum text-[0.92rem] text-bone">
                      {formatPrice(order.total, order.currency, { keepMinorUnits: true })}
                    </span>
                    <span
                      className={cn(
                        "rounded-full border px-3 py-1 font-mono text-[0.6rem] uppercase tracking-[0.14em]",
                        TONE[order.status],
                      )}
                    >
                      {LABEL[order.status]}
                    </span>
                  </div>
                </Link>
              </li>
            ))}
          </ul>
        )}
      </section>
    </>
  );
}
