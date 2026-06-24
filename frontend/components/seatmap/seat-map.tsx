"use client";

import {
  useEffect,
  useMemo,
  useRef,
  useState,
  type CSSProperties,
} from "react";
import type { Fixture } from "@/data/types";
import { getSeries } from "@/data/events";
import { formatPrice } from "@/lib/format";
import { withAlpha } from "@/lib/color";
import { cn } from "@/lib/cn";
import { buildSeatSections, type GeneratedSeat } from "@/lib/seats";
import { tierColor, HELD_COLOR } from "./tier-colors";
import { Clock, Check } from "@/components/ui/icons";

const MAX_SEATS = 8;
const HOLD_SECONDS = 420;

function mmss(total: number) {
  const m = Math.floor(total / 60);
  const s = total % 60;
  return `${m}:${s.toString().padStart(2, "0")}`;
}

/* --------------------------------- Seat ---------------------------------- */

function Seat({
  seat,
  selected,
  onToggle,
}: {
  seat: GeneratedSeat;
  selected: boolean;
  onToggle: (id: string) => void;
}) {
  const color = tierColor(seat.tierId);
  const disabled = seat.state !== "available";

  let style: CSSProperties = {
    width: "var(--seat)",
    height: "var(--seat)",
  };
  if (seat.state === "sold") {
    style = { ...style, background: "var(--ink-3)", borderColor: "transparent" };
  } else if (seat.state === "held") {
    style = {
      ...style,
      background: withAlpha(HELD_COLOR, 0.25),
      borderColor: withAlpha(HELD_COLOR, 0.6),
    };
  } else if (selected) {
    style = { ...style, background: color, borderColor: color };
  } else {
    style = {
      ...style,
      background: withAlpha(color, 0.16),
      borderColor: withAlpha(color, 0.5),
    };
  }

  return (
    <button
      type="button"
      disabled={disabled}
      aria-pressed={selected}
      aria-label={`${seat.sectionName} seat ${seat.label}, ${
        seat.state === "available"
          ? `available, ${seat.tierName}`
          : seat.state
      }`}
      title={`${seat.sectionName} ${seat.label} · ${seat.tierName}`}
      onClick={() => onToggle(seat.id)}
      style={style}
      className={cn(
        "rounded-[3px] border transition-transform duration-150",
        seat.state === "sold" && "opacity-40",
        seat.state === "held" && "animate-pulse cursor-not-allowed",
        seat.state === "available" &&
          "cursor-pointer hover:scale-[1.18] hover:brightness-125",
        selected && "scale-[1.12] ring-2 ring-bone ring-offset-1 ring-offset-ink",
      )}
    />
  );
}

/* -------------------------------- Stand ---------------------------------- */

function Stand({
  name,
  seats,
  tierId,
  selectedIds,
  onToggle,
  highlighted,
  className,
}: {
  name: string;
  seats: GeneratedSeat[];
  tierId: string;
  selectedIds: Set<string>;
  onToggle: (id: string) => void;
  highlighted: boolean;
  className?: string;
}) {
  const cols = Math.max(...seats.map((s) => s.col)) + 1;
  const available = seats.filter((s) => s.state === "available").length;
  const color = tierColor(tierId);

  return (
    <div
      className={cn(
        "rounded-xl border bg-ink-2/60 p-3 transition-colors",
        highlighted ? "border-bone/40" : "border-line",
        className,
      )}
    >
      <div className="mb-2.5 flex items-center justify-between gap-2">
        <span className="flex items-center gap-1.5">
          <span
            className="h-2 w-2 rounded-full"
            style={{ background: color }}
          />
          <span className="font-mono text-[0.58rem] uppercase tracking-[0.14em] text-bone/80">
            {name}
          </span>
        </span>
        <span className="tnum text-[0.6rem] text-faint">{available} open</span>
      </div>
      <div
        className="grid justify-center gap-[3px]"
        style={{ gridTemplateColumns: `repeat(${cols}, var(--seat))` }}
      >
        {seats.map((seat) => (
          <Seat
            key={seat.id}
            seat={seat}
            selected={selectedIds.has(seat.id)}
            onToggle={onToggle}
          />
        ))}
      </div>
    </div>
  );
}

function Pitch({ fixture }: { fixture: Fixture }) {
  return (
    <div className="relative grid min-h-[110px] min-w-[140px] flex-1 place-items-center rounded-xl border border-line bg-ink">
      <div className="absolute inset-5 rounded-[36%] border border-line-2/50" />
      <div className="absolute h-12 w-12 rounded-full border border-line-2/50" />
      <div className="absolute inset-x-5 top-1/2 h-px bg-line-2/50" />
      <div className="relative text-center">
        <div className="font-display text-sm font-semibold tracking-tight text-bone">
          {fixture.home.short} <span className="text-faint">v</span>{" "}
          {fixture.away.short}
        </div>
        <div className="font-mono text-[0.5rem] uppercase tracking-[0.2em] text-faint">
          Field of play
        </div>
      </div>
    </div>
  );
}

/* ------------------------------- Legend ---------------------------------- */

function Legend({ fixture }: { fixture: Fixture }) {
  const usedTiers = Array.from(new Set(fixture.sections.map((s) => s.tierId)))
    .map((id) => fixture.tiers.find((t) => t.id === id)!)
    .filter(Boolean);

  return (
    <div className="flex flex-wrap items-center gap-x-5 gap-y-2.5">
      {usedTiers.map((t) => (
        <span key={t.id} className="flex items-center gap-2">
          <span
            className="h-3 w-3 rounded-[3px]"
            style={{
              background: withAlpha(tierColor(t.id), 0.18),
              border: `1px solid ${withAlpha(tierColor(t.id), 0.6)}`,
            }}
          />
          <span className="text-[0.78rem] text-muted">{t.name}</span>
        </span>
      ))}
      <span className="h-3 w-px bg-line-2" />
      <span className="flex items-center gap-2">
        <span
          className="h-3 w-3 rounded-[3px]"
          style={{
            background: withAlpha(HELD_COLOR, 0.25),
            border: `1px solid ${withAlpha(HELD_COLOR, 0.6)}`,
          }}
        />
        <span className="text-[0.78rem] text-muted">Held</span>
      </span>
      <span className="flex items-center gap-2">
        <span className="h-3 w-3 rounded-[3px] bg-ink-3 opacity-50" />
        <span className="text-[0.78rem] text-muted">Sold</span>
      </span>
    </div>
  );
}

/* ------------------------------ Seat map --------------------------------- */

export function SeatMap({
  fixture,
  initialTier,
  initialSection,
}: {
  fixture: Fixture;
  initialTier?: string;
  initialSection?: string;
}) {
  const sections = useMemo(() => buildSeatSections(fixture), [fixture]);
  const byId = useMemo(() => {
    const m = new Map<string, GeneratedSeat>();
    sections.forEach((sec) => sec.seats.forEach((s) => m.set(s.id, s)));
    return m;
  }, [sections]);

  const series = getSeries(fixture.seriesId);
  const [selected, setSelected] = useState<string[]>([]);
  const [seconds, setSeconds] = useState(HOLD_SECONDS);
  const [expired, setExpired] = useState(false);
  const [placed, setPlaced] = useState(false);
  const [limitHit, setLimitHit] = useState(false);
  const secondsRef = useRef(HOLD_SECONDS);

  const active = selected.length > 0;

  // Hold countdown: ticks while seats are held, clears the selection at zero.
  // Frozen once an order is placed so the confirmation never decays. The clock
  // is reset to a full hold in the toggle handler when a fresh selection starts.
  // Side effects live in the interval callback, never inside a state updater.
  useEffect(() => {
    if (!active || placed) return;
    const id = setInterval(() => {
      const next = secondsRef.current - 1;
      if (next <= 0) {
        clearInterval(id);
        secondsRef.current = HOLD_SECONDS;
        setSeconds(HOLD_SECONDS);
        setSelected([]);
        setExpired(true);
      } else {
        secondsRef.current = next;
        setSeconds(next);
      }
    }, 1000);
    return () => clearInterval(id);
  }, [active, placed]);

  const selectedSet = useMemo(() => new Set(selected), [selected]);

  const toggle = (id: string) => {
    const seat = byId.get(id);
    if (!seat || seat.state !== "available" || placed) return;
    if (selected.includes(id)) {
      setExpired(false);
      setSelected((prev) => prev.filter((x) => x !== id));
      return;
    }
    if (selected.length >= MAX_SEATS) {
      setLimitHit(true);
      return;
    }
    setLimitHit(false);
    setExpired(false);
    // Starting a fresh selection restarts the hold clock at the full window.
    if (selected.length === 0) {
      secondsRef.current = HOLD_SECONDS;
      setSeconds(HOLD_SECONDS);
    }
    setSelected((prev) => [...prev, id]);
  };

  const selectedSeats = selected
    .map((id) => byId.get(id))
    .filter((s): s is GeneratedSeat => Boolean(s))
    .sort((a, b) => (a.id < b.id ? -1 : 1));
  const subtotal = selectedSeats.reduce((sum, s) => sum + s.price, 0);
  const fee = Math.round(subtotal * 0.05);
  const total = subtotal + fee;

  const isHighlighted = (sectionId: string, tierId: string) =>
    (!!initialSection && sectionId === initialSection) ||
    (!!initialTier && tierId === initialTier);

  const standFor = (side: GeneratedSeat["side"]) =>
    sections.find((s) => s.side === side);

  const renderStand = (side: GeneratedSeat["side"], className?: string) => {
    const sec = standFor(side);
    if (!sec) return null;
    return (
      <Stand
        name={sec.sectionName}
        seats={sec.seats}
        tierId={sec.tierId}
        selectedIds={selectedSet}
        onToggle={toggle}
        highlighted={isHighlighted(sec.sectionId, sec.tierId)}
        className={className}
      />
    );
  };

  return (
    <div className="grid gap-10 lg:grid-cols-12 lg:gap-12">
      {/* Map */}
      <div className="lg:col-span-7 xl:col-span-8">
        <div className="mb-6">
          <Legend fixture={fixture} />
        </div>

        <div
          className="overflow-x-auto pb-2"
          style={{ "--seat": "clamp(0.95rem, 3vw, 1.12rem)" } as CSSProperties}
        >
          <div className="mx-auto flex min-w-[300px] max-w-2xl flex-col items-center gap-3">
            {renderStand("n", "w-full")}
            <div className="flex w-full flex-col items-stretch gap-3 sm:flex-row sm:justify-center">
              {renderStand("w")}
              <Pitch fixture={fixture} />
              {renderStand("e")}
            </div>
            {renderStand("s", "w-full")}
          </div>
        </div>

        <p className="mt-6 text-center text-[0.76rem] text-faint">
          Seat availability is sample data. Pick up to {MAX_SEATS} seats to see
          the live summary update.
        </p>
      </div>

      {/* Summary */}
      <aside className="lg:col-span-5 xl:col-span-4">
        <div className="lg:sticky lg:top-28">
          <div className="rounded-2xl border border-line bg-ink-2 p-6">
            <p className="sr-only" aria-live="polite">
              {placed
                ? `Reserved ${selectedSeats.length} seat${selectedSeats.length === 1 ? "" : "s"}.`
                : expired
                  ? "Your seat hold expired and the selection was cleared."
                  : limitHit
                    ? `Seat limit reached. You can hold up to ${MAX_SEATS} seats.`
                    : selectedSeats.length === 0
                      ? "No seats selected."
                      : `${selectedSeats.length} seat${selectedSeats.length === 1 ? "" : "s"} selected. Total ${formatPrice(total, series.currency)}.`}
            </p>

            <div className="flex items-center justify-between">
              <h2 className="font-display text-lg font-semibold tracking-tight">
                Your selection
              </h2>
              <span
                role="timer"
                aria-label={
                  placed
                    ? "Seats reserved"
                    : active
                      ? `Seats held, ${mmss(seconds)} remaining`
                      : "Seats are held for seven minutes once selected"
                }
                className={cn(
                  "inline-flex items-center gap-1.5 rounded-full border px-2.5 py-1 font-mono text-[0.66rem] tabular-nums tracking-[0.08em]",
                  placed
                    ? "border-accent/50 text-accent"
                    : active && seconds <= 60
                      ? "border-accent/50 text-accent"
                      : "border-line-2 text-muted",
                )}
              >
                {placed ? (
                  <Check className="h-3.5 w-3.5" />
                ) : (
                  <Clock className="h-3.5 w-3.5" />
                )}
                {placed ? "Reserved" : active ? mmss(seconds) : "7:00"}
              </span>
            </div>

            {expired && !active && (
              <p className="mt-4 rounded-lg border border-accent/30 bg-accent/10 px-3 py-2 text-[0.78rem] text-accent">
                Your hold expired. Pick your seats again to restart the timer.
              </p>
            )}
            {limitHit && (
              <p className="mt-4 text-[0.76rem] text-faint">
                That is the {MAX_SEATS} seat limit for a single order.
              </p>
            )}

            {placed ? (
              <div className="py-10 text-center">
                <span className="mx-auto grid h-12 w-12 place-items-center rounded-full bg-accent text-accent-ink">
                  <Check className="h-6 w-6" />
                </span>
                <p className="mt-4 font-display text-xl tracking-tight text-bone">
                  Seats reserved.
                </p>
                <p className="mt-2 text-[0.82rem] text-muted">
                  This is a demonstration checkout, so no payment was taken. Your
                  selection of {selectedSeats.length || "your"} seats would be
                  confirmed here.
                </p>
                <button
                  type="button"
                  onClick={() => {
                    setPlaced(false);
                    setSelected([]);
                  }}
                  className="mt-6 rounded-full border border-line-2 px-5 py-2 text-sm text-bone transition-colors hover:border-bone"
                >
                  Start over
                </button>
              </div>
            ) : selectedSeats.length === 0 ? (
              <div className="py-12 text-center">
                <p className="text-[0.9rem] text-muted">
                  Tap an open seat to begin.
                </p>
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
                      <span className="flex items-center gap-2.5">
                        <span
                          className="h-2.5 w-2.5 rounded-[3px]"
                          style={{ background: tierColor(s.tierId) }}
                        />
                        <span className="text-[0.84rem] text-bone">
                          {s.sectionName}
                          <span className="tnum text-muted"> · {s.label}</span>
                        </span>
                      </span>
                      <span className="flex items-center gap-3">
                        <span className="tnum text-[0.84rem] text-bone">
                          {formatPrice(s.price, series.currency)}
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
                      Subtotal
                      <span className="tnum"> ({selectedSeats.length})</span>
                    </dt>
                    <dd className="tnum text-bone">
                      {formatPrice(subtotal, series.currency)}
                    </dd>
                  </div>
                  <div className="flex justify-between text-muted">
                    <dt>Booking fee</dt>
                    <dd className="tnum text-bone">
                      {formatPrice(fee, series.currency)}
                    </dd>
                  </div>
                  <div className="mt-1 flex items-baseline justify-between border-t border-line pt-3">
                    <dt className="font-display text-base text-bone">Total</dt>
                    <dd className="tnum text-xl text-bone">
                      {formatPrice(total, series.currency)}
                    </dd>
                  </div>
                </dl>

                <button
                  type="button"
                  onClick={() => setPlaced(true)}
                  className="mt-6 inline-flex h-12 w-full items-center justify-center gap-2 rounded-full bg-accent text-[0.92rem] font-medium text-accent-ink transition-[filter] hover:brightness-105"
                >
                  Reserve {selectedSeats.length} seat
                  {selectedSeats.length === 1 ? "" : "s"}
                </button>
                <p className="mt-3 text-center text-[0.72rem] text-faint">
                  Demonstration checkout. No payment is taken.
                </p>
              </>
            )}
          </div>
        </div>
      </aside>
    </div>
  );
}
