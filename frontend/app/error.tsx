"use client";

import { useEffect } from "react";
import { Button } from "@/components/ui/button";

/**
 * The storefront renders from the catalog on every request, so when the booking
 * service is unreachable a fixture page has nothing to show. Saying so — and
 * offering the way back — beats Next's stock "Application error" screen, and
 * beats pretending the fixture does not exist.
 */
export default function Error({
  error,
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  useEffect(() => {
    console.error(error);
  }, [error]);

  return (
    <div className="shell flex min-h-[82vh] flex-col items-center justify-center py-32 text-center">
      <span className="kicker">Something went wrong</span>
      <h1 className="display mt-5 text-[clamp(2.6rem,11vw,7rem)]">
        Play stopped.
      </h1>
      <p className="mt-6 max-w-sm text-[0.98rem] leading-relaxed text-muted">
        We could not load this page just now. Nothing you have booked is
        affected — try again in a moment.
      </p>
      <div className="mt-10 flex flex-wrap items-center justify-center gap-3">
        <Button onClick={reset} arrow magnetic>
          Try again
        </Button>
        <Button href="/events" variant="outline">
          Browse fixtures
        </Button>
      </div>
      {error.digest && (
        <p className="mt-8 font-mono text-[0.62rem] uppercase tracking-[0.18em] text-faint">
          Reference {error.digest}
        </p>
      )}
    </div>
  );
}
