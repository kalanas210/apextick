'use client';

import { useQuery } from '@tanstack/react-query';
import { api, retryOn5xx } from '@/lib/api';
import { catalogQuery, hasFilters, type CatalogFilters } from '@/lib/catalog';
import type { EventSummary, PageResponse, Series } from '@/lib/types';

/**
 * The public catalog, filtered and sorted by the API. The page that mounts the
 * grid has already fetched the unfiltered view on the server and passes it in, so
 * the default grid paints with no request at all and only a changed filter costs
 * a round trip — and that seed is used only for the filters it was fetched with.
 */
export function useEventList(filters: CatalogFilters, initialData?: EventSummary[]) {
    return useQuery({
        queryKey: ['events', filters],
        // keeps the previous grid on screen while the next filter loads
        placeholderData: (previous) => previous,
        // a grid of 20 fixtures is not the live surface; the seat map is
        staleTime: 30_000,
        retry: retryOn5xx,
        initialData: hasFilters(filters) ? undefined : initialData,
        queryFn: async () =>
            (await api.get<PageResponse<EventSummary>>(catalogQuery(filters))).data.content,
    });
}

/** Every series, for the filter pills and the footer's links. */
export function useSeriesList(initialData?: Series[]) {
    return useQuery({
        queryKey: ['series'],
        staleTime: 5 * 60 * 1000, // a series is editorial, not inventory
        retry: retryOn5xx,
        initialData,
        queryFn: async () => (await api.get<Series[]>('/api/series')).data,
    });
}
