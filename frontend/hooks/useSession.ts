'use client';

import { useContext, useSyncExternalStore } from 'react';
import { AuthContext } from 'react-oidc-context';
import { refusedSessions } from '@/lib/api';

/**
 * Auth state that tolerates the provider not being mounted yet.
 *
 * `AuthProvider` needs `window` to derive the Keycloak authority, so it only
 * mounts after hydration. Reading the context directly (rather than `useAuth`,
 * which throws when the provider is missing) lets pages server-render normally
 * and simply report "still loading" for that first client render.
 */
export function useSession() {
    const auth = useContext(AuthContext);
    const refused = useSyncExternalStore(refusedSessions.subscribe, refusedSessions.current, () => null);
    const token = auth?.user?.access_token;
    // Signed in once, but over: the token is past its expiry and was not renewed, or the API has
    // already refused it. Pages ask the buyer to sign in again rather than failing every request.
    const expired = !!auth?.user && (auth.user.expired === true || (!!token && refused === `Bearer ${token}`));

    return {
        /** Bearer token for API calls, or undefined while signed out, still loading, or lapsed. */
        token: expired ? undefined : token,
        isAuthenticated: (auth?.isAuthenticated ?? false) && !expired,
        /** The session has lapsed: offer to sign in again, straight back to this page. */
        expired,
        /** Why signing in or renewing the session last failed, if it did. */
        error: auth?.error,
        /** True until the provider has mounted and settled its initial state. */
        isLoading: auth === undefined || auth.isLoading,
        profile: auth?.user?.profile,
        /** Sends the user to Keycloak and brings them back to the page they left. */
        signIn: () => auth?.signinRedirect({ redirect_uri: window.location.href }),
        signOut: () => auth?.signoutRedirect(),
    };
}

/** Convenience for hooks that only need the bearer token. */
export function useAccessToken(): string | undefined {
    return useSession().token;
}
