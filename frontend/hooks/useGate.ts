'use client';

import { useMutation } from '@tanstack/react-query';
import { api, authHeaders } from '@/lib/api';
import type { ScanResult } from '@/lib/gate';
import { useAccessToken } from './useSession';

export interface ScanInput {
    qrToken: string;
    /** The event this gate is admitting to; a ticket for any other is refused. */
    eventId: number;
}

/**
 * Scans a ticket at the gate. The gate API, not /api/admin: a steward's device holds
 * the scanner role, which opens this and nothing else. No invalidation, because the
 * scanner keeps its own result and log.
 */
export function useScanTicket() {
    const token = useAccessToken();
    return useMutation({
        mutationFn: async (input: ScanInput) =>
            (await api.post<ScanResult>('/api/gate/scans', input, { headers: authHeaders(token) })).data,
    });
}
