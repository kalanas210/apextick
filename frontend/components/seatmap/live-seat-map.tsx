"use client";

import { useEffect, useMemo, useState, type CSSProperties } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useQueryClient } from "@tanstack/react-query";
import { formatPrice } from "@/lib/format";
import { MAX_SEATS_PER_ORDER, quoteOrder } from "@/lib/booking-rules";
import { apiErrorCode, apiErrorMessage, apiProblem } from "@/lib/api";
import { findPendingOrder } from "@/lib/orders";
import { salesState } from "@/lib/sales-window";
import { useSeatUpdates, type SeatStatusChange } from "@/lib/realtime";
import { cn } from "@/lib/cn";
import {
  useCancelOrder,
  useCreateOrder,
  useEvent,
  useHoldSeats,
  useMyOrders,
  useReleaseHold,
  useSeats,
} from "@/hooks/useBooking";
import { useSession } from "@/hooks/useSession";
import type { Seat as ApiSeat } from "@/lib/types";
import { Legend, Pitch, Stand, mmss, type MapSeat } from "./parts";
import { buildSections, isLinkedSection, type MapSection } from "./sections";
import { Clock } from "@/components/ui/icons";

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
  // Seats the API says are locked behind an unpaid order of the buyer's own.
  const [blockedSeatIds, setBlockedSeatIds] = useState<number[] | null>(null);

  // Fetched only once such a refusal lands: it names the seats but not the order
  // that covers them, so the buyer's own list is what turns it into a link.
  const { data: myOrders } = useMyOrders(blockedSeatIds !== null);

  const blockingOrder = useMemo(
    () =>
      blockedSeatIds && event
        ? findPendingOrder(myOrders, event.id, blockedSeatIds)
        : undefined,
    [blockedSeatIds, event, myOrders],
  );
  const cancelOrder = useCancelOrder(blockingOrder?.id ?? "");

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

  // The same answer the API's SalesWindow gives, asked before the buyer picks
  // rather than after: a fixture that has sold out, closed, or kicked off stops
  // offering seats here instead of refusing the Reserve click.
  const sales = useMemo(() => (event ? salesState(event, now) : null), [event, now]);
  const salesOpen = sales?.open ?? true;

  // Countdown ticker: while a server-side hold is running, and while a sales
  // window is still ahead — that one has to unlock the map on its own.
  const ticking = Boolean(heldUntil) || sales?.code === "not-yet-open";
  useEffect(() => {
    if (!ticking) return;
    const id = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(id);
  }, [ticking]);

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
  const money = (value: number) => formatPrice(value, currency, { keepMinorUnits: true });

  const secondsLeft = heldUntil
    ? Math.max(0, Math.floor((new Date(heldUntil).getTime() - now) / 1000))
    : null;

  const toggle = (id: string) => {
    const seat = byId.get(id);
    if (!seat || seat.state !== "available") return;
    // The stands are already inert when sales are shut; this covers the remove
    // buttons in the selection list, which a surviving hold still renders.
    if (!salesOpen && !selected.includes(id)) return;
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

  /** Point the buyer at the unpaid order that is holding these seats down. */
  const showBlockingOrder = (seatIds: number[]) => {
    setNotice(null);
    setBlockedSeatIds(seatIds);
    queryClient.invalidateQueries({ queryKey: ["orders"] });
  };

  /** Hold the picked seats, turn them into an order, and go pay. */
  const reserve = async () => {
    if (!event || selectedSeats.length === 0 || !salesOpen) return;
    setNotice(null);
    const seatIds = selected.map(Number);
    try {
      const hold = await holdSeats.mutateAsync(seatIds);
      setHoldExpiry(hold.heldUntil);
      const order = await createOrder.mutateAsync({ eventId: event.id, seatIds });
      router.push(`/checkout/${order.id}`);
    } catch (error) {
      const code = apiErrorCode(error);
      if (code === "ORDER_ALREADY_PENDING") {
        // The hold succeeded; it is the order that was refused, because an
        // earlier unpaid one already covers some of these seats.
        showBlockingOrder(apiProblem(error)?.seatIds ?? seatIds);
        return;
      }
      setNotice(
        code === "SEAT_UNAVAILABLE"
          ? "Someone just took one of those seats. Your picks have been refreshed — try again."
          : apiErrorMessage(error, "We could not hold those seats. Please try again."),
      );
      if (code === "SALES_CLOSED" || code === "SALES_NOT_OPEN") {
        // The window shut under us — refetch the event so the map says so too.
        queryClient.invalidateQueries({ queryKey: ["event", slug] });
      }
      queryClient.invalidateQueries({ queryKey: ["seats", slug] });
    }
  };

  const release = async () => {
    setNotice(null);
    try {
      await releaseHold.mutateAsync();
      setBlockedSeatIds(null);
      setPicked([]);
      setHoldExpiry(null);
    } catch (error) {
      if (apiErrorCode(error) === "ORDER_PENDING") {
        // Nothing was released, so the selection, the countdown and this link all
        // have to survive — clearing them would hide the only way out.
        showBlockingOrder(apiProblem(error)?.seatIds ?? []);
        return;
      }
      setNotice(apiErrorMessage(error, "We could not release those seats. Please try again."));
    }
  };

  /** Cancel the blocking order, which is what puts its seats back on sale. */
  const cancelBlockingOrder = async () => {
    if (!blockingOrder) return;
    try {
      await cancelOrder.mutateAsync();
      setBlockedSeatIds(null);
      setPicked([]);
      setHoldExpiry(null);
      setNotice("That order was cancelled and its seats are back on sale.");
      queryClient.invalidateQueries({ queryKey: ["seats", slug] });
    } catch (error) {
      setNotice(apiErrorMessage(error, "We could not cancel that order."));
    }
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
        highlighted={isLinkedSection(sec, { section: initialSection, tier: initialTier })}
        className={className}
        locked={!salesOpen}
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
        {sales && !sales.open && (
          <div
            role="status"
            className="mb-6 rounded-xl border border-line-2 bg-ink-2 px-4 py-3.5"
          >
            <p className="font-mono text-[0.62rem] uppercase tracking-[0.16em] text-bone/80">
              {sales.title}
            </p>
            <p className="mt-1.5 text-[0.82rem] text-muted">{sales.detail}</p>
          </div>
        )}

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
          {salesOpen ? (
            <>
              Live availability — {event.availableSeats} of {event.totalSeats} seats
              open. Updates stream in as other buyers pick.
            </>
          ) : (
            <>
              {event.availableSeats} of {event.totalSeats} seats are unsold. The map is
              read-only while the fixture is not selling.
            </>
          )}
        </p>
      </div>

      {/* Summary */}
      <aside className="lg:col-span-5 xl:col-span-4">
        <div className="lg:sticky lg:top-28">
          <div className="rounded-2xl border border-line bg-ink-2 p-6">
            <p className="sr-only" aria-live="polite">
              {selectedSeats.length === 0
                ? "No seats selected."
                : `${selectedSeats.length} seat${selectedSeats.length === 1 ? "" : "s"} selected. Total ${money(total)}.`}
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

            {blockedSeatIds && (
              <div
                role="alert"
                className="mt-4 rounded-lg border border-accent/30 bg-accent/10 px-3 py-2.5 text-[0.78rem] text-accent"
              >
                <p>
                  These seats are on an order you have not paid for yet. Pay it or
                  cancel it — they cannot be freed while it stands.
                </p>
                <div className="mt-2.5 flex flex-wrap items-center gap-x-4 gap-y-2">
                  {blockingOrder ? (
                    <>
                      <Link
                        href={`/checkout/${blockingOrder.id}`}
                        className="underline underline-offset-4 hover:no-underline"
                      >
                        Continue to payment
                      </Link>
                      <button
                        type="button"
                        onClick={cancelBlockingOrder}
                        disabled={cancelOrder.isPending}
                        className="underline underline-offset-4 hover:no-underline disabled:opacity-60"
                      >
                        {cancelOrder.isPending ? "Cancelling…" : "Cancel that order"}
                      </button>
                    </>
                  ) : (
                    <Link
                      href="/account"
                      className="underline underline-offset-4 hover:no-underline"
                    >
                      Find it in your orders
                    </Link>
                  )}
                </div>
              </div>
            )}

            {selectedSeats.length === 0 ? (
              <div className="py-12 text-center">
                {salesOpen ? (
                  <>
                    <p className="text-[0.9rem] text-muted">Tap an open seat to begin.</p>
                    <p className="mt-2 text-[0.76rem] text-faint">
                      Gold, premium, and standard stands are color coded above.
                    </p>
                  </>
                ) : (
                  <>
                    <p className="text-[0.9rem] text-muted">{sales?.title}</p>
                    <p className="mt-2 text-[0.76rem] text-faint">{sales?.detail}</p>
                    <Link
                      href="/events"
                      className="mt-5 inline-block text-[0.76rem] text-muted underline underline-offset-4 transition-colors hover:text-bone"
                    >
                      Browse other fixtures
                    </Link>
                  </>
                )}
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
                          {money(s.price)}
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
                    <dd className="tnum text-bone">{money(subtotal)}</dd>
                  </div>
                  <div className="flex justify-between text-muted">
                    <dt>Booking fee</dt>
                    <dd className="tnum text-bone">{money(fee)}</dd>
                  </div>
                  <div className="mt-1 flex items-baseline justify-between border-t border-line pt-3">
                    <dt className="font-display text-base text-bone">Total</dt>
                    <dd className="tnum text-xl text-bone">{money(total)}</dd>
                  </div>
                </dl>

                {!salesOpen ? (
                  <p className="mt-6 rounded-lg border border-line-2 px-3 py-2.5 text-center text-[0.78rem] text-muted">
                    {sales?.detail || sales?.title}
                    {heldUntil && " Your hold stands until the timer runs out."}
                  </p>
                ) : authLoading ? null : isAuthenticated ? (
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

                {salesOpen && (
                  <p className="mt-3 text-center text-[0.72rem] text-faint">
                    Seats are held for a few minutes while you pay.
                  </p>
                )}
              </>
            )}
          </div>
        </div>
      </aside>
    </div>
  );
}
