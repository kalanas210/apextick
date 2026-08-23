"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { useSession } from "@/hooks/useSession";
import { Button } from "@/components/ui/button";

/**
 * Hands sign-in over to Keycloak (authorization code + PKCE). ApexTick never
 * sees a password — the identity provider owns credentials, MFA and recovery,
 * and hands back a signed token.
 */
export function KeycloakSignIn({
  action = "signin",
}: {
  action?: "signin" | "register" | "reset";
}) {
  const router = useRouter();
  const { isAuthenticated, isLoading, signIn } = useSession();

  // Already signed in? There is nothing to do on this page.
  useEffect(() => {
    if (isAuthenticated) {
      router.replace("/account");
    }
  }, [isAuthenticated, router]);

  const copy = {
    signin: {
      button: "Continue to sign in",
      note: "You will be sent to our secure sign-in, then straight back here.",
    },
    register: {
      button: "Create an account",
      note: "Accounts are created on our secure sign-in page.",
    },
    reset: {
      button: "Reset your password",
      note: "Password resets are handled on our secure sign-in page.",
    },
  }[action];

  return (
    <div className="space-y-5">
      <Button onClick={signIn} size="lg" className="w-full" arrow>
        {isLoading ? "Preparing…" : copy.button}
      </Button>
      <p className="text-center text-[0.78rem] text-faint">{copy.note}</p>
      <p className="text-center text-[0.72rem] text-faint">
        Demo account: <span className="tnum text-muted">kalana</span> /{" "}
        <span className="tnum text-muted">12345</span>
      </p>
    </div>
  );
}
