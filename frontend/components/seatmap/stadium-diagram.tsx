import Link from "next/link";
import type { CSSProperties } from "react";
import type { EventDetail, PriceTier, Section } from "@/lib/types";
import { formatPrice } from "@/lib/format";
import { withAlpha } from "@/lib/color";
import { cn } from "@/lib/cn";
import { bySide } from "./sections";
import { tierColor } from "./tier-colors";

function Stand({
  section,
  event,
  tier,
}: {
  section: Section;
  event: EventDetail;
  tier?: PriceTier;
}) {
  const color = tierColor(section.tierCode);
  return (
    <Link
      href={`/events/${event.slug}/seats?section=${section.code}`}
      style={
        {
          background: withAlpha(color, 0.12),
          borderColor: withAlpha(color, 0.45),
          "--c": color,
        } as CSSProperties
      }
      className="group flex flex-1 flex-col justify-between rounded-lg border p-3.5 transition-colors duration-300 hover:bg-[color:var(--c)]/20"
    >
      <div className="flex items-center gap-1.5">
        <span className="h-2 w-2 rounded-full" style={{ background: color }} />
        <span className="font-mono text-[0.6rem] uppercase tracking-[0.14em] text-bone/80">
          {section.name}
        </span>
      </div>
      <div className="mt-3">
        <div className="tnum text-sm text-bone">
          {tier ? formatPrice(tier.price, event.currency) : "—"}
        </div>
        <div className="font-mono text-[0.58rem] uppercase tracking-[0.12em] text-faint">
          {tier?.name ?? section.tierCode}
        </div>
      </div>
    </Link>
  );
}

/** One side of the ground, however many stands it carries. */
function Side({
  area,
  sections,
  event,
  className,
}: {
  area: string;
  sections: Section[];
  event: EventDetail;
  className?: string;
}) {
  if (sections.length === 0) {
    return null;
  }
  return (
    <div style={{ gridArea: area }} className={cn("flex gap-2.5", className)}>
      {sections.map((s) => (
        <Stand
          key={s.id}
          section={s}
          event={event}
          tier={event.tiers.find((t) => t.id === s.tierId)}
        />
      ))}
    </div>
  );
}

/**
 * Schematic of the bowl. Each stand is a tinted, tappable block arranged around
 * the field of play, and doubles as a quick section selector into the seat map.
 * A side can carry more than one stand, so each side is its own strip rather
 * than a single cell.
 */
export function StadiumDiagram({ event }: { event: EventDetail }) {
  const sides = bySide(event.sections);

  return (
    <div
      className="mx-auto grid w-full max-w-xl gap-2.5"
      style={{
        gridTemplateAreas: `". n ." "w p e" ". s ."`,
        gridTemplateColumns: "1fr 1.5fr 1fr",
      }}
    >
      <Side area="n" sections={sides.n} event={event} />
      <Side area="w" sections={sides.w} event={event} className="flex-col" />
      <Side area="e" sections={sides.e} event={event} className="flex-col" />
      <Side area="s" sections={sides.s} event={event} />

      <div
        style={{ gridArea: "p" }}
        className="relative grid min-h-[120px] place-items-center rounded-lg border border-line bg-ink"
      >
        <div className="absolute inset-4 rounded-[40%] border border-line-2/60" />
        <div className="absolute h-10 w-10 rounded-full border border-line-2/60" />
        <div className="absolute inset-x-4 top-1/2 h-px bg-line-2/60" />
        <div className="relative text-center">
          <div className="font-display text-base font-semibold tracking-tight text-bone">
            {event.home && event.away ? (
              <>
                {event.home.short} <span className="text-faint">v</span> {event.away.short}
              </>
            ) : (
              event.name
            )}
          </div>
          <div className="font-mono text-[0.55rem] uppercase tracking-[0.2em] text-faint">
            Field of play
          </div>
        </div>
      </div>
    </div>
  );
}
