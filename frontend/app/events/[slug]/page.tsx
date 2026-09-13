import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import type { CSSProperties } from "react";
import { apiGetOr, apiGetOrNull } from "@/lib/server-api";
import { eventImage } from "@/lib/images";
import { formatDate, formatPrice } from "@/lib/format";
import { salesState } from "@/lib/sales-window";
import { HOLD_MINUTES } from "@/lib/booking-rules";
import type { EventDetail, EventSummary, PageResponse } from "@/lib/types";
import { ParallaxImage } from "@/components/ui/parallax-image";
import { Reveal } from "@/components/ui/motion";
import { Button } from "@/components/ui/button";
import { Crest } from "@/components/ui/crest";
import { StatusPill } from "@/components/ui/tag";
import { TierPanel } from "@/components/fixtures/tier-panel";
import { StadiumDiagram } from "@/components/seatmap/stadium-diagram";
import { FixtureCard } from "@/components/fixtures/fixture-card";
import { Pin, Clock, ArrowUpRight } from "@/components/ui/icons";
import { SiteModeNote } from "@/components/site/site-mode";

/**
 * Read live: an operator can publish, re-price or pull this fixture at any
 * moment, and a page built at deploy time would keep selling the old one.
 */
export const dynamic = "force-dynamic";

async function loadEvent(slug: string): Promise<EventDetail | null> {
  return apiGetOrNull<EventDetail>(`/api/events/${encodeURIComponent(slug)}`);
}

export async function generateMetadata({
  params,
}: {
  params: Promise<{ slug: string }>;
}): Promise<Metadata> {
  const { slug } = await params;
  const event = await loadEvent(slug);
  if (!event) return { title: "Fixture" };
  return {
    title:
      event.home && event.away
        ? `${event.home.name} v ${event.away.name}`
        : event.name,
    description: event.blurb ?? `${event.name} at ${event.stadium}.`,
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
  const event = await loadEvent(slug);
  if (!event) notFound();

  const series = event.series;
  const date = formatDate(event.date);
  const sales = salesState(event);

  // Other fixtures in the same series. Decorative, so a failure here must not
  // take the page down with it.
  const related = series
    ? (
        await apiGetOr<PageResponse<EventSummary>>(
          `/api/events?series=${encodeURIComponent(series.slug)}&size=4`,
          { content: [], page: 0, size: 0, totalElements: 0, totalPages: 0, last: true },
        )
      ).content
        .filter((e) => e.id !== event.id)
        .slice(0, 3)
    : [];

  return (
    <div style={series?.tint ? ({ "--tint": series.tint } as CSSProperties) : undefined}>
      {/* Header */}
      <header className="relative flex min-h-[78vh] flex-col justify-end overflow-hidden">
        <ParallaxImage
          src={eventImage(event, { w: 2000, q: 80 })}
          alt={[event.stadium, event.city].filter(Boolean).join(", ")}
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
            <StatusPill status={event.status} />
            {series && <span className="kicker text-tint">{series.name}</span>}
            {event.stage && <span className="kicker text-bone/55">{event.stage}</span>}
          </div>

          <div className="mt-7 flex flex-wrap items-center gap-x-6 gap-y-5">
            {event.home && <Crest team={event.home} size={66} />}
            <h1 className="display text-[clamp(2.4rem,7vw,5.5rem)]">
              {event.home && event.away ? (
                <>
                  {event.home.name} <span className="text-faint">v</span>{" "}
                  {event.away.name}
                </>
              ) : (
                event.name
              )}
            </h1>
            {event.away && <Crest team={event.away} size={66} />}
          </div>

          <div className="mt-7 flex flex-wrap items-center gap-x-6 gap-y-2 text-[0.9rem] text-bone/75">
            <span className="tnum flex items-center gap-2">{date.full}</span>
            <span className="h-1 w-1 rounded-full bg-bone/30" />
            <span className="tnum flex items-center gap-2">
              <Clock className="h-4 w-4" /> {event.time} local
            </span>
            <span className="h-1 w-1 rounded-full bg-bone/30" />
            <span className="flex items-center gap-2">
              <Pin className="h-4 w-4" />{" "}
              {[event.stadium, event.city].filter(Boolean).join(", ")}
            </span>
          </div>
        </div>
      </header>

      {/* Body */}
      <div className="shell grid gap-12 py-20 md:py-28 lg:grid-cols-12 lg:gap-16">
        <div className="lg:col-span-7">
          {event.blurb && (
            <Reveal>
              <p className="max-w-2xl text-[1.3rem] leading-[1.45] tracking-tight text-bone">
                {event.blurb}
              </p>
            </Reveal>
          )}

          <Reveal>
            <dl className="mt-12 grid grid-cols-2 gap-x-10 gap-y-2 sm:grid-cols-3">
              <Fact label="Date" value={date.full} />
              <Fact label="Kickoff" value={`${event.time} local`} />
              <Fact label="Gates" value="2 hours before" />
              <Fact label="Venue" value={event.stadium} />
              <Fact
                label="City"
                value={[event.city, event.country].filter(Boolean).join(", ") || "—"}
              />
              <Fact label="Stage" value={event.stage ?? "—"} />
            </dl>
          </Reveal>

          {event.tiers.length > 0 && (
            <div className="mt-16">
              <Reveal>
                <div className="flex items-baseline justify-between border-t border-line pt-5">
                  <h2 className="font-display text-2xl font-semibold tracking-tight">
                    Choose your tier
                  </h2>
                  <span className="kicker hidden sm:block">
                    {event.tiers.length} way{event.tiers.length === 1 ? "" : "s"} in
                  </span>
                </div>
              </Reveal>
              <Reveal>
                <div className="mt-8">
                  <TierPanel event={event} />
                </div>
              </Reveal>
            </div>
          )}

          {event.sections.length > 0 && (
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
                  <StadiumDiagram event={event} />
                </div>
              </Reveal>
            </div>
          )}
        </div>

        {/* Sticky booking summary */}
        <aside className="lg:col-span-5">
          <div className="lg:sticky lg:top-28">
            <Reveal>
              <div className="rounded-2xl border border-line bg-ink-2 p-7">
                <div className="flex items-center justify-between">
                  <span className="kicker text-tint">Book this fixture</span>
                  {series?.tint && (
                    <span
                      className="h-2.5 w-2.5 rounded-full"
                      style={{ background: series.tint }}
                    />
                  )}
                </div>

                <div className="mt-6 flex items-end justify-between border-b border-line pb-6">
                  <div>
                    <span className="block text-[0.62rem] uppercase tracking-[0.16em] text-faint">
                      {event.fromPrice === null ? "Seats" : "Seats from"}
                    </span>
                    <span className="tnum text-3xl text-bone">
                      {event.fromPrice === null
                        ? "Not yet priced"
                        : formatPrice(event.fromPrice, event.currency)}
                    </span>
                  </div>
                  <span className="tnum text-right text-[0.82rem] leading-tight text-muted">
                    {date.day} {date.month}
                    <span className="block text-faint">{event.time}</span>
                  </span>
                </div>

                <ul className="mt-6 space-y-3 text-[0.88rem] text-muted">
                  <li className="flex items-center gap-2.5">
                    <Pin className="h-4 w-4 text-bone/50" /> {event.stadium}
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

                {/* The API refuses a hold outside the sales window anyway; saying so
                    here beats sending the buyer to a map that will not let them in. */}
                <div className="mt-7">
                  <Button
                    href={`/events/${event.slug}/seats`}
                    size="lg"
                    arrow
                    className="w-full"
                  >
                    {sales.open ? "Choose seats" : "View the seat map"}
                  </Button>
                </div>
                {sales.open ? (
                  <SiteModeNote
                    variant="checkout"
                    className="mt-4 text-center text-[0.72rem] text-faint"
                  />
                ) : (
                  <p className="mt-4 text-center text-[0.72rem] text-faint">
                    <span className="text-bone/80">{sales.title}.</span>{" "}
                    {sales.detail}
                  </p>
                )}
              </div>
            </Reveal>
          </div>
        </aside>
      </div>

      {/* Related */}
      {related.length > 0 && series && (
        <section className="shell border-t border-line py-20 md:py-28">
          <div className="flex items-baseline justify-between">
            <h2 className="font-display text-2xl font-semibold tracking-tight">
              More from {series.shortName ?? series.name}
            </h2>
            <Link
              href={`/events?series=${series.slug}`}
              className="group hidden items-center gap-1.5 font-mono text-[0.7rem] uppercase tracking-[0.18em] text-muted transition-colors hover:text-bone sm:inline-flex"
            >
              View series
              <ArrowUpRight className="h-3.5 w-3.5 transition-transform duration-300 group-hover:translate-x-0.5 group-hover:-translate-y-0.5" />
            </Link>
          </div>
          <div className="mt-10 grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
            {related.map((e) => (
              <FixtureCard key={e.id} event={e} series={series} className="h-full" />
            ))}
          </div>
        </section>
      )}
    </div>
  );
}
