'use client';

import { useAuth } from 'react-oidc-context';
import Seats from '@/components/Seats';

export default function Home() {
    const auth = useAuth();

    if (auth.isLoading) return <div className="p-8">Loading…</div>;
    if (auth.error) return <div className="p-8">Error: {auth.error.message}</div>;

    if (auth.isAuthenticated) {
        return (
            <div className="space-y-6 p-8">
                <div className="flex items-center justify-between">
                    <p>
                        Signed in as{' '}
                        <strong>{auth.user?.profile.preferred_username as string}</strong>
                    </p>
                    <button
                        onClick={() => auth.signoutRedirect()}
                        className="rounded bg-red-600 px-4 py-2 text-white"
                    >
                        Log out
                    </button>
                </div>
                <Seats eventId={1} />
            </div>
        );
    }

    return (
        <div className="p-8">
            <button
                onClick={() => auth.signinRedirect()}
                className="rounded bg-blue-600 px-4 py-2 text-white"
            >
                Log in
            </button>
        </div>
    );
}