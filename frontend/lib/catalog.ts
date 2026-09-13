import type { EventSummary } from './types';

/** The storefront's filter state. "all"/"any" mean the filter is not applied. */
export interface CatalogFilters {
    sport: 'all' | 'cricket' | 'football';
    /** A series *slug*, as the API and the URL both use. */
    series: string;
    /** A month as `YYYY-MM`, or "all". */
    month: string;
    sort: 'soonest' | 'price';
}

export const DEFAULT_FILTERS: CatalogFilters = {
    sport: 'all',
    series: 'all',
    month: 'all',
    sort: 'soonest',
};

/** The most the public list will return in one page; the API caps it at 100. */
export const CATALOG_PAGE_SIZE = 100;

export function hasFilters(f: CatalogFilters): boolean {
    return (
        f.sport !== DEFAULT_FILTERS.sport ||
        f.series !== DEFAULT_FILTERS.series ||
        f.month !== DEFAULT_FILTERS.month ||
        f.sort !== DEFAULT_FILTERS.sort
    );
}

/**
 * Filter state as the catalog endpoint's query string. The API owns every one of
 * these filters — `month` expands to a from/to range server-side, and `sort=price`
 * orders by the cheapest tier — so the grid never has to hold the whole catalog
 * in the browser to answer a question about it.
 */
export function catalogQuery(f: CatalogFilters, size = CATALOG_PAGE_SIZE): string {
    const params = new URLSearchParams({ size: String(size), sort: f.sort });
    if (f.sport !== 'all') params.set('sport', f.sport);
    if (f.series !== 'all') params.set('series', f.series);
    if (f.month !== 'all') params.set('month', f.month);
    return `/api/events?${params.toString()}`;
}

/**
 * The months the catalog actually has fixtures in, ascending, as `YYYY-MM`.
 * Taken from the event's own local date rather than the UTC instant, so a 00:30
 * kickoff lists under the night it belongs to.
 */
export function monthsOf(events: EventSummary[]): string[] {
    return Array.from(new Set(events.map((e) => e.date.slice(0, 7)))).sort();
}
