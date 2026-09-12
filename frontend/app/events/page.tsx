import type { Metadata } from "next";
import { Reveal } from "@/components/ui/motion";
import { Button } from "@/components/ui/button";
import { EventsExplorer } from "@/components/fixtures/events-explorer";
import { DEFAULT_FILTERS, catalogQuery } from "@/lib/catalog";
import { apiGetOr, apiGetSafe } from "@/lib/server-api";
import type { EventSummary, PageResponse, Series } from "@/lib/types";

export const metadata: Metadata = {
  title: "Fixtures",
  description:
    "Browse every fixture on sale. Filter by sport, series, and month, then pick your seat.",
};

/** Availability and the schedule itself change under us; never serve a snapshot. */
export const dynamic = "force-dynamic";

export default async function EventsPage({
  searchParams,
}: {
  searchParams: Promise<{ [key: string]: string | string[] | undefined }>;
}) {
  const params = await searchParams;
  const raw = Array.isArray(params.series) ? params.series[0] : params.series;

  // The grid's default view, rendered on the server so the page arrives whole.
  const [events, series] = await Promise.all([
    apiGetSafe<PageResponse<EventSummary>>(catalogQuery(DEFAULT_FILTERS)),
    apiGetOr<Series[]>("/api/series", []),
  ]);

  const page = events.data;
  const initialSeries = raw && series.some((s) => s.slug === raw) ? raw : "all";
  const seriesOnSale = new Set(
    (page?.content ?? []).map((e) => e.seriesSlug).filter(Boolean),
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
            {/* An unreachable catalog is not an empty one, and must not read like one. */}
            {events.unavailable ? (
              <>
                The schedule is not loading right now. This is us, not you — the
                fixtures are still there.
              </>
            ) : page && page.totalElements > 0 ? (
              <>
                {page.totalElements} fixture
                {page.totalElements === 1 ? "" : "s"} across {seriesOnSale} series.
                Filter to your sport, your series, or the month you can travel,
                then pick the seat you want.
              </>
            ) : (
              <>
                Nothing is on sale at this moment. The grid fills the instant a
                fixture opens, so it is worth a look back.
              </>
            )}
          </p>
        </header>
      </Reveal>

      {events.unavailable ? (
        <div className="mt-16 flex flex-col items-center gap-5 rounded-2xl border border-line bg-ink-2 py-24 text-center">
          <p className="font-display text-2xl text-bone">We cannot reach the box office.</p>
          <p className="max-w-sm text-sm text-muted">
            The booking service did not answer. Nothing you have booked is
            affected.
          </p>
          <Button href="/events" variant="outline">
            Try again
          </Button>
        </div>
      ) : (
        <div className="mt-14 md:mt-16">
          <EventsExplorer
            initialEvents={page?.content ?? []}
            initialSeries={initialSeries}
            series={series}
          />
        </div>
      )}
    </div>
  );
}
