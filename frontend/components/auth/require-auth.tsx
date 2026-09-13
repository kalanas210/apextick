"use client";

import type { ReactNode } from "react";
import { useSession } from "@/hooks/useSession";
import { Button } from "@/components/ui/button";

/**
 * Gates a client subtree behind a Keycloak session. Rather than redirecting,
 * it shows an inline prompt — a signed-out visitor browsing seats should keep
 * the page they are on and sign in without losing their place. A session that
 * lapsed mid-page gets the same prompt, saying so, instead of the page below
 * reporting its data as missing.
 */
export function RequireAuth({
  children,
  title = "Sign in to continue",
  description = "Booking a seat needs an ApexTick account. You will come straight back here.",
  expiredDescription = "Sign in again to carry on. You will come straight back to this page.",
}: {
  children: ReactNode;
  title?: string;
  description?: string;
  expiredDescription?: string;
}) {
  const { isAuthenticated, isLoading, expired, error, signIn } = useSession();

  if (isLoading) {
    return (
      <div className="grid place-items-center py-16" aria-busy="true">
        <span className="h-6 w-6 animate-spin rounded-full border-2 border-line-2 border-t-accent" />
        <span className="sr-only">Checking your session…</span>
      </div>
    );
  }

  if (!isAuthenticated) {
    return (
      <div role={expired ? "alert" : undefined} className="rounded-2xl border border-line bg-ink-2 p-8 text-center">
        <h2 className="font-display text-xl font-semibold tracking-tight">
          {expired ? "Your session expired" : title}
        </h2>
        <p className="mx-auto mt-2 max-w-sm text-[0.86rem] text-muted">{expired ? expiredDescription : description}</p>
        {!expired && error && (
          // a sign-in that came back broken (a stale tab, a state Keycloak no longer knows) says so
          <p className="mx-auto mt-2 max-w-sm text-[0.76rem] text-faint">Signing in did not finish: {error.message}</p>
        )}
        <div className="mt-6 flex justify-center">
          <Button onClick={signIn} size="md">
            {expired ? "Sign in again" : "Sign in"}
          </Button>
        </div>
      </div>
    );
  }

  return <>{children}</>;
}
