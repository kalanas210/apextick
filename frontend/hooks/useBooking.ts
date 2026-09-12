'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api, authHeaders } from '@/lib/api';
import { useAccessToken } from './useSession';
import type {
    EventDetail, Hold, Order, Payment, PaymentConfig, Seat, Ticket,
} from '@/lib/types';

/** A fresh key per attempt: the API dedupes retries of the *same* logical request. */
function idempotencyKey(): string {
    return crypto.randomUUID();
}

/* ------------------------------- catalog --------------------------------- */

/** Event detail (sections, tiers, availability). Public — no token required. */
export function useEvent(slug: string) {
    return useQuery({
        queryKey: ['event', slug],
        queryFn: async () => (await api.get<EventDetail>(`/api/events/${slug}`)).data,
    });
}

/**
 * The live seat map. Public, but a token makes the API mark the caller's own
 * holds (`mine`), which is what lets the UI keep them selected.
 */
export function useSeats(slug: string) {
    const token = useAccessToken();
    return useQuery({
        queryKey: ['seats', slug, token ? 'me' : 'anon'],
        queryFn: async () =>
            (await api.get<Seat[]>(`/api/events/${slug}/seats`, { headers: authHeaders(token) })).data,
    });
}

/* --------------------------------- holds --------------------------------- */

export function useMyHold(slug: string) {
    const token = useAccessToken();
    return useQuery({
        queryKey: ['hold', slug],
        enabled: !!token,
        queryFn: async () =>
            (await api.get<Hold | ''>(`/api/events/${slug}/holds/me`, { headers: authHeaders(token) })).data || null,
    });
}

export function useHoldSeats(slug: string) {
    const token = useAccessToken();
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: async (seatIds: number[]) =>
            (await api.post<Hold>(`/api/events/${slug}/holds`, { seatIds }, { headers: authHeaders(token) })).data,
        onSettled: () => {
            queryClient.invalidateQueries({ queryKey: ['seats', slug] });
            queryClient.invalidateQueries({ queryKey: ['hold', slug] });
        },
    });
}

export function useReleaseHold(slug: string) {
    const token = useAccessToken();
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: async () => {
            await api.delete(`/api/events/${slug}/holds`, { headers: authHeaders(token) });
        },
        onSettled: () => {
            queryClient.invalidateQueries({ queryKey: ['seats', slug] });
            queryClient.invalidateQueries({ queryKey: ['hold', slug] });
        },
    });
}

/* -------------------------------- orders --------------------------------- */

export function useCreateOrder() {
    const token = useAccessToken();
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: async (input: { eventId: number; seatIds: number[] }) =>
            (await api.post<Order>('/api/orders', input, {
                headers: { ...authHeaders(token), 'Idempotency-Key': idempotencyKey() },
            })).data,
        onSettled: () => queryClient.invalidateQueries({ queryKey: ['orders'] }),
    });
}

export function useOrder(orderId: string) {
    const token = useAccessToken();
    return useQuery({
        queryKey: ['order', orderId],
        enabled: !!token && !!orderId,
        queryFn: async () =>
            (await api.get<Order>(`/api/orders/${orderId}`, { headers: authHeaders(token) })).data,
    });
}

/**
 * The caller's own orders. `enabled` is for screens that only need the list once
 * something has gone wrong — the seat map asks for it when the API refuses on
 * account of an unpaid order, not on every visit.
 */
export function useMyOrders(enabled = true) {
    const token = useAccessToken();
    return useQuery({
        queryKey: ['orders', 'me'],
        enabled: !!token && enabled,
        queryFn: async () =>
            (await api.get<{ content: Order[] } | Order[]>('/api/orders/me', { headers: authHeaders(token) })).data,
        select: (data) => (Array.isArray(data) ? data : data.content),
    });
}

export function useCancelOrder(orderId: string) {
    const token = useAccessToken();
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: async () =>
            (await api.post<Order>(`/api/orders/${orderId}/cancel`, null, { headers: authHeaders(token) })).data,
        onSettled: () => {
            queryClient.invalidateQueries({ queryKey: ['order', orderId] });
            queryClient.invalidateQueries({ queryKey: ['orders'] });
        },
    });
}

/* ------------------------------- payments -------------------------------- */

/** Which gateway is live, plus the Stripe publishable key when there is one. */
export function usePaymentConfig() {
    return useQuery({
        queryKey: ['payment-config'],
        staleTime: Infinity, // fixed for the lifetime of the deployment
        queryFn: async () => (await api.get<PaymentConfig>('/api/payments/config')).data,
    });
}

export interface PayInput {
    /** Mock gateway: raw test-card details. Never sent when Stripe is active. */
    card?: { number: string; expMonth: number; expYear: number; cvc: string; holder: string };
    /** Stripe: a PaymentMethod minted in the browser, so no PAN reaches our server. */
    paymentMethodId?: string;
}

export function usePayOrder(orderId: string) {
    const token = useAccessToken();
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: async (input: PayInput) =>
            (await api.post<Payment>(`/api/orders/${orderId}/pay`, input, {
                headers: { ...authHeaders(token), 'Idempotency-Key': idempotencyKey() },
            })).data,
        onSettled: () => {
            queryClient.invalidateQueries({ queryKey: ['order', orderId] });
            queryClient.invalidateQueries({ queryKey: ['tickets', orderId] });
        },
    });
}

/* -------------------------------- tickets -------------------------------- */

export function useOrderTickets(orderId: string, enabled = true) {
    const token = useAccessToken();
    return useQuery({
        queryKey: ['tickets', orderId],
        enabled: !!token && !!orderId && enabled,
        queryFn: async () =>
            (await api.get<Ticket[]>(`/api/orders/${orderId}/tickets`, { headers: authHeaders(token) })).data,
    });
}
