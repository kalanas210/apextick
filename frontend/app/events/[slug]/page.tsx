import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import type { CSSProperties } from "react";
import {
  fixtures,
  getFixture,
  getSeries,
  fixturesBySeries,
} from "@/data/events";
import { unsplash } from "@/data/images";
import { formatDate, formatPrice } from "@/lib/format";
import { HOLD_MINUTES } from "@/lib/booking-rules";
import { ParallaxImage } from "@/components/ui/parallax-image";
import { Reveal } from "@/components/ui/motion";
import { Button } from "@/components/ui/button";
import { Crest } from "@/components/ui/crest";
import { StatusPill } from "@/components/ui/tag";
import { TierPanel } from "@/components/fixtures/tier-panel";
import { StadiumDiagram } from "@/components/seatmap/stadium-diagram";
import { FixtureCard } from "@/components/fixtures/fixture-card";
import { Pin, Clock, ArrowUpRight } from "@/components/ui/icons";

export function generateStaticParams() {
  return fixtures.map((f) => ({ slug: f.slug }));
}

export async function generateMetadata({
  params,
}: {
  params: Promise<{ slug: string }>;
}): Promise<Metadata> {
  const { slug } = await params;
  const fixture = getFixture(slug);
  if (!fixture) return { title: "Fixture" };
  return {
    title: `${fixture.home.name} v ${fixture.away.name}`,
    description: fixture.blurb,
  };
}

function Fact({ label, value }: { label: string; value: string }) {
  return (
    <div className="border-t border-line py-4">
      <dt className="font-mono text-[0.6rem] uppercase tracking-[0.18em] text-faint">
        {label}
      </dt>
      <dd className="mt-1.5 text-[0.95rem] text-bone">{value}</dd>
    </div>
  );
}

export default async function FixtureDetailPage({
  params,
}: {
  params: Promise<{ slug: string }>;
}) {
  const { slug } = await params;
  const fixture = getFixture(slug);
  if (!fixture) notFound();

  const s = getSeries(fixture.seriesId);
  const date = formatDate(fixture.date);
  const from = Math.min(...fixture.tiers.map((t) => t.price));
  const related = fixturesBySeries(fixture.seriesId)
    .filter((f) => f.id !== fixture.id)
    .slice(0, 3);

  return (
    <div style={{ "--tint": s.tint } as CSSProperties}>
      {/* Header */}
      <header className="relative flex min-h-[78vh] flex-col justify-end overflow-hidden">
        <ParallaxImage
          src={unsplash(fixture.image, { w: 2000, q: 80 })}
          alt={`${fixture.stadium}, ${fixture.city}`}
          amount={6}
          priority
          sizes="100vw"
          className="absolute inset-0"
        >
          <div className="absolute inset-0 bg-gradient-to-t from-ink via-ink/45 to-ink/30" />
          <div className="absolute inset-0 bg-[radial-gradient(120%_80%_at_50%_-10%,transparent_50%,rgba(11,11,12,0.7))]" />
        </ParallaxImage>

        <div className="shell relative z-10 pb-12 pt-32">
          <Link
            href="/events"
            className="group inline-flex items-center gap-2 font-mono text-[0.66rem] uppercase tracking-[0.18em] text-bone/70 transition-colors hover:text-bone"
          >
            <span className="transition-transform duration-300 group-hover:-translate-x-0.5">
              &larr;
            </span>
            All fixtures
          </Link>

          <div className="mt-7 flex flex-wrap items-center gap-3">
            <StatusPill status={fixture.status} />
            <span className="kicker text-tint">{s.name}</span>
            <span className="kicker text-bone/55">{fixture.stage}</span>
          </div>

          <div className="mt-7 flex flex-wrap items-center gap-x-6 gap-y-5">
            <Crest team={fixture.home} size={66} />
            <h1 className="display text-[clamp(2.4rem,7vw,5.5rem)]">
              {fixture.home.name}{" "}
              <span className="text-faint">v</span> {fixture.away.name}
            </h1>
            <Crest team={fixture.away} size={66} />
          </div>

          <div className="mt-7 flex flex-wrap items-center gap-x-6 gap-y-2 text-[0.9rem] text-bone/75">
            <span className="tnum flex items-center gap-2">
              {date.full}
            </span>
            <span className="h-1 w-1 rounded-full bg-bone/30" />
            <span className="tnum flex items-center gap-2">
              <Clock className="h-4 w-4" /> {fixture.time} local
            </span>
            <span className="h-1 w-1 rounded-full bg-bone/30" />
            <span className="flex items-center gap-2">
              <Pin className="h-4 w-4" /> {fixture.stadium}, {fixture.city}
            </span>
          </div>
        </div>
      </header>

      {/* Body */}
      <div className="shell grid gap-12 py-20 md:py-28 lg:grid-cols-12 lg:gap-16">
        <div className="lg:col-span-7">
          <Reveal>
            <p className="max-w-2xl text-[1.3rem] leading-[1.45] tracking-tight text-bone">
              {fixture.blurb}
            </p>
          </Reveal>

          <Reveal>
            <dl className="mt-12 grid grid-cols-2 gap-x-10 gap-y-2 sm:grid-cols-3">
              <Fact label="Date" value={date.full} />
              <Fact label="Kickoff" value={`${fixture.time} local`} />
              <Fact label="Gates" value="2 hours before" />
              <Fact label="Venue" value={fixture.stadium} />
              <Fact label="City" value={`${fixture.city}, ${fixture.country}`} />
              <Fact label="Stage" value={fixture.stage} />
            </dl>
          </Reveal>

          <div className="mt-16">
            <Reveal>
              <div className="flex items-baseline justify-between border-t border-line pt-5">
                <h2 className="font-display text-2xl font-semibold tracking-tight">
                  Choose your tier
                </h2>
                <span className="kicker hidden sm:block">Four ways in</span>
              </div>
            </Reveal>
            <Reveal>
              <div className="mt-8">
                <TierPanel fixture={fixture} />
              </div>
            </Reveal>
          </div>

          <div className="mt-16">
            <Reveal>
              <div className="flex items-baseline justify-between border-t border-line pt-5">
                <h2 className="font-display text-2xl font-semibold tracking-tight">
                  Pick your stand
                </h2>
                <span className="kicker hidden sm:block">Tap to enter</span>
              </div>
            </Reveal>
            <Reveal>
              <div className="mt-10">
                <StadiumDiagram fixture={fixture} />
              </div>
            </Reveal>
          </div>
        </div>

        {/* Sticky booking summary */}
        <aside className="lg:col-span-5">
          <div className="lg:sticky lg:top-28">
            <Reveal>
              <div className="rounded-2xl border border-line bg-ink-2 p-7">
                <div className="flex items-center justify-between">
                  <span className="kicker text-tint">Book this fixture</span>
                  <span
                    className="h-2.5 w-2.5 rounded-full"
                    style={{ background: s.tint }}
                  />
                </div>

                <div className="mt-6 flex items-end justify-between border-b border-line pb-6">
                  <div>
                    <span className="block text-[0.62rem] uppercase tracking-[0.16em] text-faint">
                      Seats from
                    </span>
                    <span className="tnum text-3xl text-bone">
                      {formatPrice(from, s.currency)}
                    </span>
                  </div>
                  <span className="tnum text-right text-[0.82rem] leading-tight text-muted">
                    {date.day} {date.month}
                    <span className="block text-faint">{fixture.time}</span>
                  </span>
                </div>

                <ul className="mt-6 space-y-3 text-[0.88rem] text-muted">
                  <li className="flex items-center gap-2.5">
                    <Pin className="h-4 w-4 text-bone/50" /> {fixture.stadium}
                  </li>
                  <li className="flex items-center gap-2.5">
                    <Clock className="h-4 w-4 text-bone/50" /> Seats held{" "}
                    {HOLD_MINUTES} minutes at checkout
                  </li>
                  <li className="flex items-center gap-2.5">
                    <ArrowUpRight className="h-4 w-4 text-bone/50" /> Instant
                    mobile entry
                  </li>
                </ul>

                <div className="mt-7">
                  <Button
                    href={`/events/${fixture.slug}/seats`}
                    size="lg"
                    arrow
                    className="w-full"
                  >
                    Choose seats
                  </Button>
                </div>
                <p className="mt-4 text-center text-[0.72rem] text-faint">
                  Checkout runs in test mode. Pay with a test card; no real
                  money is charged.
                </p>
              </div>
            </Reveal>
          </div>
        </aside>
      </div>

      {/* Related */}
      {related.length > 0 && (
        <section className="shell border-t border-line py-20 md:py-28">
          <div className="flex items-baseline justify-between">
            <h2 className="font-display text-2xl font-semibold tracking-tight">
              More from {s.shortName}
            </h2>
            <Link
              href={`/events?series=${s.id}`}
              className="group hidden items-center gap-1.5 font-mono text-[0.7rem] uppercase tracking-[0.18em] text-muted transition-colors hover:text-bone sm:inline-flex"
            >
              View series
              <ArrowUpRight className="h-3.5 w-3.5 transition-transform duration-300 group-hover:translate-x-0.5 group-hover:-translate-y-0.5" />
            </Link>
          </div>
          <div className="mt-10 grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
            {related.map((f) => (
              <FixtureCard key={f.id} fixture={f} className="h-full" />
            ))}
          </div>
        </section>
      )}
    </div>
  );
}
