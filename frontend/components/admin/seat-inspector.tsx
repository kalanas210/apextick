"use client";

import Link from "next/link";
import { useCallback, useMemo, useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { cn } from "@/lib/cn";
import { apiErrorMessage } from "@/lib/api";
import { formatInstant } from "@/lib/format";
import { pillClass, SEAT_TONE } from "@/lib/status";
import { useSeatUpdates, type SeatStatusChange } from "@/lib/realtime";
import { useAdminEvent, useAdminSeats, useBlockSeat, useReleaseSeat, useUnblockSeat } from "@/hooks/useAdmin";
import { AdminHeader } from "./admin-shell";
import { Button } from "@/components/ui/button";
import { ConfirmDialog } from "@/components/ui/confirm-dialog";
import { DataTable } from "@/components/ui/data-table";
import { Select } from "@/components/ui/field";
import { Notice, useNotice } from "@/components/ui/notice";
import { Pagination } from "@/components/ui/pagination";
import type { AdminSeat } from "@/lib/types";

/** The endpoint returns every seat in one array, so the paging happens here. */
const PAGE_SIZE = 50;

type SeatAction = "release" | "block" | "unblock";

/** What each action says before it runs, and what it says once it has. */
const ACTION_COPY: Record<SeatAction, { title: string; confirm: string; done: string; failed: string }> = {
  release: {
    title: "Release this hold?",
    confirm: "Release",
    done: "Released",
    failed: "Could not release that seat.",
  },
  block: {
    title: "Take this seat off sale?",
    confirm: "Block",
    done: "Blocked",
    failed: "Could not block that seat.",
  },
  unblock: {
    title: "Put this seat back on sale?",
    confirm: "Unblock",
    done: "Back on sale:",
    failed: "Could not unblock that seat.",
  },
};

export function SeatInspector({ id }: { id: number }) {
  const queryClient = useQueryClient();
  const { data: event } = useAdminEvent(id);
  const { data: seats, isLoading, error } = useAdminSeats(id);
  const release = useReleaseSeat(id);
  const block = useBlockSeat(id);
  const unblock = useUnblockSeat(id);
  const { notice, show, clear } = useNotice();

  const [status, setStatus] = useState<string>("");
  const [page, setPage] = useState(0);
  const [confirming, setConfirming] = useState<{ seat: AdminSeat; action: SeatAction } | null>(null);

  // Live seat changes patch the cache in place, the way the public map does —
  // a refetch would pull the whole seat array back for a one-seat change.
  const onSeatChange = useCallback(
    (changes: SeatStatusChange[]) => {
      queryClient.setQueriesData<AdminSeat[]>({ queryKey: ["admin", "seats", id] }, (current) =>
        current?.map((seat) => {
          const change = changes.find((c) => c.seatId === seat.id);
          // The broadcast carries no holder, so heldBy has to be derived: leaving
          // the old one in place would show an available seat still named to
          // whoever last held it.
          return change
            ? {
                ...seat,
                status: change.status,
                heldUntil: change.heldUntil,
                heldBy: change.status === "HELD" ? seat.heldBy : null,
              }
            : seat;
        }),
      );
    },
    [queryClient, id],
  );
  useSeatUpdates(id, onSeatChange);

  const sectionName = useMemo(() => {
    const map = new Map((event?.sections ?? []).map((s) => [s.id, s.name]));
    return (sectionId: number) => map.get(sectionId) ?? `#${sectionId}`;
  }, [event]);

  const filtered = useMemo(
    () => (status ? (seats ?? []).filter((s) => s.status === status) : (seats ?? [])),
    [seats, status],
  );
  const totalPages = Math.max(1, Math.ceil(filtered.length / PAGE_SIZE));
  // Live updates shrink the filtered set under the operator -- holds expire and a
  // "Held" filter empties out. Clamping keeps them on the last real page instead
  // of an out-of-range one that reads as "no seats in that state".
  const safePage = Math.min(page, totalPages - 1);
  const shown = filtered.slice(safePage * PAGE_SIZE, safePage * PAGE_SIZE + PAGE_SIZE);

  const counts = useMemo(() => {
    const all = seats ?? [];
    return {
      AVAILABLE: all.filter((s) => s.status === "AVAILABLE").length,
      HELD: all.filter((s) => s.status === "HELD").length,
      BOOKED: all.filter((s) => s.status === "BOOKED").length,
      BLOCKED: all.filter((s) => s.status === "BLOCKED").length,
    };
  }, [seats]);

  const mutations = { release, block, unblock };
  const busy = release.isPending || block.isPending || unblock.isPending;

  const run = ({ seat, action }: { seat: AdminSeat; action: SeatAction }) => {
    clear();
    const copy = ACTION_COPY[action];
    mutations[action].mutate(seat.id, {
      onSuccess: () => {
        setConfirming(null);
        show("success", `${copy.done} ${seat.label}.`);
      },
      onError: (err) => {
        setConfirming(null);
        show("error", apiErrorMessage(err, copy.failed));
      },
    });
  };

  return (
    <>
      <AdminHeader
        kicker={
          <Link href={`/admin/events/${id}`} className="ulink">
            {event?.name ?? "Event"}
          </Link>
        }
        title="Seats"
        description={
          seats?.length
            ? `${counts.AVAILABLE} available · ${counts.HELD} held · ${counts.BOOKED} booked · ${counts.BLOCKED} blocked`
            : "Every seat generated for this event."
        }
        action={
          <Button href={`/admin/events/${id}`} size="sm" variant="outline">
            Back to event
          </Button>
        }
      />

      {notice && (
        <Notice tone={notice.tone} onDismiss={clear} className="mt-6">
          {notice.message}
        </Notice>
      )}

      <div className="mt-6 flex flex-wrap items-center gap-3">
        <Select
          value={status}
          onChange={(e) => {
            setStatus(e.target.value);
            setPage(0);
          }}
          aria-label="Filter by seat status"
          placeholder="Any status"
          options={[
            { value: "AVAILABLE", label: "Available" },
            { value: "HELD", label: "Held" },
            { value: "BOOKED", label: "Booked" },
            { value: "BLOCKED", label: "Blocked" },
          ]}
          className="w-44"
        />
        <p className="text-[0.78rem] text-faint">
          A held seat can be released and an available one blocked — a booked seat is undone by refunding its order.
        </p>
      </div>

      <div className="mt-6">
        <DataTable<AdminSeat>
          caption="seats"
          rows={shown}
          rowKey={(s) => s.id}
          isLoading={isLoading}
          error={error}
          emptyTitle={status ? "No seats in that state" : "No seats yet"}
          emptyHint={
            status ? "Try a different status." : "Generate the seating layout to create seats."
          }
          emptyAction={
            !status ? (
              <Button href={`/admin/events/${id}/layout`} size="md">
                Build the layout
              </Button>
            ) : undefined
          }
          footer={
            <Pagination
              page={safePage}
              totalPages={totalPages}
              totalElements={filtered.length}
              onPage={setPage}
            />
          }
          columns={[
            { key: "label", header: "Seat", cell: (s) => <span className="tnum">{s.label}</span> },
            {
              key: "section",
              header: "Section",
              cell: (s) => <span className="text-muted">{sectionName(s.sectionId)}</span>,
            },
            {
              key: "status",
              header: "Status",
              cell: (s) => <span className={cn(pillClass, SEAT_TONE[s.status])}>{s.status}</span>,
            },
            {
              key: "heldBy",
              header: "Held by",
              cell: (s) =>
                s.heldBy ? (
                  <span className="tnum text-[0.75rem] text-faint">{s.heldBy.slice(0, 8)}…</span>
                ) : (
                  <span className="text-faint">—</span>
                ),
            },
            {
              key: "heldUntil",
              header: "Held until",
              cell: (s) => <span className="text-muted">{formatInstant(s.heldUntil)}</span>,
            },
            {
              key: "action",
              header: <span className="sr-only">Actions</span>,
              align: "right",
              cell: (s) => {
                const action: SeatAction | null =
                  s.status === "HELD" ? "release" : s.status === "AVAILABLE" ? "block"
                    : s.status === "BLOCKED" ? "unblock" : null;
                return action ? (
                  <button
                    type="button"
                    onClick={() => setConfirming({ seat: s, action })}
                    className="rounded-full border border-line-2 px-3 py-1 text-[0.75rem] text-muted transition-colors hover:border-bone hover:text-bone"
                  >
                    {ACTION_COPY[action].confirm}
                  </button>
                ) : null;
              },
            },
          ]}
        />
      </div>

      <ConfirmDialog
        open={confirming !== null}
        tone={confirming?.action === "unblock" ? "default" : "danger"}
        title={confirming ? ACTION_COPY[confirming.action].title : ""}
        description={
          confirming?.action === "block" ? (
            <>
              Seat <strong className="text-bone">{confirming.seat.label}</strong> comes off sale: nobody can hold
              or buy it until it is unblocked.
            </>
          ) : confirming?.action === "unblock" ? (
            <>
              Seat <strong className="text-bone">{confirming.seat.label}</strong> goes back on sale immediately.
            </>
          ) : (
            <>
              Seat <strong className="text-bone">{confirming?.seat.label}</strong> goes back on sale
              immediately, and whoever is holding it loses it mid-checkout.
            </>
          )
        }
        confirmLabel={confirming ? ACTION_COPY[confirming.action].confirm : "Confirm"}
        busy={busy}
        onConfirm={() => confirming && run(confirming)}
        onCancel={() => setConfirming(null)}
      />
    </>
  );
}
