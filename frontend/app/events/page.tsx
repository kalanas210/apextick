import type { Metadata } from "next";
import type { SeriesId } from "@/data/types";
import { series as seriesMap } from "@/data/events";
import { Reveal } from "@/components/ui/motion";
import { EventsExplorer } from "@/components/fixtures/events-explorer";

export const metadata: Metadata = {
  title: "Fixtures",
  description:
    "Browse every fixture across the ICC T20 World Cup 2026, the Indian Premier League, and the Premier League. Filter by sport, series, and month.",
};

function isSeriesId(value: string | undefined): value is SeriesId {
  return value !== undefined && value in seriesMap;
}

export default async function EventsPage({
  searchParams,
}: {
  searchParams: Promise<{ [key: string]: string | string[] | undefined }>;
}) {
  const params = await searchParams;
  const raw = Array.isArray(params.series) ? params.series[0] : params.series;
  const initialSeries = isSeriesId(raw) ? raw : "all";

  return (
    <div className="shell pb-28 pt-28 md:pt-36">
      <Reveal>
        <header className="border-t border-line pt-5">
          <span className="kicker">The schedule</span>
          <h1 className="display mt-6 max-w-4xl text-[clamp(2.6rem,7vw,6rem)]">
            Every fixture, one grid.
          </h1>
          <p className="mt-6 max-w-lg text-[1rem] leading-relaxed text-muted">
            Sixteen marquee matches across three series. Filter to your sport,
            your series, or the month you can travel, then pick the seat you
            want.
          </p>
        </header>
      </Reveal>

      <div className="mt-14 md:mt-16">
        <EventsExplorer initialSeries={initialSeries} />
      </div>
    </div>
  );
}
