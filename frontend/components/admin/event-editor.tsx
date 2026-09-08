"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { cn } from "@/lib/cn";
import { apiErrorCode, apiErrorMessage, apiFieldErrors } from "@/lib/api";
import { formatInstant, formatPrice } from "@/lib/format";
import { EVENT_LABEL, EVENT_STATUSES } from "@/lib/status";
import { useEvent } from "@/hooks/useBooking";
import { useDeleteEvent, useEventStats, useSetEventStatus, useUpdateEvent } from "@/hooks/useAdmin";
import { AdminHeader } from "./admin-shell";
import { EventForm } from "./event-form";
import { Button } from "@/components/ui/button";
import { ConfirmDialog } from "@/components/ui/confirm-dialog";
import { Notice, useNotice } from "@/components/ui/notice";
import type { EventStatus, EventUpsert } from "@/lib/types";

export function EventEditor({ id }: { id: number }) {
  const router = useRouter();
  const { data: event, isLoading, error } = useEvent(String(id));
  const stats = useEventStats(id);
  const update = useUpdateEvent(id);
  const setStatus = useSetEventStatus(id);
  const remove = useDeleteEvent();
  const { notice, show, clear } = useNotice();

  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [confirmDelete, setConfirmDelete] = useState(false);

  if (isLoading) {
    return (
      <div className="grid place-items-center py-24" aria-busy="true">
        <span className="h-7 w-7 animate-spin rounded-full border-2 border-line-2 border-t-accent" />
        <span className="sr-only">Loading the event…</span>
      </div>
    );
  }

  if (error || !event) {
    return (
      <div className="rounded-2xl border border-line bg-ink-2 p-8 text-center">
        <p className="text-[0.9rem] text-muted">{apiErrorMessage(error, "That event could not be loaded.")}</p>
        <div className="mt-6 flex justify-center">
          <Button href="/admin/events" size="md" variant="outline">
            Back to events
          </Button>
        </div>
      </div>
    );
  }

  const hasLayout = (event.tiers?.length ?? 0) > 0 || (event.sections?.length ?? 0) > 0;

  const save = (body: EventUpsert) => {
    setFieldErrors({});
    clear();
    update.mutate(body, {
      onSuccess: () => show("success", "Saved."),
      onError: (err) => {
        setFieldErrors(apiFieldErrors(err));
        if (apiErrorCode(err) === "SLUG_TAKEN") {
          setFieldErrors({ slug: "Another event already uses this slug." });
          show("error", "That slug is taken.");
        } else {
          show("error", apiErrorMessage(err, "Could not save the event."));
        }
      },
    });
  };

  const changeStatus = (status: EventStatus) => {
    clear();
    setStatus.mutate(status, {
      onSuccess: () => show("success", `Now ${EVENT_LABEL[status].toLowerCase()}.`),
      onError: (err) => show("error", apiErrorMessage(err, "Could not change the status.")),
    });
  };

  const destroy = () => {
    clear();
    remove.mutate(id, {
      onSuccess: () => router.push("/admin/events"),
      onError: (err) => {
        setConfirmDelete(false);
        show(
          "error",
          apiErrorCode(err) === "EVENT_HAS_ORDERS"
            ? "This event has orders, so it cannot be deleted. Set it to Cancelled instead."
            : apiErrorMessage(err, "Could not delete the event."),
        );
      },
    });
  };

  return (
    <>
      <AdminHeader
        kicker={
          <Link href="/admin/events" className="ulink">
            Events
          </Link>
        }
        title={event.name}
        description={
          <>
            <span className="tnum">{event.slug}</span> · {event.stadium}
            {event.city ? `, ${event.city}` : ""} · {formatInstant(event.startsAt)}
          </>
        }
        action={
          <div className="flex gap-2">
            <Button href={`/admin/events/${id}/seats`} size="sm" variant="outline">
              Seats
            </Button>
            <Button href={`/admin/events/${id}/layout`} size="sm" variant={hasLayout ? "outline" : "primary"}>
              {hasLayout ? "Layout" : "Build layout"}
            </Button>
          </div>
        }
      />

      {notice && (
        <Notice tone={notice.tone} onDismiss={clear} className="mt-6">
          {notice.message}
        </Notice>
      )}

      <div className="mt-8 grid gap-10 lg:grid-cols-12 lg:gap-12">
        <div className="lg:col-span-7 xl:col-span-8">
          <EventForm
            mode="edit"
            initial={event}
            busy={update.isPending}
            fieldErrors={fieldErrors}
            onSubmit={save}
          />
        </div>

        <aside className="lg:col-span-5 xl:col-span-4">
          {/* Capped and scrollable: a sticky column taller than the viewport
              would strand whatever sits at its bottom. */}
            <div className="space-y-6 lg:sticky lg:top-8 lg:max-h-[calc(100vh-4rem)] lg:overflow-y-auto lg:pr-1 [scrollbar-width:thin]">
            <section className="rounded-2xl border border-line bg-ink-2 p-6">
              <h2 className="kicker">Sales status</h2>
              <p className="mt-2 text-[0.78rem] text-faint">
                Drafts and cancellations are hidden from the public catalog.
              </p>
              <div className="mt-4 flex flex-wrap gap-2">
                {EVENT_STATUSES.map((s) => {
                  const active = event.status === s;
                  return (
                    <button
                      key={s}
                      type="button"
                      aria-pressed={active}
                      disabled={setStatus.isPending}
                      onClick={() => !active && changeStatus(s)}
                      className={cn(
                        "rounded-full border px-3 py-1.5 font-mono text-[0.6rem] uppercase tracking-[0.14em] transition-colors",
                        active
                          ? "border-accent bg-accent/10 text-accent"
                          : "border-line-2 text-muted hover:border-bone hover:text-bone",
                        setStatus.isPending && "opacity-60",
                      )}
                    >
                      {EVENT_LABEL[s]}
                    </button>
                  );
                })}
              </div>
            </section>

            <section className="rounded-2xl border border-line bg-ink-2 p-6">
              <h2 className="kicker">Seating</h2>
              {stats.isLoading ? (
                <p className="mt-3 text-[0.82rem] text-faint">Loading…</p>
              ) : stats.error || !stats.data ? (
                <p className="mt-3 text-[0.82rem] text-faint">Stats unavailable.</p>
              ) : stats.data.total === 0 ? (
                <>
                  <p className="mt-3 text-[0.82rem] text-muted">No layout yet.</p>
                  <div className="mt-4">
                    <Button href={`/admin/events/${id}/layout`} size="sm">
                      Build the layout
                    </Button>
                  </div>
                </>
              ) : (
                <>
                  <dl className="mt-4 space-y-2 text-[0.84rem]">
                    <Row label="Available" value={stats.data.available} />
                    <Row label="Held" value={stats.data.held} />
                    <Row label="Booked" value={stats.data.booked} />
                    <Row label="Total" value={stats.data.total} strong />
                    <Row
                      label="Revenue"
                      value={formatPrice(stats.data.revenue, stats.data.currency)}
                      strong
                    />
                  </dl>
                  {stats.data.byTier.length > 0 && (
                    <div className="mt-5 border-t border-line pt-4">
                      <p className="kicker mb-2">By tier</p>
                      <ul className="space-y-1.5 text-[0.8rem]">
                        {stats.data.byTier.map((t) => (
                          <li key={t.tierId} className="flex justify-between gap-3">
                            <span className="text-muted">{t.tierCode}</span>
                            <span className="tnum text-bone">
                              {t.available}
                              <span className="text-faint"> / {t.total}</span>
                            </span>
                          </li>
                        ))}
                      </ul>
                    </div>
                  )}
                </>
              )}
            </section>

            <section className="rounded-2xl border border-[#ff6b6b]/25 bg-ink-2 p-6">
              <h2 className="kicker text-[#ff6b6b]">Danger zone</h2>
              <p className="mt-2 text-[0.8rem] text-muted">
                Deleting removes the event and its entire seating layout. An event with orders
                cannot be deleted — cancel it instead.
              </p>
              <div className="mt-4">
                <Button
                  onClick={() => setConfirmDelete(true)}
                  size="sm"
                  variant="outline"
                  className="border-[#ff6b6b]/50 text-[#ff6b6b] hover:border-[#ff6b6b] hover:bg-[#ff6b6b]/10"
                >
                  Delete event
                </Button>
              </div>
            </section>
          </div>
        </aside>
      </div>

      <ConfirmDialog
        open={confirmDelete}
        tone="danger"
        title="Delete this event?"
        description={
          <>
            <strong className="text-bone">{event.name}</strong> and its seating layout
            {hasLayout ? ` (${event.totalSeats} seats)` : ""} will be removed. This cannot be undone.
          </>
        }
        confirmLabel="Delete"
        busy={remove.isPending}
        onConfirm={destroy}
        onCancel={() => setConfirmDelete(false)}
      />
    </>
  );
}

function Row({ label, value, strong }: { label: string; value: string | number; strong?: boolean }) {
  return (
    <div className="flex justify-between gap-3">
      <dt className="text-muted">{label}</dt>
      <dd className={cn("tnum", strong ? "text-bone" : "text-bone-2")}>{value}</dd>
    </div>
  );
}
