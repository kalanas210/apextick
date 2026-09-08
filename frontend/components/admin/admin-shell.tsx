"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import type { ReactNode } from "react";
import { cn } from "@/lib/cn";
import { useMe } from "@/hooks/useMe";
import { useSession } from "@/hooks/useSession";
import { RequireAdmin } from "@/components/auth/require-admin";
import { Logo } from "@/components/site/logo";

const NAV = [
  { href: "/admin", label: "Dashboard", exact: true },
  { href: "/admin/events", label: "Events" },
  { href: "/admin/orders", label: "Orders" },
  { href: "/admin/scan", label: "Scan tickets" },
];

/**
 * Chrome for the panel. Not the marketing header: that one is a scroll-reactive
 * fixed bar built around a mobile menu, where an operator wants their sections
 * visible at all times. Below `lg` the sidebar becomes a scrolling strip rather
 * than a second focus-trapping drawer.
 */
export function AdminShell({ children }: { children: ReactNode }) {
  return (
    <RequireAdmin>
      <Panel>{children}</Panel>
    </RequireAdmin>
  );
}

function Panel({ children }: { children: ReactNode }) {
  const pathname = usePathname();
  const { data: me } = useMe();
  const { signOut } = useSession();

  return (
    <div className="lg:flex lg:min-h-screen">
      <aside className="border-b border-line lg:sticky lg:top-0 lg:h-screen lg:w-60 lg:shrink-0 lg:border-b-0 lg:border-r">
        <div className="flex items-center justify-between gap-4 px-5 py-4 lg:block lg:px-6 lg:py-7">
          <div>
            <Logo />
            <p className="kicker mt-2 hidden lg:block">Admin</p>
          </div>
          <Link href="/" className="ulink text-[0.78rem] text-muted lg:hidden">
            Exit
          </Link>
        </div>

        <nav aria-label="Admin sections" className="px-3 pb-3 lg:px-3 lg:pb-6">
          <ul className="flex gap-1 overflow-x-auto lg:block lg:space-y-1 lg:overflow-visible">
            {NAV.map((item) => {
              const active = item.exact
                ? pathname === item.href
                : pathname.startsWith(item.href);
              return (
                <li key={item.href}>
                  <Link
                    href={item.href}
                    aria-current={active ? "page" : undefined}
                    className={cn(
                      "block whitespace-nowrap rounded-lg px-3 py-2 text-[0.86rem] transition-colors",
                      active
                        ? "bg-bone/[0.06] text-bone lg:border-l-2 lg:border-accent lg:rounded-l-none"
                        : "text-muted hover:bg-bone/[0.03] hover:text-bone",
                    )}
                  >
                    {item.label}
                  </Link>
                </li>
              );
            })}
          </ul>
        </nav>

        <div className="hidden border-t border-line px-6 py-5 lg:block">
          <p className="truncate text-[0.82rem] text-bone">{me?.username ?? "…"}</p>
          {me?.email && <p className="mt-0.5 truncate text-[0.72rem] text-faint">{me.email}</p>}
          <div className="mt-3 flex flex-col items-start gap-1.5">
            <Link href="/" className="ulink text-[0.78rem] text-muted">
              Back to the site
            </Link>
            <button
              type="button"
              onClick={signOut}
              className="text-[0.78rem] text-muted transition-colors hover:text-bone"
            >
              Sign out
            </button>
          </div>
        </div>
      </aside>

      {/* No fixed header here, so none of the marketing pages' top padding. */}
      <div className="min-w-0 flex-1 px-5 pb-20 pt-8 md:px-8 lg:px-10">{children}</div>
    </div>
  );
}

/** Page title block, so every admin screen opens the same way. */
export function AdminHeader({
  kicker,
  title,
  description,
  action,
}: {
  kicker: ReactNode;
  title: string;
  description?: ReactNode;
  action?: ReactNode;
}) {
  return (
    <header className="flex flex-wrap items-end justify-between gap-4 border-b border-line pb-6">
      <div className="min-w-0">
        <span className="kicker">{kicker}</span>
        <h1 className="display mt-2 text-[clamp(1.5rem,3vw,2.2rem)]">{title}</h1>
        {description && <p className="mt-2 text-[0.86rem] text-muted">{description}</p>}
      </div>
      {action}
    </header>
  );
}
