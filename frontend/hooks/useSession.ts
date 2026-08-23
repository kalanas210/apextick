'use client';

import { useContext } from 'react';
import { AuthContext } from 'react-oidc-context';

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

    return {
        /** Bearer token for API calls, or undefined while signed out / still loading. */
        token: auth?.user?.access_token,
        isAuthenticated: auth?.isAuthenticated ?? false,
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
