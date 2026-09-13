import { Hero } from "@/components/home/hero";
import { SeriesShowcase } from "@/components/home/series-showcase";
import { FixturesPreview } from "@/components/home/fixtures-preview";
import { StatsBand } from "@/components/home/stats-band";
import { Experience } from "@/components/home/experience";
import { ClosingCta } from "@/components/home/closing-cta";
import { DEFAULT_FILTERS, catalogQuery } from "@/lib/catalog";
import { apiGetOr, apiGetSafe } from "@/lib/server-api";
import type { EventSummary, PageResponse, Series } from "@/lib/types";

/** Everything below the fold is the live catalog, so nothing here is prebuilt. */
export const dynamic = "force-dynamic";

export default async function HomePage() {
  // One read of the catalog feeds the whole page: the hero's counts, the series
  // bands, the fixtures rail and the stats. A dead API costs the sections their
  // content, never the page itself.
  const [events, series] = await Promise.all([
    apiGetSafe<PageResponse<EventSummary>>(catalogQuery(DEFAULT_FILTERS)),
    apiGetOr<Series[]>("/api/series", []),
  ]);

  const onSale = events.data?.content ?? [];
  const fixturesBySeries = onSale.reduce<Record<string, number>>((counts, event) => {
    if (event.seriesSlug) {
      counts[event.seriesSlug] = (counts[event.seriesSlug] ?? 0) + 1;
    }
    return counts;
  }, {});

  return (
    <>
      <Hero series={series} fixturesBySeries={fixturesBySeries} />
      <SeriesShowcase series={series} fixturesBySeries={fixturesBySeries} />
      <FixturesPreview events={onSale} series={series} />
      {/* Counting from a catalog we could not read would announce a sold-out
          season as an empty one. */}
      {!events.unavailable && (
        <StatsBand events={onSale} seriesCount={Object.keys(fixturesBySeries).length} />
      )}
      <Experience />
      <ClosingCta />
    </>
  );
}
