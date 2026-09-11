"use client";

import { useEffect, useMemo, useState, type CSSProperties } from "react";
import { useRouter } from "next/navigation";
import { useQueryClient } from "@tanstack/react-query";
import { formatPrice } from "@/lib/format";
import { MAX_SEATS_PER_ORDER, quoteOrder } from "@/lib/booking-rules";
import { apiErrorCode, apiErrorMessage } from "@/lib/api";
import { useSeatUpdates, type SeatStatusChange } from "@/lib/realtime";
import { cn } from "@/lib/cn";
import {
  useCreateOrder,
  useEvent,
  useHoldSeats,
  useReleaseHold,
  useSeats,
} from "@/hooks/useBooking";
import { useSession } from "@/hooks/useSession";
import type { EventDetail, Seat as ApiSeat } from "@/lib/types";
import { Legend, Pitch, Stand, mmss, type MapSeat } from "./parts";
import { Clock } from "@/components/ui/icons";

interface MapSection {
  sectionId: number;
  sectionName: string;
  side: "n" | "s" | "e" | "w";
  tierId: string;
  seats: MapSeat[];
}

/** Folds API seats into the sections described by the event, ready to render. */
function buildSections(event: EventDetail, seats: ApiSeat[]): MapSection[] {
  const tierNameByCode = new Map(event.tiers.map((t) => [t.code, t.name]));
  const sectionById = new Map(event.sections.map((s) => [s.id, s]));
  const grouped = new Map<number, MapSeat[]>();

  for (const seat of seats) {
    const section = sectionById.get(seat.sectionId);
    if (!section) continue;
    const state: MapSeat["state"] =
      seat.status === "BOOKED" ? "sold"
        // a seat this user is holding stays pickable — it is already theirs
        : seat.status === "HELD" && !seat.mine ? "held"
          : "available";
    const list = grouped.get(seat.sectionId) ?? [];
    list.push({
      id: String(seat.id),
      label: seat.label,
      col: seat.col ?? list.length,
      sectionName: section.name,
      tierId: seat.tierCode,
      tierName: tierNameByCode.get(seat.tierCode) ?? seat.tierCode,
      state,
      price: seat.price,
    });
    grouped.set(seat.sectionId, list);
  }

  return event.sections
    .filter((s) => grouped.has(s.id))
    .map((s) => ({
      sectionId: s.id,
      sectionName: s.name,
      side: s.side,
      tierId: s.tierCode,
      seats: (grouped.get(s.id) ?? []).sort(
        (a, b) => a.label.localeCompare(b.label, undefined, { numeric: true }),
      ),
    }));
}

export function LiveSeatMap({
  slug,
  home,
  away,
  initialTier,
  initialSection,
}: {
  slug: string;
  home: string;
  away: string;
  initialTier?: string;
  initialSection?: string;
}) {
  const router = useRouter();
  const queryClient = useQueryClient();
  const { isAuthenticated, isLoading: authLoading, signIn } = useSession();

  const { data: event, isLoading: eventLoading, error: eventError } = useEvent(slug);
  const { data: seats, isLoading: seatsLoading } = useSeats(slug);
  const holdSeats = useHoldSeats(slug);
  const releaseHold = useReleaseHold(slug);
  const createOrder = useCreateOrder();

  // `null` means "the user hasn't picked anything yet", which is what lets an
  // existing server-side hold seed the selection without an effect.
  const [picked, setPicked] = useState<string[] | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [holdExpiry, setHoldExpiry] = useState<string | null>(null);
  const [now, setNow] = useState(() => Date.now());

  // Live seat flips from other buyers, applied straight into the cache so the
  // map moves without waiting for a refetch.
  useSeatUpdates(event?.id, (changes: SeatStatusChange[]) => {
    queryClient.setQueriesData<ApiSeat[]>({ queryKey: ["seats", slug] }, (current) => {
      if (!current) return current;
      const byId = new Map(changes.map((c) => [c.seatId, c]));
      return current.map((seat) => {
        const change = byId.get(seat.id);
        if (!change) return seat;
        // our own holds are only ever confirmed by our own responses
        return change.status === "HELD" && seat.mine
          ? seat
          : { ...seat, status: change.status, heldUntil: change.heldUntil, mine: false };
      });
    });
  });

  // Seats the API already says are ours — a hold that survived a reload.
  const mine = useMemo(
    () => seats?.filter((s) => s.status === "HELD" && s.mine) ?? [],
    [seats],
  );
  const mineIds = useMemo(() => mine.map((s) => String(s.id)), [mine]);
  const heldUntil =
    holdExpiry ??
    mine.map((s) => s.heldUntil).filter((v): v is string => Boolean(v)).sort()[0] ??
    null;

  // Countdown ticker, only while a server-side hold is actually running.
  useEffect(() => {
    if (!heldUntil) return;
    const id = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(id);
  }, [heldUntil]);

  const sections = useMemo(
    () => (event && seats ? buildSections(event, seats) : []),
    [event, seats],
  );
  const byId = useMemo(() => {
    const map = new Map<string, MapSeat>();
    sections.forEach((sec) => sec.seats.forEach((s) => map.set(s.id, s)));
    return map;
  }, [sections]);

  // Selections are filtered rather than stored-and-corrected: a seat another
  // buyer just took simply stops being selected, with no extra render pass.
  const selected = useMemo(
    () => (picked ?? mineIds).filter((id) => byId.get(id)?.state === "available"),
    [picked, mineIds, byId],
  );

  const selectedSet = useMemo(() => new Set(selected), [selected]);
  const selectedSeats = selected
    .map((id) => byId.get(id))
    .filter((s): s is MapSeat => Boolean(s))
    .sort((a, b) => a.label.localeCompare(b.label, undefined, { numeric: true }));

  const currency = event?.currency ?? "USD";
  // priced exactly as the order will be, so checkout shows the same total
  const { subtotal, fee, total } = quoteOrder(selectedSeats.map((s) => s.price));

  const secondsLeft = heldUntil
    ? Math.max(0, Math.floor((new Date(heldUntil).getTime() - now) / 1000))
    : null;

  const toggle = (id: string) => {
    const seat = byId.get(id);
    if (!seat || seat.state !== "available") return;
    if (!selected.includes(id) && selected.length >= MAX_SEATS_PER_ORDER) {
      setNotice(`That is the ${MAX_SEATS_PER_ORDER} seat limit for a single order.`);
      return;
    }
    setNotice(null);
    setPicked(
      selected.includes(id) ? selected.filter((x) => x !== id) : [...selected, id],
    );
  };

  const busy = holdSeats.isPending || createOrder.isPending;

  /** Hold the picked seats, turn them into an order, and go pay. */
  const reserve = async () => {
    if (!event || selectedSeats.length === 0) return;
    setNotice(null);
    const seatIds = selected.map(Number);
    try {
      const hold = await holdSeats.mutateAsync(seatIds);
      setHoldExpiry(hold.heldUntil);
      const order = await createOrder.mutateAsync({ eventId: event.id, seatIds });
      router.push(`/checkout/${order.id}`);
    } catch (error) {
      const code = apiErrorCode(error);
      setNotice(
        code === "SEAT_UNAVAILABLE"
          ? "Someone just took one of those seats. Your picks have been refreshed — try again."
          : apiErrorMessage(error, "We could not hold those seats. Please try again."),
      );
      queryClient.invalidateQueries({ queryKey: ["seats", slug] });
    }
  };

  const release = async () => {
    await releaseHold.mutateAsync().catch(() => undefined);
    setPicked([]);
    setHoldExpiry(null);
  };

  if (eventLoading || seatsLoading) {
    return (
      <div className="grid place-items-center py-24" aria-busy="true">
        <span className="h-7 w-7 animate-spin rounded-full border-2 border-line-2 border-t-accent" />
        <span className="sr-only">Loading the live seat map…</span>
      </div>
    );
  }

  if (eventError || !event) {
    return (
      <div className="rounded-2xl border border-line bg-ink-2 p-8 text-center">
        <p className="text-[0.9rem] text-muted">
          The live seat map is unavailable right now.
        </p>
        <p className="mt-2 text-[0.76rem] text-faint">
          {apiErrorMessage(eventError, "Could not reach the booking service.")}
        </p>
      </div>
    );
  }

  const isHighlighted = (sectionCode: string, tierCode: string) =>
    (!!initialSection && sectionCode === initialSection) ||
    (!!initialTier && tierCode === initialTier);

  const renderStand = (side: MapSection["side"], className?: string) => {
    const sec = sections.find((s) => s.side === side);
    if (!sec) return null;
    return (
      <Stand
        name={sec.sectionName}
        seats={sec.seats}
        tierId={sec.tierId}
        selectedIds={selectedSet}
        onToggle={toggle}
        highlighted={isHighlighted(String(sec.sectionId), sec.tierId)}
        className={className}
      />
    );
  };

  const usedTierCodes = Array.from(new Set(sections.map((s) => s.tierId)));
  const legendTiers = usedTierCodes.map((code) => ({
    id: code,
    name: event.tiers.find((t) => t.code === code)?.name ?? code,
  }));

  return (
    <div className="grid gap-10 lg:grid-cols-12 lg:gap-12">
      {/* Map */}
      <div className="lg:col-span-7 xl:col-span-8">
        <div className="mb-6">
          <Legend tiers={legendTiers} />
        </div>

        <div
          className="overflow-x-auto pb-2"
          style={{ "--seat": "clamp(0.95rem, 3vw, 1.12rem)" } as CSSProperties}
        >
          <div className="mx-auto flex min-w-[300px] max-w-2xl flex-col items-center gap-3">
            {renderStand("n", "w-full")}
            <div className="flex w-full flex-col items-stretch gap-3 sm:flex-row sm:justify-center">
              {renderStand("w")}
              <Pitch home={home} away={away} />
              {renderStand("e")}
            </div>
            {renderStand("s", "w-full")}
          </div>
        </div>

        <p className="mt-6 text-center text-[0.76rem] text-faint">
          Live availability — {event.availableSeats} of {event.totalSeats} seats open.
          Updates stream in as other buyers pick.
        </p>
      </div>

      {/* Summary */}
      <aside className="lg:col-span-5 xl:col-span-4">
        <div className="lg:sticky lg:top-28">
          <div className="rounded-2xl border border-line bg-ink-2 p-6">
            <p className="sr-only" aria-live="polite">
              {selectedSeats.length === 0
                ? "No seats selected."
                : `${selectedSeats.length} seat${selectedSeats.length === 1 ? "" : "s"} selected. Total ${formatPrice(total, currency)}.`}
            </p>

            <div className="flex items-center justify-between">
              <h2 className="font-display text-lg font-semibold tracking-tight">
                Your selection
              </h2>
              {secondsLeft !== null && (
                <span
                  role="timer"
                  aria-label={`Seats held, ${mmss(secondsLeft)} remaining`}
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

            {notice && (
              <p className="mt-4 rounded-lg border border-accent/30 bg-accent/10 px-3 py-2 text-[0.78rem] text-accent">
                {notice}
              </p>
            )}

            {selectedSeats.length === 0 ? (
              <div className="py-12 text-center">
                <p className="text-[0.9rem] text-muted">Tap an open seat to begin.</p>
                <p className="mt-2 text-[0.76rem] text-faint">
                  Gold, premium, and standard stands are color coded above.
                </p>
              </div>
            ) : (
              <>
                <ul className="mt-5 max-h-64 space-y-1.5 overflow-y-auto pr-1">
                  {selectedSeats.map((s) => (
                    <li
                      key={s.id}
                      className="flex items-center justify-between gap-3 rounded-lg border border-line bg-ink px-3 py-2.5"
                    >
                      <span className="text-[0.84rem] text-bone">
                        {s.sectionName}
                        <span className="tnum text-muted"> · {s.label}</span>
                      </span>
                      <span className="flex items-center gap-3">
                        <span className="tnum text-[0.84rem] text-bone">
                          {formatPrice(s.price, currency)}
                        </span>
                        <button
                          type="button"
                          aria-label={`Remove seat ${s.label}`}
                          onClick={() => toggle(s.id)}
                          className="grid h-5 w-5 place-items-center rounded-full text-faint transition-colors hover:bg-bone/10 hover:text-bone"
                        >
                          &times;
                        </button>
                      </span>
                    </li>
                  ))}
                </ul>

                <dl className="mt-5 space-y-2 border-t border-line pt-5 text-[0.86rem]">
                  <div className="flex justify-between text-muted">
                    <dt>
                      Subtotal<span className="tnum"> ({selectedSeats.length})</span>
                    </dt>
                    <dd className="tnum text-bone">{formatPrice(subtotal, currency)}</dd>
                  </div>
                  <div className="flex justify-between text-muted">
                    <dt>Booking fee</dt>
                    <dd className="tnum text-bone">{formatPrice(fee, currency)}</dd>
                  </div>
                  <div className="mt-1 flex items-baseline justify-between border-t border-line pt-3">
                    <dt className="font-display text-base text-bone">Total</dt>
                    <dd className="tnum text-xl text-bone">{formatPrice(total, currency)}</dd>
                  </div>
                </dl>

                {authLoading ? null : isAuthenticated ? (
                  <button
                    type="button"
                    onClick={reserve}
                    disabled={busy}
                    className="mt-6 inline-flex h-12 w-full items-center justify-center gap-2 rounded-full bg-accent text-[0.92rem] font-medium text-accent-ink transition-[filter] hover:brightness-105 disabled:opacity-60"
                  >
                    {busy ? (
                      <>
                        <span className="h-4 w-4 animate-spin rounded-full border-2 border-accent-ink/30 border-t-accent-ink" />
                        Holding your seats…
                      </>
                    ) : (
                      <>
                        Reserve {selectedSeats.length} seat
                        {selectedSeats.length === 1 ? "" : "s"}
                      </>
                    )}
                  </button>
                ) : (
                  <button
                    type="button"
                    onClick={signIn}
                    className="mt-6 inline-flex h-12 w-full items-center justify-center rounded-full bg-accent text-[0.92rem] font-medium text-accent-ink transition-[filter] hover:brightness-105"
                  >
                    Sign in to reserve
                  </button>
                )}

                {heldUntil && (
                  <button
                    type="button"
                    onClick={release}
                    className="mt-3 w-full text-center text-[0.72rem] text-faint underline-offset-4 transition-colors hover:text-bone hover:underline"
                  >
                    Release my seats
                  </button>
                )}

                <p className="mt-3 text-center text-[0.72rem] text-faint">
                  Seats are held for a few minutes while you pay.
                </p>
              </>
            )}
          </div>
        </div>
      </aside>
    </div>
  );
}
