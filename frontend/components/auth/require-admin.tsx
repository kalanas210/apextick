"use client";

import type { ReactNode } from "react";
import { useIsAdmin } from "@/hooks/useMe";
import { useSession } from "@/hooks/useSession";
import { apiStatus } from "@/lib/api";
import { Button } from "@/components/ui/button";
import { RequireAuth } from "./require-auth";

/**
 * Gates the admin area on the `admin` realm role.
 *
 * Composes RequireAuth rather than repeating it, so a signed-out visitor gets the
 * same inline prompt as everywhere else. Because it returns instead of rendering
 * children, a non-admin never fires a single /api/admin request — the API would
 * refuse them anyway, but there is no reason to make it say so.
 */
export function RequireAdmin({ children }: { children: ReactNode }) {
  return (
    <RequireAuth
      title="Sign in to continue"
      description="The admin area needs an ApexTick account with the admin role."
    >
      <AdminGate>{children}</AdminGate>
    </RequireAuth>
  );
}

function AdminGate({ children }: { children: ReactNode }) {
  const { isAdmin, isLoading, error } = useIsAdmin();
  const { signIn } = useSession();

  if (isLoading) {
    return (
      <div className="grid place-items-center py-16" aria-busy="true">
        <span className="h-6 w-6 animate-spin rounded-full border-2 border-line-2 border-t-accent" />
        <span className="sr-only">Checking your permissions…</span>
      </div>
    );
  }

  // An expired token reads as 401, not 403 — that is "sign in again", not "you
  // are not allowed", and telling someone the wrong one sends them nowhere useful.
  if (error && apiStatus(error) === 401) {
    return (
      <Card title="Your session expired" description="Sign in again to get back to the admin area.">
        <Button onClick={signIn} size="md">
          Sign in
        </Button>
      </Card>
    );
  }

  if (error) {
    return (
      <Card
        title="Could not check your permissions"
        description="The API did not answer. Reload the page, or try again in a moment."
      >
        <Button onClick={() => window.location.reload()} size="md" variant="outline">
          Reload
        </Button>
      </Card>
    );
  }

  if (!isAdmin) {
    return (
      <Card
        title="Not authorised"
        description="This area is for ApexTick administrators. Your account does not have the admin role."
      >
        <Button href="/" size="md" variant="outline">
          Back to the site
        </Button>
      </Card>
    );
  }

  return <>{children}</>;
}

function Card({
  title,
  description,
  children,
}: {
  title: string;
  description: string;
  children: ReactNode;
}) {
  return (
    <div className="rounded-2xl border border-line bg-ink-2 p-8 text-center">
      <h2 className="font-display text-xl font-semibold tracking-tight">{title}</h2>
      <p className="mx-auto mt-2 max-w-sm text-[0.86rem] text-muted">{description}</p>
      <div className="mt-6 flex justify-center">{children}</div>
    </div>
  );
}
