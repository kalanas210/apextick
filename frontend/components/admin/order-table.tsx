"use client";

import { useRouter, useSearchParams } from "next/navigation";
import { useCallback } from "react";
import { cn } from "@/lib/cn";
import { formatInstant, formatPrice } from "@/lib/format";
import { ORDER_LABEL, ORDER_STATUSES, ORDER_TONE, pillClass } from "@/lib/status";
import { useAdminOrders } from "@/hooks/useAdmin";
import { AdminHeader } from "./admin-shell";
import { DataTable } from "@/components/ui/data-table";
import { Pagination } from "@/components/ui/pagination";
import { Select } from "@/components/ui/field";
import type { Order } from "@/lib/types";

const SIZE = 20;

export function OrderTable() {
  const router = useRouter();
  const searchParams = useSearchParams();

  const status = searchParams.get("status") ?? "";
  const page = Number(searchParams.get("page") ?? 0);

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

  const { data, isLoading, error } = useAdminOrders({
    status: status || undefined,
    page,
    size: SIZE,
  });

  return (
    <>
      <AdminHeader
        kicker="Admin"
        title="Orders"
        description="Every order across the catalog, newest first."
      />

      <div className="mt-6 flex flex-wrap items-center gap-3">
        <Select
          value={status}
          onChange={(e) => setParams({ status: e.target.value, page: 0 })}
          aria-label="Filter by order status"
          placeholder="Any status"
          options={ORDER_STATUSES.map((s) => ({ value: s, label: ORDER_LABEL[s] }))}
          className="w-48"
        />
        {status && (
          <button
            type="button"
            onClick={() => setParams({ status: undefined, page: 0 })}
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
          emptyTitle={status ? "No orders in that state" : "No orders yet"}
          emptyHint={
            status
              ? "Try a different status."
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
              key: "event",
              header: "Event",
              cell: (o) => (
                <span className="block">
                  <span className="block">{o.eventName}</span>
                  <span className="mt-0.5 block text-[0.72rem] text-faint">
                    {formatInstant(o.startsAt)}
                  </span>
                </span>
              ),
            },
            {
              key: "seats",
              header: "Seats",
              cell: (o) => (
                <span className="tnum text-muted">
                  {o.items.map((i) => i.label).join(", ") || "—"}
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
                <span className={cn(pillClass, ORDER_TONE[o.status])}>{ORDER_LABEL[o.status]}</span>
              ),
            },
            {
              key: "expires",
              header: "Expires",
              cell: (o) =>
                o.status === "PENDING_PAYMENT" ? (
                  <span className="text-muted">{formatInstant(o.expiresAt)}</span>
                ) : (
                  <span className="text-faint">—</span>
                ),
            },
          ]}
        />
      </div>
    </>
  );
}
