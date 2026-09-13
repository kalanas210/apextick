import { describe, expect, it } from 'vitest';
import {
    CATALOG_PAGE_SIZE,
    DEFAULT_FILTERS,
    catalogQuery,
    hasFilters,
    monthsOf,
} from './catalog';
import type { EventSummary } from './types';

function event(date: string): EventSummary {
    return { date } as EventSummary;
}

describe('catalogQuery', () => {
    it('asks for the whole first page in kickoff order by default', () => {
        expect(catalogQuery(DEFAULT_FILTERS)).toBe(
            `/api/events?size=${CATALOG_PAGE_SIZE}&sort=soonest`,
        );
    });

    it('leaves "all" out rather than sending it as a filter value', () => {
        const query = catalogQuery({ ...DEFAULT_FILTERS, sport: 'all', series: 'all' });
        expect(query).not.toContain('sport=');
        expect(query).not.toContain('series=');
    });

    it('passes each chosen filter through under the name the API expects', () => {
        const query = catalogQuery({
            sport: 'cricket',
            series: 'icc-t20-2026',
            month: '2026-02',
            sort: 'price',
        });
        const params = new URLSearchParams(query.split('?')[1]);
        expect(params.get('sport')).toBe('cricket');
        expect(params.get('series')).toBe('icc-t20-2026');
        expect(params.get('month')).toBe('2026-02');
        expect(params.get('sort')).toBe('price');
    });
});

describe('hasFilters', () => {
    it('is false for the default view, so the server-rendered grid is reused', () => {
        expect(hasFilters(DEFAULT_FILTERS)).toBe(false);
    });

    it('counts a sort as a filter — the default grid is not in price order', () => {
        expect(hasFilters({ ...DEFAULT_FILTERS, sort: 'price' })).toBe(true);
    });
});

describe('monthsOf', () => {
    it('lists each month once, ascending', () => {
        const months = monthsOf(
            ['2026-03-04', '2026-02-21', '2026-02-02', '2026-06-11'].map(event),
        );
        expect(months).toEqual(['2026-02', '2026-03', '2026-06']);
    });

    it('is empty when the catalog is', () => {
        expect(monthsOf([])).toEqual([]);
    });
});
