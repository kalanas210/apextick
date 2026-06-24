import Link from "next/link";
import type { CSSProperties } from "react";
import type { Fixture } from "@/data/types";
import { formatPrice } from "@/lib/format";
import { getSeries } from "@/data/events";
import { withAlpha } from "@/lib/color";
import { tierColor } from "./tier-colors";

const AREA: Record<string, string> = { n: "n", s: "s", e: "e", w: "w" };

/**
 * Schematic of the bowl. Each stand is a tinted, tappable block arranged around
 * the field of play, and doubles as a quick section selector into the seat map.
 */
export function StadiumDiagram({ fixture }: { fixture: Fixture }) {
  const s = getSeries(fixture.seriesId);

  return (
    <div
      className="mx-auto grid w-full max-w-xl gap-2.5"
      style={{
        gridTemplateAreas: `". n ." "w p e" ". s ."`,
        gridTemplateColumns: "1fr 1.5fr 1fr",
      }}
    >
      {fixture.sections.map((section) => {
        const tier = fixture.tiers.find((t) => t.id === section.tierId)!;
        const color = tierColor(section.tierId);
        return (
          <Link
            key={section.id}
            href={`/events/${fixture.slug}/seats?section=${section.id}`}
            style={
              {
                gridArea: AREA[section.side],
                background: withAlpha(color, 0.12),
                borderColor: withAlpha(color, 0.45),
                "--c": color,
              } as CSSProperties
            }
            className="group flex flex-col justify-between rounded-lg border p-3.5 transition-colors duration-300 hover:bg-[color:var(--c)]/20"
          >
            <div className="flex items-center gap-1.5">
              <span
                className="h-2 w-2 rounded-full"
                style={{ background: color }}
              />
              <span className="font-mono text-[0.6rem] uppercase tracking-[0.14em] text-bone/80">
                {section.name}
              </span>
            </div>
            <div className="mt-3">
              <div className="tnum text-sm text-bone">
                {formatPrice(tier.price, s.currency)}
              </div>
              <div className="font-mono text-[0.58rem] uppercase tracking-[0.12em] text-faint">
                {tier.name}
              </div>
            </div>
          </Link>
        );
      })}

      <div
        style={{ gridArea: "p" }}
        className="relative grid min-h-[120px] place-items-center rounded-lg border border-line bg-ink"
      >
        <div className="absolute inset-4 rounded-[40%] border border-line-2/60" />
        <div className="absolute h-10 w-10 rounded-full border border-line-2/60" />
        <div className="absolute inset-x-4 top-1/2 h-px bg-line-2/60" />
        <div className="relative text-center">
          <div className="font-display text-base font-semibold tracking-tight text-bone">
            {fixture.home.short} <span className="text-faint">v</span>{" "}
            {fixture.away.short}
          </div>
          <div className="font-mono text-[0.55rem] uppercase tracking-[0.2em] text-faint">
            Field of play
          </div>
        </div>
      </div>
    </div>
  );
}
