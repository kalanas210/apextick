import Link from "next/link";
import type { CSSProperties } from "react";
import type { Fixture } from "@/data/types";
import { fixturesByDate, featuredFixtures, getSeries } from "@/data/events";
import { unsplash } from "@/data/images";
import { formatDate, formatPrice } from "@/lib/format";
import { withAlpha } from "@/lib/color";
import { SectionHeading } from "@/components/ui/section-heading";
import { Stagger, StaggerItem, Reveal } from "@/components/ui/motion";
import { ParallaxImage } from "@/components/ui/parallax-image";
import { Crest } from "@/components/ui/crest";
import { StatusPill } from "@/components/ui/tag";
import { ArrowUpRight, Pin } from "@/components/ui/icons";
import { FixtureCard } from "@/components/fixtures/fixture-card";

function Spotlight({ fixture }: { fixture: Fixture }) {
  const s = getSeries(fixture.seriesId);
  const date = formatDate(fixture.date);
  const from = Math.min(...fixture.tiers.map((t) => t.price));

  return (
    <Reveal>
      <Link
        href={`/events/${fixture.slug}`}
        style={{ "--tint": s.tint } as CSSProperties}
        className="group grid overflow-hidden rounded-2xl border border-line bg-ink-2 transition-colors duration-500 hover:border-tint lg:grid-cols-12"
      >
        <ParallaxImage
          src={unsplash(fixture.image, { w: 1400, q: 80 })}
          alt={`${fixture.home.name} versus ${fixture.away.name}`}
          sizes="(max-width: 1024px) 100vw, 58vw"
          className="aspect-[16/10] lg:col-span-7 lg:aspect-auto lg:min-h-[460px]"
          imgClassName="transition-transform duration-[1100ms] ease-out group-hover:scale-[1.05]"
        >
          <div
            className="absolute inset-0"
            style={{
              background: `linear-gradient(135deg, ${withAlpha(s.tint, 0.16)}, transparent 50%)`,
            }}
          />
          <div className="absolute inset-0 bg-gradient-to-t from-ink-2/90 via-ink-2/10 to-transparent" />
          <div className="absolute inset-x-0 top-0 flex items-center justify-between p-5">
            <StatusPill status={fixture.status} />
            <span className="rounded-full border border-white/15 bg-ink/40 px-3 py-1 font-mono text-[0.6rem] uppercase tracking-[0.18em] text-bone/85 backdrop-blur-sm">
              {s.name}
            </span>
          </div>
          <div className="absolute bottom-5 left-6 flex items-center gap-3">
            <Crest team={fixture.home} size={56} />
            <Crest team={fixture.away} size={56} />
          </div>
        </ParallaxImage>

        <div className="flex flex-col justify-between gap-8 p-7 lg:col-span-5 lg:p-10">
          <div className="flex items-center justify-between">
            <span className="kicker text-tint">{fixture.stage}</span>
            <span className="tnum text-sm text-muted">{date.full}</span>
          </div>

          <div>
            <h3 className="display text-[clamp(2rem,4vw,3.1rem)]">
              {fixture.home.short}{" "}
              <span className="text-faint">v</span> {fixture.away.short}
            </h3>
            <p className="mt-4 max-w-sm text-[0.96rem] leading-relaxed text-muted">
              {fixture.blurb}
            </p>
            <div className="mt-4 flex items-center gap-1.5 text-[0.85rem] text-muted">
              <Pin className="h-3.5 w-3.5" />
              {fixture.stadium}, {fixture.city}
            </div>
          </div>

          <div className="flex items-center justify-between border-t border-line pt-6">
            <div>
              <span className="block text-[0.6rem] uppercase tracking-[0.16em] text-faint">
                From
              </span>
              <span className="tnum text-lg text-bone">
                {formatPrice(from, s.currency)}
              </span>
            </div>
            <span className="inline-flex h-12 items-center gap-2 rounded-full bg-bone px-6 text-sm font-medium text-ink transition-colors duration-300 group-hover:bg-tint group-hover:text-accent-ink">
              Choose seats
              <ArrowUpRight className="h-4 w-4" />
            </span>
          </div>
        </div>
      </Link>
    </Reveal>
  );
}

export function FixturesPreview() {
  const spotlight = featuredFixtures[0] ?? fixturesByDate[0];
  const grid = fixturesByDate.filter((f) => f.id !== spotlight.id).slice(0, 6);

  return (
    <section className="shell py-24 md:py-32">
      <SectionHeading
        index="02"
        kicker="On sale now"
        title="The next big nights"
        description="Fixtures sorted by date, from the group stage openers to the matches a whole city stops for."
        action={{ href: "/events", label: "See all fixtures" }}
      />

      <div className="mt-14 md:mt-16">
        <Spotlight fixture={spotlight} />
      </div>

      <Stagger className="mt-7 grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
        {grid.map((f) => (
          <StaggerItem key={f.id}>
            <FixtureCard fixture={f} className="h-full" />
          </StaggerItem>
        ))}
      </Stagger>
    </section>
  );
}
