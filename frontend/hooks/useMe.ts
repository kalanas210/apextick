'use client';

import { useQuery } from '@tanstack/react-query';
import { api, authHeaders, retryOn5xx } from '@/lib/api';
import type { Me } from '@/lib/types';
import { useAccessToken } from './useSession';

/**
 * The signed-in user as the API sees them, roles included.
 *
 * Asking the service rather than decoding the token client-side keeps one source
 * of truth — this is the same `CurrentUser` the API authorises against — needs no
 * JWT library, and notices a token that has been revoked or had its roles pulled
 * since it was issued, which a decode would happily still read as "admin".
 */
export function useMe() {
    const token = useAccessToken();
    return useQuery({
        queryKey: ['me'],
        enabled: !!token,
        staleTime: Infinity,
        retry: retryOn5xx,
        queryFn: async () => (await api.get<Me>('/api/me', { headers: authHeaders(token) })).data,
    });
}

/**
 * Whether the session may use the admin area. The realm role is lowercase `admin`;
 * the ROLE_ADMIN spelling only exists inside Spring Security's authority mapping.
 *
 * This gates what gets rendered, not what is permitted — /api/admin/** is enforced
 * server-side regardless of what this returns.
 */
export function useIsAdmin(): { isAdmin: boolean; isLoading: boolean; error: unknown } {
    const { data, isLoading, error } = useMe();
    return { isAdmin: data?.roles?.includes('admin') ?? false, isLoading, error };
}
