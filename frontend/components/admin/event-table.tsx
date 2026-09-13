"use client";

import { useRouter, useSearchParams } from "next/navigation";
import { useCallback, useEffect, useState } from "react";
import { cn } from "@/lib/cn";
import { formatInstant } from "@/lib/format";
import { EVENT_LABEL, EVENT_STATUSES, EVENT_TONE, pillClass } from "@/lib/status";
import { useAdminEvents } from "@/hooks/useAdmin";
import { AdminHeader } from "./admin-shell";
import { Button } from "@/components/ui/button";
import { DataTable } from "@/components/ui/data-table";
import { Pagination } from "@/components/ui/pagination";
import { Input, Select } from "@/components/ui/field";
import { Search } from "@/components/ui/icons";
import type { EventStatus, EventSummary } from "@/lib/types";

const SIZE = 20;

export function EventTable() {
  const router = useRouter();
  const searchParams = useSearchParams();

  // Filters live in the URL so a filtered list is a link somebody can send.
  const q = searchParams.get("q") ?? "";
  const status = searchParams.get("status") ?? "";
  const page = Number(searchParams.get("page") ?? 0);

  const [draftQ, setDraftQ] = useState(q);

  const setParams = useCallback(
    (next: Record<string, string | number | undefined>) => {
      const params = new URLSearchParams(searchParams.toString());
      for (const [key, value] of Object.entries(next)) {
        if (value === undefined || value === "" || value === 0) params.delete(key);
        else params.set(key, String(value));
      }
      router.replace(params.size ? `/admin/events?${params}` : "/admin/events", { scroll: false });
    },
    [router, searchParams],
  );

  // Debounced: typing a fixture name should not fire a request per keystroke.
  useEffect(() => {
    if (draftQ === q) return;
    const timer = setTimeout(() => setParams({ q: draftQ, page: 0 }), 300);
    return () => clearTimeout(timer);
  }, [draftQ, q, setParams]);

  const { data, isLoading, error } = useAdminEvents({
    q: q || undefined,
    status: status || undefined,
    page,
    size: SIZE,
  });

  return (
    <>
      <AdminHeader
        kicker="Admin"
        title="Events"
        description="Every event, drafts and cancellations included."
        action={
          <Button href="/admin/events/new" size="sm" arrow>
            New event
          </Button>
        }
      />

      <div className="mt-6 flex flex-wrap items-center gap-3">
        <div className="relative min-w-56 flex-1">
          <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-faint" />
          <Input
            type="search"
            value={draftQ}
            onChange={(e) => setDraftQ(e.target.value)}
            placeholder="Search name, city or stage"
            aria-label="Search events"
            className="pl-9"
          />
        </div>
        <Select
          value={status}
          onChange={(e) => setParams({ status: e.target.value, page: 0 })}
          aria-label="Filter by status"
          placeholder="Any status"
          options={EVENT_STATUSES.map((s) => ({ value: s, label: EVENT_LABEL[s] }))}
          className="w-44"
        />
        {(q || status) && (
          <button
            type="button"
            onClick={() => {
              setDraftQ("");
              setParams({ q: undefined, status: undefined, page: 0 });
            }}
            className="text-[0.8rem] text-muted transition-colors hover:text-bone"
          >
            Reset
          </button>
        )}
      </div>

      <div className="mt-6">
        <DataTable<EventSummary>
          caption="events"
          rows={data?.content}
          rowKey={(e) => e.id}
          isLoading={isLoading}
          error={error}
          rowHref={(e) => `/admin/events/${e.id}`}
          emptyTitle={q || status ? "Nothing matches those filters" : "No events yet"}
          emptyHint={
            q || status
              ? "Try a broader search, or reset the filters."
              : "Create one, then generate its seating layout."
          }
          emptyAction={
            !q && !status ? (
              <Button href="/admin/events/new" size="md" arrow>
                New event
              </Button>
            ) : undefined
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
              key: "name",
              header: "Event",
              cell: (e) => (
                <span className="block">
                  <span className="block font-medium text-bone">{e.name}</span>
                  <span className="tnum mt-0.5 block text-[0.72rem] text-faint">{e.slug}</span>
                </span>
              ),
            },
            {
              key: "starts",
              header: "Starts",
              cell: (e) => <span className="whitespace-nowrap text-muted">{formatInstant(e.startsAt)}</span>,
            },
            {
              key: "venue",
              header: "Venue",
              cell: (e) => (
                <span className="text-muted">
                  {e.stadium}
                  {e.city && <span className="text-faint">, {e.city}</span>}
                </span>
              ),
            },
            {
              key: "status",
              header: "Status",
              cell: (e) => (
                <span className={cn(pillClass, EVENT_TONE[e.status as EventStatus])}>
                  {EVENT_LABEL[e.status as EventStatus] ?? e.status}
                </span>
              ),
            },
            {
              key: "seats",
              header: "Seats",
              align: "right",
              cell: (e) =>
                e.totalSeats > 0 ? (
                  <span>
                    {e.availableSeats}
                    <span className="text-faint"> / {e.totalSeats}</span>
                  </span>
                ) : (
                  <span className="text-faint">No layout</span>
                ),
            },
          ]}
        />
      </div>
    </>
  );
}
