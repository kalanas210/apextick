"use client";

import type { ReactNode } from "react";
import { useSession } from "@/hooks/useSession";
import { Button } from "@/components/ui/button";

/**
 * Gates a client subtree behind a Keycloak session. Rather than redirecting,
 * it shows an inline prompt — a signed-out visitor browsing seats should keep
 * the page they are on and sign in without losing their place.
 */
export function RequireAuth({
  children,
  title = "Sign in to continue",
  description = "Booking a seat needs an ApexTick account. You will come straight back here.",
}: {
  children: ReactNode;
  title?: string;
  description?: string;
}) {
  const { isAuthenticated, isLoading, signIn } = useSession();

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
      <div className="rounded-2xl border border-line bg-ink-2 p-8 text-center">
        <h2 className="font-display text-xl font-semibold tracking-tight">{title}</h2>
        <p className="mx-auto mt-2 max-w-sm text-[0.86rem] text-muted">{description}</p>
        <div className="mt-6 flex justify-center">
          <Button onClick={signIn} size="md">
            Sign in
          </Button>
        </div>
      </div>
    );
  }

  return <>{children}</>;
}
