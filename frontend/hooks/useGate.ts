'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api, authHeaders, retryOn5xx } from '@/lib/api';
import type { Admissions, ScanResult, UnadmitResult } from '@/lib/gate';
import { useAccessToken } from './useSession';

export interface ScanInput {
    qrToken: string;
    /** The event this gate is admitting to; a ticket for any other is refused. */
    eventId: number;
    /** Which gate scanned, for the record. */
    gate?: string;
}

export interface UnadmitInput {
    ticketId: string;
    /** Required: an admission undone goes on the ticket's record with its reason. */
    reason: string;
}

/** The query key of an event's admissions count, shared by the poll and the scans that move it. */
export function admissionsKey(eventId: number | null) {
    return ['gate', 'admissions', eventId] as const;
}

/**
 * Scans a ticket at the gate. The gate API, not /api/admin: a steward's device holds
 * the scanner role, which opens this and nothing else.
 */
export function useScanTicket() {
    const token = useAccessToken();
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: async (input: ScanInput) =>
            (await api.post<ScanResult>('/api/gate/scans', input, { headers: authHeaders(token) })).data,
        // An admission answers with the new count, so this device's figure moves at once
        // rather than at the next poll.
        onSuccess: (result: ScanResult) => {
            queryClient.setQueryData(admissionsKey(result.event.id), result.admissions);
        },
    });
}

/** Undoes a mistaken admission. An admin's action: the API refuses a steward. */
export function useUnadmitTicket() {
    const token = useAccessToken();
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: async ({ ticketId, reason }: UnadmitInput) =>
            (await api.post<UnadmitResult>(`/api/gate/tickets/${ticketId}/unadmit`, { reason },
                { headers: authHeaders(token) })).data,
        onSuccess: (result: UnadmitResult) => {
            queryClient.setQueryData(admissionsKey(result.ticket.eventId), result.admissions);
        },
    });
}

/**
 * Tickets admitted to an event so far, across every gate. Polled, because the other
 * turnstiles are other devices and this one only hears about its own scans.
 */
export function useAdmissions(eventId: number | null) {
    const token = useAccessToken();
    return useQuery({
        queryKey: admissionsKey(eventId),
        enabled: !!token && eventId !== null,
        refetchInterval: 15_000,
        retry: retryOn5xx,
        queryFn: async () =>
            (await api.get<Admissions>(`/api/gate/events/${eventId}/admissions`,
                { headers: authHeaders(token) })).data,
    });
}
