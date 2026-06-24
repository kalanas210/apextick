"use client";

import { usePathname } from "next/navigation";
import type { ReactNode } from "react";
import { Header } from "./header";

const AUTH_PREFIXES = ["/signin", "/register", "/forgot-password"];

/**
 * Decides the page chrome. Auth routes get a clean, focused canvas with no
 * marketing header or footer; everything else gets the full site chrome.
 */
export function Chrome({
  children,
  footer,
}: {
  children: ReactNode;
  footer: ReactNode;
}) {
  const pathname = usePathname();
  const isAuth = AUTH_PREFIXES.some((p) => pathname.startsWith(p));

  if (isAuth) {
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
