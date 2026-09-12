/**
 * Server-side reads of the public catalog, for the storefront pages that render
 * before the browser has run anything.
 *
 * The browser talks to the API through Caddy on the public origin (see
 * `lib/api.ts`); this runs inside the container, where that origin may not
 * resolve, so it prefers `API_INTERNAL_URL` — the service name on the compose
 * network. Everything here is read-only and anonymous: nothing that needs the
 * caller's token is fetched on the server.
 */
const baseURL =
    process.env.API_INTERNAL_URL ||
    process.env.NEXT_PUBLIC_API_URL ||
    'http://localhost:8081';

export class ApiError extends Error {
    constructor(readonly status: number, readonly path: string) {
        super(`GET ${path} failed with ${status}`);
        this.name = 'ApiError';
    }
}

/**
 * A catalog read. Availability changes by the second and an event can be pulled
 * from sale at any moment, so nothing here is cached — a page served from a
 * snapshot would offer seats that are already gone.
 */
export async function apiGet<T>(path: string): Promise<T> {
    const response = await fetch(`${baseURL}${path}`, {
        cache: 'no-store',
        headers: { Accept: 'application/json' },
    });
    if (!response.ok) {
        throw new ApiError(response.status, path);
    }
    return (await response.json()) as T;
}

/** The same read, but a 404 is an answer rather than a failure. */
export async function apiGetOrNull<T>(path: string): Promise<T | null> {
    try {
        return await apiGet<T>(path);
    } catch (error) {
        if (error instanceof ApiError && error.status === 404) {
            return null;
        }
        throw error;
    }
}

/**
 * A read whose failure must not take the page down with it — decorative lists
 * (the footer's series links, a "more from this series" rail) are worth less
 * than the page around them.
 */
export async function apiGetOr<T>(path: string, fallback: T): Promise<T> {
    try {
        return await apiGet<T>(path);
    } catch {
        return fallback;
    }
}
