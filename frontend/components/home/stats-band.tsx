import type { EventSummary } from "@/lib/types";
import { Marquee } from "@/components/ui/marquee";
import { Reveal } from "@/components/ui/motion";

/**
 * Counted from the catalog, not written into the page. The band used to claim
 * sixteen fixtures across twelve venues and three series whatever was actually
 * on sale — the one number a visitor can check for themselves by clicking
 * through to the grid.
 */
export function StatsBand({
  events,
  seriesCount,
}: {
  events: EventSummary[];
  seriesCount: number;
}) {
  const venues = Array.from(new Set(events.map((e) => e.stadium).filter(Boolean)));
  const seats = events.reduce((sum, e) => sum + e.availableSeats, 0);

  const pad = (n: number) => String(n).padStart(2, "0");
  const stats = [
    { value: pad(events.length), label: events.length === 1 ? "Fixture on sale" : "Fixtures on sale" },
    { value: pad(venues.length), label: venues.length === 1 ? "Venue" : "Venues" },
    { value: pad(seriesCount), label: seriesCount === 1 ? "Series" : "Series" },
    { value: seats.toLocaleString("en-GB"), label: "Seats available" },
  ];

  return (
    <section className="border-y border-line bg-ink-2/40">
      <div className="shell grid grid-cols-2 gap-x-6 gap-y-10 py-16 md:grid-cols-4 md:gap-x-10 md:py-20">
        {stats.map((s, i) => (
          <Reveal key={s.label} delay={i * 0.06}>
            <div className="border-l border-line pl-5">
              <div className="tnum text-[clamp(2.4rem,5vw,3.6rem)] leading-none text-bone">
                {s.value}
              </div>
              <div className="mt-2 font-mono text-[0.62rem] uppercase tracking-[0.18em] text-faint">
                {s.label}
              </div>
            </div>
          </Reveal>
        ))}
      </div>

      {venues.length > 0 && (
        <div className="border-t border-line py-7">
          <Marquee speed={48}>
            {venues.map((v) => (
              <span key={v} className="flex items-center">
                <span className="px-7 font-display text-[1.4rem] font-medium tracking-tight text-bone/35 transition-colors hover:text-bone md:text-[1.9rem]">
                  {v}
                </span>
                <span className="h-1.5 w-1.5 rounded-full bg-accent/70" />
              </span>
            ))}
          </Marquee>
        </div>
      )}
    </section>
  );
}
