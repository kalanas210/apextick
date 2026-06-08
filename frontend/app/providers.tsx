'use client';

import { useEffect, useState } from 'react';
import { AuthProvider } from 'react-oidc-context';

const oidcConfig = {
    authority: 'http://localhost:8180/realms/apextick',
    client_id: 'apextick-web',
    redirect_uri: 'http://localhost:3000',
    post_logout_redirect_uri: 'http://localhost:3000',
    response_type: 'code',
    scope: 'openid profile email',
    onSigninCallback: () => {
        // wipe the ?code=...&state=... off the URL after login completes
        window.history.replaceState({}, document.title, window.location.pathname);
    },
};

export default function Providers({ children }: { children: React.ReactNode }) {
    const [mounted, setMounted] = useState(false);
    useEffect(() => setMounted(true), []);

    if (!mounted) {
        return <div className="p-8">Loading…</div>;
    }

    return <AuthProvider {...oidcConfig}>{children}</AuthProvider>;
}