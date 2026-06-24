import Link from "next/link";
import type { Fixture } from "@/data/types";
import { getSeries } from "@/data/events";
import { formatPrice } from "@/lib/format";
import { tierColor } from "@/components/seatmap/tier-colors";
import { Check, ArrowUpRight } from "@/components/ui/icons";

export function TierPanel({ fixture }: { fixture: Fixture }) {
  const s = getSeries(fixture.seriesId);

  return (
    <div className="grid gap-4 sm:grid-cols-2">
      {fixture.tiers.map((t) => {
        const color = tierColor(t.id);
        const soldOut = t.remaining === 0;
        const scarce = t.remaining > 0 && t.remaining < 220;
        return (
          <div
            key={t.id}
            className="flex flex-col rounded-xl border border-line bg-ink-2 p-6"
          >
            <div className="flex items-start justify-between">
              <div className="flex items-center gap-2.5">
                <span
                  className="h-3 w-3 rounded-full"
                  style={{ background: color }}
                />
                <span className="font-display text-[1.15rem] font-semibold tracking-tight">
                  {t.name}
                </span>
              </div>
              <div className="text-right">
                <span className="tnum text-[1.15rem] text-bone">
                  {formatPrice(t.price, s.currency)}
                </span>
                <span className="block text-[0.6rem] uppercase tracking-[0.14em] text-faint">
                  per seat
                </span>
              </div>
            </div>

            <ul className="mt-5 space-y-2.5">
              {t.perks.map((perk) => (
                <li
                  key={perk}
                  className="flex items-center gap-2.5 text-[0.88rem] text-muted"
                >
                  <Check className="h-3.5 w-3.5 shrink-0 text-bone/60" />
                  {perk}
                </li>
              ))}
            </ul>

            <div className="mt-6 flex items-center justify-between border-t border-line pt-5">
              <span
                className={`tnum text-[0.78rem] ${scarce ? "text-accent" : "text-faint"}`}
              >
                {soldOut ? "Waitlist only" : `${t.remaining} remaining`}
              </span>
              {soldOut ? (
                <span className="font-mono text-[0.66rem] uppercase tracking-[0.16em] text-faint">
                  Sold out
                </span>
              ) : (
                <Link
                  href={`/events/${fixture.slug}/seats?tier=${t.id}`}
                  className="group inline-flex items-center gap-1.5 font-mono text-[0.66rem] uppercase tracking-[0.16em] text-bone transition-colors hover:text-accent"
                >
                  Select
                  <ArrowUpRight className="h-3.5 w-3.5 transition-transform duration-300 group-hover:translate-x-0.5 group-hover:-translate-y-0.5" />
                </Link>
              )}
            </div>
          </div>
        );
      })}
    </div>
  );
}
