import type { Metadata } from "next";
import { Reveal } from "@/components/ui/motion";
import { EventsExplorer } from "@/components/fixtures/events-explorer";
import { DEFAULT_FILTERS, catalogQuery } from "@/lib/catalog";
import { apiGetOr } from "@/lib/server-api";
import type { EventSummary, PageResponse, Series } from "@/lib/types";

export const metadata: Metadata = {
  title: "Fixtures",
  description:
    "Browse every fixture on sale. Filter by sport, series, and month, then pick your seat.",
};

/** Availability and the schedule itself change under us; never serve a snapshot. */
export const dynamic = "force-dynamic";

const EMPTY_PAGE: PageResponse<EventSummary> = {
  content: [],
  page: 0,
  size: 0,
  totalElements: 0,
  totalPages: 0,
  last: true,
};

export default async function EventsPage({
  searchParams,
}: {
  searchParams: Promise<{ [key: string]: string | string[] | undefined }>;
}) {
  const params = await searchParams;
  const raw = Array.isArray(params.series) ? params.series[0] : params.series;

  // The grid's default view, rendered on the server so the page arrives whole.
  const [events, series] = await Promise.all([
    apiGetOr<PageResponse<EventSummary>>(catalogQuery(DEFAULT_FILTERS), EMPTY_PAGE),
    apiGetOr<Series[]>("/api/series", []),
  ]);

  const initialSeries =
    raw && series.some((s) => s.slug === raw) ? raw : "all";
  const seriesOnSale = new Set(
    events.content.map((e) => e.seriesSlug).filter(Boolean),
  ).size;

  return (
    <div className="shell pb-28 pt-28 md:pt-36">
      <Reveal>
        <header className="border-t border-line pt-5">
          <span className="kicker">The schedule</span>
          <h1 className="display mt-6 max-w-4xl text-[clamp(2.6rem,7vw,6rem)]">
            Every fixture, one grid.
          </h1>
          <p className="mt-6 max-w-lg text-[1rem] leading-relaxed text-muted">
            {events.totalElements === 0 ? (
              <>
                Nothing is on sale at this moment. The grid fills the instant a
                fixture opens, so it is worth a look back.
              </>
            ) : (
              <>
                {events.totalElements} fixture
                {events.totalElements === 1 ? "" : "s"} across {seriesOnSale} series.
                Filter to your sport, your series, or the month you can travel,
                then pick the seat you want.
              </>
            )}
          </p>
        </header>
      </Reveal>

      <div className="mt-14 md:mt-16">
        <EventsExplorer
          initialEvents={events.content}
          initialSeries={initialSeries}
          series={series}
        />
      </div>
    </div>
  );
}
