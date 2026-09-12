import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { apiGetOrNull } from "@/lib/server-api";
import { formatDate } from "@/lib/format";
import type { EventDetail } from "@/lib/types";
import { Crest } from "@/components/ui/crest";
import { StatusPill } from "@/components/ui/tag";
import { LiveSeatMap } from "@/components/seatmap/live-seat-map";
import { Pin, Clock } from "@/components/ui/icons";

/** The most live page on the site: never served from a build-time snapshot. */
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
  if (!event) return { title: "Select seats" };
  const title =
    event.home && event.away
      ? `${event.home.name} v ${event.away.name}`
      : event.name;
  return {
    title: `Seats, ${title}`,
    description: `Choose your seats for ${title} at ${event.stadium}.`,
  };
}

export default async function SeatsPage({
  params,
  searchParams,
}: {
  params: Promise<{ slug: string }>;
  searchParams: Promise<{ [key: string]: string | string[] | undefined }>;
}) {
  const { slug } = await params;
  const event = await loadEvent(slug);
  if (!event) notFound();

  const sp = await searchParams;
  const tier = typeof sp.tier === "string" ? sp.tier : undefined;
  const section = typeof sp.section === "string" ? sp.section : undefined;

  const series = event.series;
  const date = formatDate(event.date);

  return (
    <div className="shell pb-24 pt-28 md:pt-32">
      <Link
        href={`/events/${event.slug}`}
        className="group inline-flex items-center gap-2 font-mono text-[0.66rem] uppercase tracking-[0.18em] text-muted transition-colors hover:text-bone"
      >
        <span className="transition-transform duration-300 group-hover:-translate-x-0.5">
          &larr;
        </span>
        Back to fixture
      </Link>

      <header className="mt-6 border-t border-line pt-6">
        <div className="flex flex-wrap items-center gap-3">
          <StatusPill status={event.status} />
          {series && (
            <span className="kicker" style={series.tint ? { color: series.tint } : undefined}>
              {series.name}
            </span>
          )}
          {event.stage && <span className="kicker text-bone/55">{event.stage}</span>}
        </div>

        <div className="mt-6 flex flex-col gap-6 sm:flex-row sm:items-end sm:justify-between">
          <div className="flex items-center gap-4">
            {event.home && <Crest team={event.home} size={52} />}
            <h1 className="display text-[clamp(2rem,5vw,3.6rem)]">
              {event.home && event.away ? (
                <>
                  {event.home.short} <span className="text-faint">v</span>{" "}
                  {event.away.short}
                </>
              ) : (
                event.name
              )}
            </h1>
            {event.away && <Crest team={event.away} size={52} />}
          </div>

          <div className="flex flex-wrap items-center gap-x-5 gap-y-1.5 text-[0.85rem] text-muted">
            <span className="tnum">{date.full}</span>
            <span className="hidden h-1 w-1 rounded-full bg-bone/30 sm:block" />
            <span className="tnum flex items-center gap-1.5">
              <Clock className="h-4 w-4" /> {event.time}
            </span>
            <span className="hidden h-1 w-1 rounded-full bg-bone/30 sm:block" />
            <span className="flex items-center gap-1.5">
              <Pin className="h-4 w-4" /> {event.stadium}
            </span>
          </div>
        </div>
      </header>

      {/* The header above is this request's snapshot of the fixture; the map
          below keeps itself current — real availability, real holds, real
          orders, patched live as other buyers pick. */}
      <div className="mt-12">
        <LiveSeatMap slug={event.slug} initialTier={tier} initialSection={section} />
      </div>
    </div>
  );
}
