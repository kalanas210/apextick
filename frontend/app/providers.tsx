'use client';
import { useEffect, useState, useMemo } from 'react';
import { AuthProvider } from 'react-oidc-context';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

export default function Providers({ children }: { children: React.ReactNode }) {
    const [mounted, setMounted] = useState(false);
    const [queryClient] = useState(() => new QueryClient({
        defaultOptions: {
            queries: {
                // seat availability moves constantly; never serve it stale on focus
                refetchOnWindowFocus: true,
                retry: 1,
            },
        },
    }));
    // One-time mount flag so the OIDC config is built only in the browser.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    useEffect(() => setMounted(true), []);

    // Build the OIDC config once, after mount. `window` doesn't exist during
    // server-side rendering, so we guard on `mounted` (which only flips true
    // in the browser). useMemo keeps the object identity stable so
    // react-oidc-context doesn't rebuild its auth client on every render.
    const oidcConfig = useMemo(() => {
        if (!mounted) return null;
        const host = window.location.hostname;   // localhost  OR  the EC2 IP
        const origin = window.location.origin;   // http://<host>:3000  OR  https://<host>
        // Over HTTPS we're behind the Caddy reverse proxy, so Keycloak is reachable
        // same-origin under /realms (no port, no CORS, and PKCE's crypto.subtle works
        // because the page is now a secure context). Over plain HTTP (local dev /
        // direct-IP) we hit Keycloak on its own port instead.
        const proxied = window.location.protocol === 'https:';
        const authority = proxied
            ? `${origin}/realms/apextick`
            : `http://${host}:8180/realms/apextick`;
        return {
            authority,
            client_id: 'apextick-web',
            redirect_uri: origin,
            post_logout_redirect_uri: origin,
            response_type: 'code',
            scope: 'openid profile email',
            // keep long checkout sessions alive rather than 401-ing mid-payment
            automaticSilentRenew: true,
            onSigninCallback: () => {
                // drop ?code=&state= from the address bar, keeping the page the
                // user signed in from (useSession passes it as redirect_uri)
                window.history.replaceState({}, document.title, window.location.pathname);
            },
        };
    }, [mounted]);

    // Children always render, even before the auth client exists: the marketing
    // pages stay server-rendered, and `useSession` reports "loading" until the
    // provider below mounts.
    if (!oidcConfig) {
        return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>;
    }

    return (
        <AuthProvider {...oidcConfig}>
            <QueryClientProvider client={queryClient}>
                {children}
            </QueryClientProvider>
        </AuthProvider>
    );
}
