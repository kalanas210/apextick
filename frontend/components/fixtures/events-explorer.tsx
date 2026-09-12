"use client";

import { useMemo, useState } from "react";
import { AnimatePresence, motion, useReducedMotion } from "framer-motion";
import { useEventList, useSeriesList } from "@/hooks/useCatalog";
import { DEFAULT_FILTERS, hasFilters, monthsOf, type CatalogFilters } from "@/lib/catalog";
import { apiErrorMessage } from "@/lib/api";
import { formatDate } from "@/lib/format";
import { cn } from "@/lib/cn";
import type { EventSummary, Series, Sport } from "@/lib/types";
import { FixtureCard } from "./fixture-card";

function monthLabel(month: string) {
  const d = formatDate(`${month}-01`);
  return `${d.month} ${d.year}`;
}

interface PillProps {
  active: boolean;
  onClick: () => void;
  children: React.ReactNode;
}

function Pill({ active, onClick, children }: PillProps) {
  return (
    <button
      type="button"
      onClick={onClick}
      aria-pressed={active}
      className={cn(
        "inline-flex items-center gap-2 rounded-full border px-3.5 py-1.5 text-[0.8rem] tracking-tight transition-colors duration-200",
        active
          ? "border-bone bg-bone text-ink"
          : "border-line-2 text-muted hover:border-bone/40 hover:text-bone",
      )}
    >
      {children}
    </button>
  );
}

function Group({
  label,
  children,
}: {
  label: string;
  children: React.ReactNode;
}) {
  return (
    <div className="flex flex-col gap-2.5">
      <span className="font-mono text-[0.6rem] uppercase tracking-[0.2em] text-faint">
        {label}
      </span>
      <div className="flex flex-wrap gap-2">{children}</div>
    </div>
  );
}

const SPORT_LABEL: Record<Sport, string> = { cricket: "Cricket", football: "Football" };

/**
 * The fixtures grid. Every filter is answered by the API — the browser never
 * holds the catalog and decides for itself what is on sale — but the page has
 * already fetched the unfiltered view on the server and hands it in, so the
 * first paint is complete and only a changed filter costs a request.
 */
export function EventsExplorer({
  initialEvents,
  initialSeries = "all",
  series: initialSeriesList,
}: {
  initialEvents: EventSummary[];
  initialSeries?: string;
  series: Series[];
}) {
  const reduce = useReducedMotion();
  const [filters, setFilters] = useState<CatalogFilters>({
    ...DEFAULT_FILTERS,
    series: initialSeries,
  });
  const set = <K extends keyof CatalogFilters>(key: K, value: CatalogFilters[K]) =>
    setFilters((f) => ({ ...f, [key]: value }));

  const { data: allSeries = [] } = useSeriesList(initialSeriesList);
  const { data: list = [], isFetching, error } = useEventList(filters, initialEvents);

  // Only offer a filter the catalog can answer: months and sports come from the
  // events themselves, and a series with nothing on sale is not a choice.
  const months = useMemo(() => monthsOf(initialEvents), [initialEvents]);
  const sports = useMemo(
    () =>
      (["cricket", "football"] as Sport[]).filter((s) =>
        initialEvents.some((e) => e.sport === s),
      ),
    [initialEvents],
  );
  const seriesChoices = useMemo(
    () => allSeries.filter((s) => initialEvents.some((e) => e.seriesSlug === s.slug)),
    [allSeries, initialEvents],
  );
  const seriesBySlug = useMemo(
    () => new Map(allSeries.map((s) => [s.slug, s])),
    [allSeries],
  );

  const clear = () => setFilters(DEFAULT_FILTERS);
  const filtered = hasFilters(filters);

  return (
    <div>
      <div className="flex flex-col gap-6 border-y border-line py-7">
        <div className="flex flex-col gap-6 lg:flex-row lg:gap-12">
          {sports.length > 1 && (
            <Group label="Sport">
              <Pill active={filters.sport === "all"} onClick={() => set("sport", "all")}>
                All
              </Pill>
              {sports.map((s) => (
                <Pill
                  key={s}
                  active={filters.sport === s}
                  onClick={() => set("sport", s)}
                >
                  {SPORT_LABEL[s]}
                </Pill>
              ))}
            </Group>
          )}

          {seriesChoices.length > 0 && (
            <Group label="Series">
              <Pill active={filters.series === "all"} onClick={() => set("series", "all")}>
                All
              </Pill>
              {seriesChoices.map((s) => (
                <Pill
                  key={s.slug}
                  active={filters.series === s.slug}
                  onClick={() => set("series", s.slug)}
                >
                  {s.shortName ?? s.name}
                </Pill>
              ))}
            </Group>
          )}

          {months.length > 1 && (
            <Group label="Month">
              <Pill active={filters.month === "all"} onClick={() => set("month", "all")}>
                Any
              </Pill>
              {months.map((m) => (
                <Pill
                  key={m}
                  active={filters.month === m}
                  onClick={() => set("month", m)}
                >
                  {monthLabel(m)}
                </Pill>
              ))}
            </Group>
          )}
        </div>

        <div className="flex flex-wrap items-center justify-between gap-y-3 border-t border-line pt-5">
          <span className="tnum text-sm text-muted" aria-live="polite" aria-busy={isFetching}>
            {String(list.length).padStart(2, "0")} fixture
            {list.length === 1 ? "" : "s"}
            {filtered && (
              <button
                type="button"
                onClick={clear}
                className="ml-4 text-faint underline-offset-4 transition-colors hover:text-bone hover:underline"
              >
                Reset
              </button>
            )}
          </span>

          <div className="flex items-center gap-2">
            <span className="hidden font-mono text-[0.6rem] uppercase tracking-[0.2em] text-faint sm:inline">
              Sort
            </span>
            <Pill active={filters.sort === "soonest"} onClick={() => set("sort", "soonest")}>
              Soonest
            </Pill>
            <Pill active={filters.sort === "price"} onClick={() => set("sort", "price")}>
              Price
            </Pill>
          </div>
        </div>
      </div>

      {error && list.length === 0 ? (
        <div className="flex flex-col items-center gap-4 py-28 text-center">
          <p className="font-display text-2xl text-bone">The schedule is not loading.</p>
          <p className="max-w-sm text-sm text-muted">
            {apiErrorMessage(error, "Could not reach the booking service.")}
          </p>
        </div>
      ) : list.length === 0 ? (
        <div className="flex flex-col items-center gap-4 py-28 text-center">
          <p className="font-display text-2xl text-bone">
            {filtered ? "No fixtures match that." : "Nothing is on sale right now."}
          </p>
          <p className="max-w-sm text-sm text-muted">
            {filtered
              ? "Try widening the filters — the full schedule is one click away."
              : "New fixtures appear here the moment they go on sale."}
          </p>
          {filtered && (
            <button
              type="button"
              onClick={clear}
              className="mt-2 rounded-full border border-line-2 px-5 py-2 text-sm text-bone transition-colors hover:border-bone"
            >
              Reset filters
            </button>
          )}
        </div>
      ) : (
        <div className={cn("transition-opacity duration-200", isFetching && "opacity-60")}>
          {reduce ? (
            <div className="mt-10 grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
              {list.map((e) => (
                <FixtureCard
                  key={e.id}
                  event={e}
                  series={e.seriesSlug ? seriesBySlug.get(e.seriesSlug) : undefined}
                  className="h-full"
                />
              ))}
            </div>
          ) : (
            <motion.div
              layout
              className="mt-10 grid gap-6 sm:grid-cols-2 lg:grid-cols-3"
            >
              <AnimatePresence mode="popLayout">
                {list.map((e) => (
                  <motion.div
                    key={e.id}
                    layout
                    initial={{ opacity: 0, scale: 0.96 }}
                    animate={{ opacity: 1, scale: 1 }}
                    exit={{ opacity: 0, scale: 0.96 }}
                    transition={{ duration: 0.35, ease: [0.16, 1, 0.3, 1] }}
                  >
                    <FixtureCard
                      event={e}
                      series={e.seriesSlug ? seriesBySlug.get(e.seriesSlug) : undefined}
                      className="h-full"
                    />
                  </motion.div>
                ))}
              </AnimatePresence>
            </motion.div>
          )}
        </div>
      )}
    </div>
  );
}
