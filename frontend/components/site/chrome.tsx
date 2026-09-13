"use client";

import { usePathname } from "next/navigation";
import type { ReactNode } from "react";
import { Header } from "./header";

const BARE_PREFIXES = ["/signin", "/register", "/forgot-password", "/admin"];

/**
 * Decides the page chrome. Auth routes get a clean, focused canvas with no
 * marketing header or footer, and the admin panel brings its own sidebar;
 * everything else gets the full site chrome.
 */
export function Chrome({
  children,
  footer,
}: {
  children: ReactNode;
  footer: ReactNode;
}) {
  const pathname = usePathname();
  const bare = BARE_PREFIXES.some((p) => pathname.startsWith(p));

  if (bare) {
    return (
      <main id="main" tabIndex={-1} className="outline-none">
        {children}
      </main>
    );
  }

  return (
    <>
      <Header />
      <main id="main" tabIndex={-1} className="outline-none">
        {children}
      </main>
      {footer}
    </>
  );
}
