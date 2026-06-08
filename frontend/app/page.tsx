'use client';

import { useAuth } from 'react-oidc-context';

export default function Home() {
  const auth = useAuth();

  if (auth.isLoading) return <div className="p-8">Loading…</div>;
  if (auth.error) return <div className="p-8">Error: {auth.error.message}</div>;

  if (auth.isAuthenticated) {
    return (
        <div className="space-y-4 p-8">
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