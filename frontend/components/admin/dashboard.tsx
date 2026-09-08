"use client";

import Link from "next/link";
import { useMemo } from "react";
import type { ReactNode } from "react";
import { cn } from "@/lib/cn";
import { formatInstant, formatPrice } from "@/lib/format";
import { ORDER_LABEL, ORDER_TONE, pillClass } from "@/lib/status";
import { useAdminEvents, useAdminOrders } from "@/hooks/useAdmin";
import { AdminHeader } from "./admin-shell";
import { Button } from "@/components/ui/button";
import { DataTable } from "@/components/ui/data-table";
import type { Order } from "@/lib/types";

const RECENT = 5;

export function AdminDashboard() {
  // One page big enough to count statuses across the whole (demo-scale) catalog.
  const events = useAdminEvents({ size: 100 });
  const orders = useAdminOrders({ size: RECENT });
  const paid = useAdminOrders({ status: "PAID", size: 100 });

  const counts = useMemo(() => {
    const rows = events.data?.content ?? [];
    return {
      total: events.data?.totalElements ?? 0,
      live: rows.filter((e) => e.status === "onsale" || e.status === "selling-fast").length,
      draft: rows.filter((e) => e.status === "draft").length,
    };
  }, [events.data]);

  const revenue = useMemo(() => {
    const rows = paid.data?.content ?? [];
    // Mixed-currency totals would be meaningless, so this reports the currency
    // that actually dominates the paid orders rather than inventing a base one.
    const byCurrency = new Map<string, number>();
    for (const o of rows) {
      byCurrency.set(o.currency, (byCurrency.get(o.currency) ?? 0) + o.total);
    }
    const top = [...byCurrency.entries()].sort((a, b) => b[1] - a[1])[0];
    return top ? { amount: top[1], currency: top[0] as Order["currency"] } : null;
  }, [paid.data]);

  return (
    <>
      <AdminHeader
        kicker="Admin"
        title="Overview"
        description="Everything happening across the catalog right now."
        action={
          <Button href="/admin/events/new" size="sm" arrow>
            New event
          </Button>
        }
      />

      <section className="mt-8 grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <Stat label="Events" value={counts.total} hint={`${counts.draft} in draft`} loading={events.isLoading} />
        <Stat label="On sale" value={counts.live} hint="Visible in the catalog" loading={events.isLoading} />
        <Stat
          label="Paid orders"
          value={paid.data?.totalElements ?? 0}
          hint="All time"
          loading={paid.isLoading}
        />
        <Stat
          label="Revenue"
          value={revenue ? formatPrice(revenue.amount, revenue.currency) : "—"}
          hint={revenue ? `Confirmed, in ${revenue.currency}` : "No paid orders yet"}
          loading={paid.isLoading}
        />
      </section>

      <section className="mt-10">
        <div className="mb-4 flex items-end justify-between gap-4">
          <h2 className="kicker">Latest orders</h2>
          <Link href="/admin/orders" className="ulink text-[0.8rem] text-muted">
            All orders
          </Link>
        </div>

        <DataTable<Order>
          caption="the most recent orders"
          rows={orders.data?.content}
          rowKey={(o) => o.id}
          isLoading={orders.isLoading}
          error={orders.error}
          emptyTitle="No orders yet"
          emptyHint="Orders appear here the moment someone holds a seat."
          columns={[
            {
              key: "order",
              header: "Order",
              cell: (o) => <span className="tnum">{o.orderNumber}</span>,
            },
            { key: "event", header: "Event", cell: (o) => o.eventName },
            {
              key: "seats",
              header: "Seats",
              align: "right",
              cell: (o) => o.items.length,
            },
            {
              key: "total",
              header: "Total",
              align: "right",
              cell: (o) => formatPrice(o.total, o.currency),
            },
            {
              key: "status",
              header: "Status",
              cell: (o) => (
                <span className={cn(pillClass, ORDER_TONE[o.status])}>{ORDER_LABEL[o.status]}</span>
              ),
            },
            {
              key: "created",
              header: "Placed",
              cell: (o) => <span className="text-muted">{formatInstant(o.createdAt)}</span>,
            },
          ]}
        />
      </section>
    </>
  );
}

function Stat({
  label,
  value,
  hint,
  loading,
}: {
  label: string;
  value: ReactNode;
  hint: string;
  loading?: boolean;
}) {
  return (
    <div className="rounded-2xl border border-line bg-ink-2 p-6">
      <p className="kicker">{label}</p>
      <p className="tnum mt-3 font-display text-[1.8rem] leading-none tracking-tight text-bone">
        {loading ? <span className="text-faint">…</span> : value}
      </p>
      <p className="mt-2 text-[0.75rem] text-faint">{hint}</p>
    </div>
  );
}
