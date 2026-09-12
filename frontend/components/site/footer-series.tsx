"use client";

import Link from "next/link";
import { useSeriesList } from "@/hooks/useCatalog";

/**
 * The footer's series links, read from the catalog. It sits at the bottom of
 * every page, including the admin panel, so it fetches in the browser rather
 * than making every route in the app wait on the API to render.
 */
export function FooterSeries() {
  const { data: series = [] } = useSeriesList();

  if (series.length === 0) {
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
