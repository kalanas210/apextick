"use client";

import type { CSSProperties } from "react";
import { withAlpha } from "@/lib/color";
import { cn } from "@/lib/cn";
import { tierColor, HELD_COLOR } from "./tier-colors";

/**
 * Shared presentation for both seat maps — the static showcase (`seat-map.tsx`,
 * driven by the sample fixtures) and the live one (`live-seat-map.tsx`, driven
 * by the booking API). Both speak this minimal seat shape so the two never
 * drift apart visually.
 */
export interface MapSeat {
  id: string;
  label: string;
  col: number;
  sectionName: string;
  tierId: string;
  tierName: string;
  state: "available" | "held" | "sold";
  price: number;
}

export function mmss(total: number) {
  const m = Math.floor(Math.max(total, 0) / 60);
  const s = Math.max(total, 0) % 60;
  return `${m}:${s.toString().padStart(2, "0")}`;
}

/* --------------------------------- Seat ---------------------------------- */

export function Seat({
  seat,
  selected,
  onToggle,
  locked = false,
}: {
  seat: MapSeat;
  selected: boolean;
  onToggle: (id: string) => void;
  /** The whole map is closed for sales — the seat still reads, it just cannot be picked. */
  locked?: boolean;
}) {
  const color = tierColor(seat.tierId);
  const pickable = seat.state === "available" && !locked;
  const disabled = !pickable;

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
          ? `${locked ? "not on sale" : "available"}, ${seat.tierName}`
          : seat.state
      }`}
      title={`${seat.sectionName} ${seat.label} · ${seat.tierName}`}
      onClick={() => onToggle(seat.id)}
      style={style}
      className={cn(
        "rounded-[3px] border transition-transform duration-150",
        seat.state === "sold" && "opacity-40",
        seat.state === "held" && "animate-pulse cursor-not-allowed",
        locked && seat.state === "available" && "cursor-not-allowed opacity-70",
        pickable && "cursor-pointer hover:scale-[1.18] hover:brightness-125",
        selected && "scale-[1.12] ring-2 ring-bone ring-offset-1 ring-offset-ink",
      )}
    />
  );
}

/* -------------------------------- Stand ---------------------------------- */

export function Stand({
  name,
  seats,
  tierId,
  selectedIds,
  onToggle,
  highlighted,
  className,
  locked = false,
}: {
  name: string;
  seats: MapSeat[];
  tierId: string;
  selectedIds: Set<string>;
  onToggle: (id: string) => void;
  highlighted: boolean;
  className?: string;
  /** Sales are not open for this event; the stand reads but does not respond. */
  locked?: boolean;
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
          <span className="h-2 w-2 rounded-full" style={{ background: color }} />
          <span className="font-mono text-[0.58rem] uppercase tracking-[0.14em] text-bone/80">
            {name}
          </span>
        </span>
        <span className="tnum text-[0.6rem] text-faint">
          {locked ? `${available} unsold` : `${available} open`}
        </span>
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
            locked={locked}
          />
        ))}
      </div>
    </div>
  );
}

/* -------------------------------- Pitch ---------------------------------- */

export function Pitch({ home, away }: { home: string; away: string }) {
  return (
    <div className="relative grid min-h-[110px] min-w-[140px] flex-1 place-items-center rounded-xl border border-line bg-ink">
      <div className="absolute inset-5 rounded-[36%] border border-line-2/50" />
      <div className="absolute h-12 w-12 rounded-full border border-line-2/50" />
      <div className="absolute inset-x-5 top-1/2 h-px bg-line-2/50" />
      <div className="relative text-center">
        <div className="font-display text-sm font-semibold tracking-tight text-bone">
          {home} <span className="text-faint">v</span> {away}
        </div>
        <div className="font-mono text-[0.5rem] uppercase tracking-[0.2em] text-faint">
          Field of play
        </div>
      </div>
    </div>
  );
}

/* ------------------------------- Legend ---------------------------------- */

export function Legend({
  tiers,
}: {
  tiers: { id: string; name: string }[];
}) {
  return (
    <div className="flex flex-wrap items-center gap-x-5 gap-y-2.5">
      {tiers.map((t) => (
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
