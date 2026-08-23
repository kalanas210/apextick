import axios from 'axios';
import type { ProblemDetail } from './types';

/**
 * Behind Caddy (https) the API is same-origin; in local development the
 * booking service answers on :8081 alongside `next dev` on :3000.
 * `NEXT_PUBLIC_API_URL` overrides both, for hosts where those ports are taken.
 */
const baseURL =
    process.env.NEXT_PUBLIC_API_URL ||
    (typeof window !== 'undefined'
        ? (window.location.protocol === 'https:'
            ? window.location.origin
            : `http://${window.location.hostname}:8081`)
        : '');

export const api = axios.create({ baseURL });

/** Absolute URL for an API path — needed for links the browser follows itself (PDF downloads). */
export function apiUrl(path: string): string {
    return `${baseURL}${path}`;
}

export function authHeaders(token?: string): Record<string, string> {
    return token ? { Authorization: `Bearer ${token}` } : {};
}

/**
 * Human-readable message from an API failure. The backend speaks RFC-7807, so
 * prefer its `detail` over axios's generic "Request failed with status code…".
 */
export function apiErrorMessage(error: unknown, fallback = 'Something went wrong.'): string {
    if (axios.isAxiosError(error)) {
        const problem = error.response?.data as ProblemDetail | undefined;
        if (problem?.detail) {
            return problem.detail;
        }
        if (error.response?.status === 401) {
            return 'Your session expired. Please sign in again.';
        }
    }
    return fallback;
}

/** The API's machine-readable error code (e.g. `SEAT_UNAVAILABLE`), when present. */
export function apiErrorCode(error: unknown): string | undefined {
    return axios.isAxiosError(error)
        ? (error.response?.data as ProblemDetail | undefined)?.code
        : undefined;
}
