"use client";

import { useSyncExternalStore } from "react";
import Link from "next/link";
import { useSeriesList } from "@/hooks/useCatalog";

/** True only once the browser has taken over; false in the server's markup. */
const noSubscribe = () => () => {};

/**
 * The footer's series links, read from the catalog. It sits at the bottom of
 * every page, including the admin panel, so it fetches in the browser rather
 * than making every route in the app wait on the API to render.
 *
 * Nothing renders until after hydration. The fixtures grid seeds this same
 * query with the list it was server-rendered from, so on that page the browser
 * has the series on its very first render while the server's markup did not —
 * and React then found "Series" where the server had written "Explore".
 */
export function FooterSeries() {
  const hydrated = useSyncExternalStore(
    noSubscribe,
    () => true,
    () => false,
  );
  const { data: series = [] } = useSeriesList();

  if (!hydrated || series.length === 0) {
    return null;
  }

  return (
    <div>
      <h3 className="kicker mb-5">Series</h3>
      <ul className="space-y-3">
        {series.map((s) => (
          <li key={s.id}>
            <Link
              href={`/events?series=${s.slug}`}
              className="text-[0.92rem] text-muted transition-colors hover:text-bone"
            >
              {s.shortName ?? s.name}
            </Link>
          </li>
        ))}
      </ul>
    </div>
  );
}
