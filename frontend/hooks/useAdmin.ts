'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api, authHeaders, retryOn5xx } from '@/lib/api';
import { useAccessToken } from './useSession';
import type {
    AdminSeat, EventDetail, EventStats, EventSummary, EventUpsert, LayoutInput, LayoutResult,
    Order, PageResponse, Series, Team, VerifyResult,
} from '@/lib/types';

export interface AdminEventParams {
    q?: string;
    status?: string;
    sport?: string;
    page?: number;
    size?: number;
}

export interface AdminOrderParams {
    status?: string;
    page?: number;
    size?: number;
}

/** Drops empty values so the query key is stable and the URL stays clean. */
function params(input: Record<string, string | number | undefined>) {
    return Object.fromEntries(
        Object.entries(input).filter(([, v]) => v !== undefined && v !== ''),
    );
}

/* -------------------------------- events --------------------------------- */

/**
 * The admin event list. Not the public one: that hides drafts, which is exactly
 * what an operator has just created and is looking for.
 */
export function useAdminEvents(query: AdminEventParams) {
    const token = useAccessToken();
    return useQuery({
        queryKey: ['admin', 'events', query],
        enabled: !!token,
        retry: retryOn5xx,
        queryFn: async () =>
            (await api.get<PageResponse<EventSummary>>('/api/admin/events', {
                headers: authHeaders(token),
                params: params({ ...query }),
            })).data,
    });
}

/**
 * One event for the panel. Same shape as the public `useEvent`, different read
 * model: the public endpoint only serves what is on sale, and a draft is exactly
 * what an operator is editing. Its own key, because the two answers differ.
 */
export function useAdminEvent(id: number | undefined) {
    const token = useAccessToken();
    return useQuery({
        queryKey: ['admin', 'event', id],
        enabled: !!token && !!id,
        retry: retryOn5xx,
        queryFn: async () =>
            (await api.get<EventDetail>(`/api/admin/events/${id}`, { headers: authHeaders(token) })).data,
    });
}

export function useCreateEvent() {
    const token = useAccessToken();
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: async (body: EventUpsert) =>
            (await api.post<EventDetail>('/api/admin/events', body, { headers: authHeaders(token) })).data,
        onSettled: () => queryClient.invalidateQueries({ queryKey: ['admin', 'events'] }),
    });
}

export function useUpdateEvent(id: number) {
    const token = useAccessToken();
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: async (body: EventUpsert) =>
            (await api.put<EventDetail>(`/api/admin/events/${id}`, body, { headers: authHeaders(token) })).data,
        onSettled: () => {
            queryClient.invalidateQueries({ queryKey: ['admin', 'events'] });
            queryClient.invalidateQueries({ queryKey: ['admin', 'event', id] });
            queryClient.invalidateQueries({ queryKey: ['event', String(id)] });
        },
    });
}

export function useSetEventStatus(id: number) {
    const token = useAccessToken();
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: async (status: string) =>
            (await api.patch<EventDetail>(`/api/admin/events/${id}/status`, { status },
                { headers: authHeaders(token) })).data,
        onSettled: () => {
            queryClient.invalidateQueries({ queryKey: ['admin', 'events'] });
            queryClient.invalidateQueries({ queryKey: ['admin', 'event', id] });
            queryClient.invalidateQueries({ queryKey: ['event', String(id)] });
            // Going on sale (or off it) changes what the public catalog shows.
            queryClient.invalidateQueries({ queryKey: ['events'] });
        },
    });
}

export function useDeleteEvent() {
    const token = useAccessToken();
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: async (id: number) => {
            await api.delete(`/api/admin/events/${id}`, { headers: authHeaders(token) });
        },
        onSettled: () => {
            queryClient.invalidateQueries({ queryKey: ['admin', 'events'] });
            queryClient.invalidateQueries({ queryKey: ['events'] });
        },
    });
}

/* -------------------------------- layout --------------------------------- */

/** One-shot: the API answers 409 LAYOUT_EXISTS if the event already has seats. */
export function useApplyLayout(id: number) {
    const token = useAccessToken();
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: async (body: LayoutInput) =>
            (await api.post<LayoutResult>(`/api/admin/events/${id}/layout`, body,
                { headers: authHeaders(token) })).data,
        onSettled: () => {
            // The tiers and sections the builder switches on live on the event itself.
            queryClient.invalidateQueries({ queryKey: ['admin', 'event', id] });
            queryClient.invalidateQueries({ queryKey: ['event', String(id)] });
            queryClient.invalidateQueries({ queryKey: ['admin', 'stats', id] });
            queryClient.invalidateQueries({ queryKey: ['admin', 'seats', id] });
            // The list shows seat counts, which only exist once a layout does.
            queryClient.invalidateQueries({ queryKey: ['admin', 'events'] });
        },
    });
}

/* --------------------------------- stats --------------------------------- */

export function useEventStats(id: number | undefined) {
    const token = useAccessToken();
    return useQuery({
        queryKey: ['admin', 'stats', id],
        enabled: !!token && !!id,
        retry: retryOn5xx,
        queryFn: async () =>
            (await api.get<EventStats>(`/api/admin/events/${id}/stats`, { headers: authHeaders(token) })).data,
    });
}

/* -------------------------------- orders --------------------------------- */

export function useAdminOrders(query: AdminOrderParams) {
    const token = useAccessToken();
    return useQuery({
        queryKey: ['admin', 'orders', query],
        enabled: !!token,
        retry: retryOn5xx,
        queryFn: async () =>
            (await api.get<PageResponse<Order>>('/api/admin/orders', {
                headers: authHeaders(token),
                params: params({ ...query }),
            })).data,
    });
}

/* --------------------------------- seats --------------------------------- */

/** Every seat for one event, unpaginated — the caller pages it client-side. */
export function useAdminSeats(eventId: number | undefined) {
    const token = useAccessToken();
    return useQuery({
        queryKey: ['admin', 'seats', eventId],
        enabled: !!token && !!eventId,
        retry: retryOn5xx,
        queryFn: async () =>
            (await api.get<AdminSeat[]>('/api/admin/seats', {
                headers: authHeaders(token),
                params: { eventId },
            })).data,
    });
}

export function useReleaseSeat(eventId: number) {
    const token = useAccessToken();
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: async (seatId: number) =>
            (await api.post<AdminSeat>(`/api/admin/seats/${seatId}/release`, null,
                { headers: authHeaders(token) })).data,
        onSettled: () => {
            queryClient.invalidateQueries({ queryKey: ['admin', 'seats', eventId] });
            queryClient.invalidateQueries({ queryKey: ['admin', 'stats', eventId] });
            // Other browsers hear about this over STOMP; this tab asks directly.
            queryClient.invalidateQueries({ queryKey: ['seats'] });
        },
    });
}

/* -------------------------------- tickets -------------------------------- */

/** No invalidation: the scanner keeps its own result and log. */
export function useVerifyTicket() {
    const token = useAccessToken();
    return useMutation({
        mutationFn: async (qrToken: string) =>
            (await api.post<VerifyResult>('/api/admin/tickets/verify', { qrToken },
                { headers: authHeaders(token) })).data,
    });
}

/* -------------------------------- lookups -------------------------------- */

export function useAdminTeams(sport?: string) {
    const token = useAccessToken();
    return useQuery({
        queryKey: ['admin', 'teams', sport ?? 'all'],
        enabled: !!token,
        staleTime: Infinity,
        retry: retryOn5xx,
        queryFn: async () =>
            (await api.get<Team[]>('/api/admin/teams', {
                headers: authHeaders(token),
                params: params({ sport }),
            })).data,
    });
}

export function useSeriesList() {
    return useQuery({
        queryKey: ['series'],
        staleTime: Infinity,
        queryFn: async () => (await api.get<Series[]>('/api/series')).data,
    });
}
