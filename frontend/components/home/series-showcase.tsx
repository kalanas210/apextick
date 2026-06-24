import type { CSSProperties } from "react";
import type { Series } from "@/data/types";
import { seriesList, fixturesBySeries } from "@/data/events";
import { unsplash } from "@/data/images";
import { withAlpha } from "@/lib/color";
import { cn } from "@/lib/cn";
import { SectionHeading } from "@/components/ui/section-heading";
import { Reveal } from "@/components/ui/motion";
import { Button } from "@/components/ui/button";
import { ParallaxImage } from "@/components/ui/parallax-image";

function Stat({ value, label }: { value: string; label: string }) {
  return (
    <div>
      <div className="tnum text-[1.6rem] leading-none text-bone">{value}</div>
      <div className="mt-1.5 font-mono text-[0.6rem] uppercase tracking-[0.16em] text-faint">
        {label}
      </div>
    </div>
  );
}

function SeriesBand({
  series,
  index,
  reversed,
}: {
  series: Series;
  index: number;
  reversed: boolean;
}) {
  const count = fixturesBySeries(series.id).length;
  return (
    <div
      style={{ "--tint": series.tint } as CSSProperties}
      className="grid gap-8 lg:grid-cols-12 lg:items-center lg:gap-12"
    >
      <Reveal
        className={cn("lg:col-span-7", reversed && "lg:order-2")}
        y={32}
      >
        <ParallaxImage
          src={unsplash(series.image, { w: 1500, q: 80 })}
          alt={`${series.name} atmosphere`}
          sizes="(max-width: 1024px) 100vw, 58vw"
          className="aspect-[16/10] rounded-2xl border border-line"
        >
          <div
            className="absolute inset-0"
            style={{
              background: `linear-gradient(140deg, ${withAlpha(series.tint, 0.18)}, transparent 55%)`,
            }}
          />
          <div className="absolute inset-0 bg-gradient-to-t from-ink/70 via-transparent to-transparent" />
          <div className="absolute left-5 top-5">
            <span className="rounded-full border border-white/15 bg-ink/40 px-3 py-1 font-mono text-[0.6rem] uppercase tracking-[0.18em] text-bone/85 backdrop-blur-sm">
              {series.sport}
            </span>
          </div>
          <div className="tnum absolute bottom-5 left-6 text-[0.72rem] uppercase tracking-[0.2em] text-bone/75">
            {series.cities.join("  /  ")}
          </div>
        </ParallaxImage>
      </Reveal>

      <div className={cn("lg:col-span-5", reversed && "lg:order-1")}>
        <Reveal>
          <div className="flex items-center gap-3">
            <span className="tnum text-sm text-faint">
              0{index + 1}
            </span>
            <span className="h-px w-6 bg-line-2" />
            <span className="kicker text-tint">{series.kicker}</span>
          </div>
          <h3 className="display mt-5 text-[clamp(2rem,4.6vw,3.6rem)]">
            {series.name}
          </h3>
          <p className="mt-5 max-w-md text-[0.98rem] leading-relaxed text-muted">
            {series.story}
          </p>

          <div className="mt-8 flex items-center gap-9 border-t border-line pt-6">
            <Stat value={series.scale} label="Scale" />
            <Stat value={String(count)} label="Fixtures listed" />
            <Stat value={String(series.cities.length)} label="Host cities" />
          </div>

          <div className="mt-8">
            <Button
              href={`/events?series=${series.id}`}
              variant="outline"
              arrow
            >
              Explore series
            </Button>
          </div>
        </Reveal>
      </div>
    </div>
  );
}

export function SeriesShowcase() {
  return (
    <section id="series" className="shell scroll-mt-24 py-24 md:py-32">
      <SectionHeading
        index="01"
        kicker="Four worlds"
        title={
          <>
            Four series.{" "}
            <span className="text-muted">One way in.</span>
          </>
        }
        description="Two world cups, a franchise juggernaut, and the oldest league in the modern game. Each carries its own color. All of them live here."
        action={{ href: "/events", label: "All fixtures" }}
      />

      <div className="mt-16 space-y-20 md:mt-24 md:space-y-28">
        {seriesList.map((s, i) => (
          <SeriesBand key={s.id} series={s} index={i} reversed={i % 2 === 1} />
        ))}
      </div>
    </section>
  );
}
