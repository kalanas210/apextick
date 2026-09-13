"use client";

import { useRouter, useSearchParams } from "next/navigation";
import { useCallback, useEffect, useState } from "react";
import { cn } from "@/lib/cn";
import { formatInstant, formatPrice } from "@/lib/format";
import { ORDER_LABEL, ORDER_STATUSES, ORDER_TONE, pillClass } from "@/lib/status";
import { useAdminOrders } from "@/hooks/useAdmin";
import { AdminHeader } from "./admin-shell";
import { DataTable } from "@/components/ui/data-table";
import { Pagination } from "@/components/ui/pagination";
import { Input, Select } from "@/components/ui/field";
import { Search } from "@/components/ui/icons";
import type { Order } from "@/lib/types";

const SIZE = 20;

export function OrderTable() {
  const router = useRouter();
  const searchParams = useSearchParams();

  // Filters live in the URL, so "the orders we still owe money on" is a link to send.
  const q = searchParams.get("q") ?? "";
  const status = searchParams.get("status") ?? "";
  const refundOwed = searchParams.get("refund") === "owed";
  const page = Number(searchParams.get("page") ?? 0);

  const [draftQ, setDraftQ] = useState(q);

  const setParams = useCallback(
    (next: Record<string, string | number | undefined>) => {
      const params = new URLSearchParams(searchParams.toString());
      for (const [key, value] of Object.entries(next)) {
        if (value === undefined || value === "" || value === 0) params.delete(key);
        else params.set(key, String(value));
      }
      router.replace(params.size ? `/admin/orders?${params}` : "/admin/orders", { scroll: false });
    },
    [router, searchParams],
  );

  // Debounced: a customer reading out an order number should not fire a request per character.
  useEffect(() => {
    if (draftQ === q) return;
    const timer = setTimeout(() => setParams({ q: draftQ.trim(), page: 0 }), 300);
    return () => clearTimeout(timer);
  }, [draftQ, q, setParams]);

  const { data, isLoading, error } = useAdminOrders({
    q: q || undefined,
    status: status || undefined,
    refundRequired: refundOwed || undefined,
    page,
    size: SIZE,
  });

  const filtered = Boolean(q || status || refundOwed);

  return (
    <>
      <AdminHeader
        kicker="Admin"
        title="Orders"
        description="Find an order by its number or the customer's email, then open it to refund it."
      />

      <div className="mt-6 flex flex-wrap items-center gap-3">
        <div className="relative min-w-56 flex-1">
          <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-faint" />
          <Input
            type="search"
            value={draftQ}
            onChange={(e) => setDraftQ(e.target.value)}
            placeholder="Order number, email or name"
            aria-label="Search orders"
            className="pl-9"
          />
        </div>
        <Select
          value={status}
          onChange={(e) => setParams({ status: e.target.value, page: 0 })}
          aria-label="Filter by order status"
          placeholder="Any status"
          options={ORDER_STATUSES.map((s) => ({ value: s, label: ORDER_LABEL[s] }))}
          className="w-48"
        />
        <button
          type="button"
          aria-pressed={refundOwed}
          onClick={() => setParams({ refund: refundOwed ? undefined : "owed", page: 0 })}
          className={cn(
            "rounded-full border px-4 py-2 text-[0.8rem] transition-colors",
            refundOwed
              ? "border-[#ff6b6b]/50 text-[#ff6b6b]"
              : "border-line-2 text-muted hover:border-bone hover:text-bone",
          )}
        >
          Refund owed
        </button>
        {filtered && (
          <button
            type="button"
            onClick={() => {
              setDraftQ("");
              setParams({ q: undefined, status: undefined, refund: undefined, page: 0 });
            }}
            className="text-[0.8rem] text-muted transition-colors hover:text-bone"
          >
            Reset
          </button>
        )}
      </div>

      <div className="mt-6">
        <DataTable<Order>
          caption="orders"
          rows={data?.content}
          rowKey={(o) => o.id}
          isLoading={isLoading}
          error={error}
          rowHref={(o) => `/admin/orders/${o.id}`}
          emptyTitle={
            refundOwed && !q && !status
              ? "No refunds owed"
              : filtered
                ? "Nothing matches those filters"
                : "No orders yet"
          }
          emptyHint={
            refundOwed && !q && !status
              ? "Every refund asked for has gone through."
              : filtered
                ? "Check the order number, or search by the customer's email instead."
                : "Orders appear the moment someone holds a seat and checks out."
          }
          footer={
            <Pagination
              page={data?.page ?? 0}
              totalPages={data?.totalPages ?? 0}
              totalElements={data?.totalElements ?? 0}
              onPage={(p) => setParams({ page: p })}
            />
          }
          columns={[
            {
              key: "order",
              header: "Order",
              cell: (o) => (
                <span className="block">
                  <span className="tnum block font-medium text-bone">{o.orderNumber}</span>
                  <span className="mt-0.5 block text-[0.72rem] text-faint">
                    {formatInstant(o.createdAt)}
                  </span>
                </span>
              ),
            },
            {
              key: "customer",
              header: "Customer",
              cell: (o) => (
                <span className="block min-w-0">
                  <span className="block truncate">{o.userName ?? "—"}</span>
                  <span className="mt-0.5 block truncate text-[0.72rem] text-faint">{o.userEmail ?? ""}</span>
                </span>
              ),
            },
            {
              key: "event",
              header: "Event",
              cell: (o) => (
                <span className="block">
                  <span className="block">{o.eventName}</span>
                  <span className="mt-0.5 block text-[0.72rem] text-faint">
                    {o.items.map((i) => i.label).join(", ") || "—"}
                  </span>
                </span>
              ),
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
                <span className="block">
                  <span className={cn(pillClass, ORDER_TONE[o.status])}>{ORDER_LABEL[o.status]}</span>
                  {o.status === "PENDING_PAYMENT" && o.expiresAt && (
                    <span className="mt-1 block text-[0.72rem] text-faint">
                      until {formatInstant(o.expiresAt)}
                    </span>
                  )}
                </span>
              ),
            },
          ]}
        />
      </div>
    </>
  );
}
