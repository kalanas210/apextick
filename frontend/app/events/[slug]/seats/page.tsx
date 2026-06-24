import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { fixtures, getFixture, getSeries } from "@/data/events";
import { formatDate } from "@/lib/format";
import { Crest } from "@/components/ui/crest";
import { StatusPill } from "@/components/ui/tag";
import { SeatMap } from "@/components/seatmap/seat-map";
import { Pin, Clock } from "@/components/ui/icons";

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
  if (!fixture) return { title: "Select seats" };
  return {
    title: `Seats, ${fixture.home.name} v ${fixture.away.name}`,
    description: `Choose your seats for ${fixture.home.name} versus ${fixture.away.name} at ${fixture.stadium}.`,
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
  const fixture = getFixture(slug);
  if (!fixture) notFound();

  const sp = await searchParams;
  const tier = typeof sp.tier === "string" ? sp.tier : undefined;
  const section = typeof sp.section === "string" ? sp.section : undefined;

  const s = getSeries(fixture.seriesId);
  const date = formatDate(fixture.date);

  return (
    <div className="shell pb-24 pt-28 md:pt-32">
      <Link
        href={`/events/${fixture.slug}`}
        className="group inline-flex items-center gap-2 font-mono text-[0.66rem] uppercase tracking-[0.18em] text-muted transition-colors hover:text-bone"
      >
        <span className="transition-transform duration-300 group-hover:-translate-x-0.5">
          &larr;
        </span>
        Back to fixture
      </Link>

      <header className="mt-6 border-t border-line pt-6">
        <div className="flex flex-wrap items-center gap-3">
          <StatusPill status={fixture.status} />
          <span className="kicker" style={{ color: s.tint }}>
            {s.name}
          </span>
          <span className="kicker text-bone/55">{fixture.stage}</span>
        </div>

        <div className="mt-6 flex flex-col gap-6 sm:flex-row sm:items-end sm:justify-between">
          <div className="flex items-center gap-4">
            <Crest team={fixture.home} size={52} />
            <h1 className="display text-[clamp(2rem,5vw,3.6rem)]">
              {fixture.home.short} <span className="text-faint">v</span>{" "}
              {fixture.away.short}
            </h1>
            <Crest team={fixture.away} size={52} />
          </div>

          <div className="flex flex-wrap items-center gap-x-5 gap-y-1.5 text-[0.85rem] text-muted">
            <span className="tnum">{date.full}</span>
            <span className="hidden h-1 w-1 rounded-full bg-bone/30 sm:block" />
            <span className="tnum flex items-center gap-1.5">
              <Clock className="h-4 w-4" /> {fixture.time}
            </span>
            <span className="hidden h-1 w-1 rounded-full bg-bone/30 sm:block" />
            <span className="flex items-center gap-1.5">
              <Pin className="h-4 w-4" /> {fixture.stadium}
            </span>
          </div>
        </div>
      </header>

      <div className="mt-12">
        <SeatMap
          fixture={fixture}
          initialTier={tier}
          initialSection={section}
        />
      </div>
    </div>
  );
}
