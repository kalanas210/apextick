import { fixtures } from "@/data/events";
import { Marquee } from "@/components/ui/marquee";
import { Reveal } from "@/components/ui/motion";

const venues = Array.from(new Set(fixtures.map((f) => f.stadium)));

const STATS = [
  { value: "16", label: "Marquee fixtures" },
  { value: "12", label: "Iconic venues" },
  { value: "03", label: "Flagship series" },
  { value: "100%", label: "Seat-level views" },
];

export function StatsBand() {
  return (
    <section className="border-y border-line bg-ink-2/40">
      <div className="shell grid grid-cols-2 gap-x-6 gap-y-10 py-16 md:grid-cols-4 md:gap-x-10 md:py-20">
        {STATS.map((s, i) => (
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
    </section>
  );
}
