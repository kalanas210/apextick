"use client";

import Link from "next/link";
import { cn } from "@/lib/cn";
import { useSession } from "@/hooks/useSession";

/**
 * Header account control. Signed out it starts the Keycloak redirect; signed in
 * it links to the account area and offers a sign-out. Renders nothing while the
 * auth provider is still settling, so the header never flickers between states.
 */
export function AccountMenu({ className }: { className?: string }) {
  const { isAuthenticated, isLoading, profile, signIn, signOut } = useSession();

  const linkClasses =
    "rounded-full px-3.5 py-2 text-[0.82rem] transition-colors text-muted hover:text-bone";

  if (isLoading) {
    return <span className={cn(linkClasses, "opacity-0", className)} aria-hidden>Sign in</span>;
  }

  if (!isAuthenticated) {
    return (
      <button type="button" onClick={signIn} className={cn(linkClasses, className)}>
        Sign in
      </button>
    );
  }

  const name =
    (typeof profile?.given_name === "string" && profile.given_name) ||
    (typeof profile?.preferred_username === "string" && profile.preferred_username) ||
    "Account";

  return (
    <span className={cn("flex items-center gap-1", className)}>
      <Link href="/account" className={cn(linkClasses, "text-bone")}>
        {name}
      </Link>
      <button
        type="button"
        onClick={signOut}
        className={cn(linkClasses, "text-faint hover:text-bone")}
      >
        Sign out
      </button>
    </span>
  );
}
