"use client";

import type { ReactNode } from "react";
import { useRoles } from "@/hooks/useMe";
import { useSession } from "@/hooks/useSession";
import { apiStatus } from "@/lib/api";
import { Button } from "@/components/ui/button";
import { RequireAuth } from "./require-auth";

/**
 * Gates the admin area on the `admin` realm role, or on `admin` or `scanner` for the
 * one screen a gate steward may open.
 *
 * Composes RequireAuth rather than repeating it, so a signed-out visitor gets the
 * same inline prompt as everywhere else. Because it returns instead of rendering
 * children, a visitor without the role never fires a single request the API would
 * refuse — it would refuse them anyway, but there is no reason to make it say so.
 */
export function RequireAdmin({
  children,
  allowScanner = false,
}: {
  children: ReactNode;
  /** Let a gate steward in as well: true for the scanner, and only the scanner. */
  allowScanner?: boolean;
}) {
  return (
    <RequireAuth
      title="Sign in to continue"
      description={
        allowScanner
          ? "The scanner needs an ApexTick account with the admin or scanner role."
          : "The admin area needs an ApexTick account with the admin role."
      }
    >
      <AdminGate allowScanner={allowScanner}>{children}</AdminGate>
    </RequireAuth>
  );
}

function AdminGate({ children, allowScanner }: { children: ReactNode; allowScanner: boolean }) {
  const { isAdmin, isScanner, isLoading, error } = useRoles();
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

  if (isAdmin || (allowScanner && isScanner)) {
    return <>{children}</>;
  }

  // A steward who wandered off the scanner: send them back to the page they can use.
  if (isScanner) {
    return (
      <Card
        title="Not authorised"
        description="Your account scans tickets at the gate. The rest of the admin area needs the admin role."
      >
        <Button href="/admin/scan" size="md" variant="outline">
          Open the scanner
        </Button>
      </Card>
    );
  }

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
