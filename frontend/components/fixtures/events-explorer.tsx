"use client";

import { useMemo, useState } from "react";
import { AnimatePresence, motion, useReducedMotion } from "framer-motion";
import type { SeriesId, Sport } from "@/data/types";
import { fixtures, getSeries, seriesList } from "@/data/events";
import { formatDate } from "@/lib/format";
import { cn } from "@/lib/cn";
import { FixtureCard } from "./fixture-card";

type SeriesChoice = SeriesId | "all";
type SportChoice = Sport | "all";
type SortChoice = "soonest" | "price";

const MONTHS = Array.from(new Set(fixtures.map((f) => f.date.slice(0, 7)))).sort();

function monthLabel(month: string) {
  const d = formatDate(`${month}-01`);
  return `${d.month} ${d.year}`;
}

function minPrice(id: string) {
  const f = fixtures.find((x) => x.id === id)!;
  return Math.min(...f.tiers.map((t) => t.price));
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

export function EventsExplorer({
  initialSeries = "all",
}: {
  initialSeries?: SeriesChoice;
}) {
  const reduce = useReducedMotion();
  const [sport, setSport] = useState<SportChoice>("all");
  const [series, setSeries] = useState<SeriesChoice>(initialSeries);
  const [month, setMonth] = useState<string>("all");
  const [sort, setSort] = useState<SortChoice>("soonest");

  const list = useMemo(() => {
    const filtered = fixtures.filter((f) => {
      if (sport !== "all" && getSeries(f.seriesId).sport !== sport) return false;
      if (series !== "all" && f.seriesId !== series) return false;
      if (month !== "all" && !f.date.startsWith(month)) return false;
      return true;
    });
    return filtered.sort((a, b) =>
      sort === "price"
        ? minPrice(a.id) - minPrice(b.id)
        : a.date < b.date
          ? -1
          : a.date > b.date
            ? 1
            : 0,
    );
  }, [sport, series, month, sort]);

  const clear = () => {
    setSport("all");
    setSeries("all");
    setMonth("all");
    setSort("soonest");
  };

  const hasFilters =
    sport !== "all" || series !== "all" || month !== "all" || sort !== "soonest";

  return (
    <div>
      <div className="flex flex-col gap-6 border-y border-line py-7">
        <div className="flex flex-col gap-6 lg:flex-row lg:gap-12">
          <Group label="Sport">
            <Pill active={sport === "all"} onClick={() => setSport("all")}>
              All
            </Pill>
            <Pill active={sport === "cricket"} onClick={() => setSport("cricket")}>
              Cricket
            </Pill>
            <Pill
              active={sport === "football"}
              onClick={() => setSport("football")}
            >
              Football
            </Pill>
          </Group>

          <Group label="Series">
            <Pill active={series === "all"} onClick={() => setSeries("all")}>
              All
            </Pill>
            {seriesList.map((s) => (
              <Pill
                key={s.id}
                active={series === s.id}
                onClick={() => setSeries(s.id)}
              >
                {s.shortName}
              </Pill>
            ))}
          </Group>

          <Group label="Month">
            <Pill active={month === "all"} onClick={() => setMonth("all")}>
              Any
            </Pill>
            {MONTHS.map((m) => (
              <Pill key={m} active={month === m} onClick={() => setMonth(m)}>
                {monthLabel(m)}
              </Pill>
            ))}
          </Group>
        </div>

        <div className="flex flex-wrap items-center justify-between gap-y-3 border-t border-line pt-5">
          <span className="tnum text-sm text-muted">
            {String(list.length).padStart(2, "0")} fixture
            {list.length === 1 ? "" : "s"}
            {hasFilters && (
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
            <Pill active={sort === "soonest"} onClick={() => setSort("soonest")}>
              Soonest
            </Pill>
            <Pill active={sort === "price"} onClick={() => setSort("price")}>
              Price
            </Pill>
          </div>
        </div>
      </div>

      {list.length === 0 ? (
        <div className="flex flex-col items-center gap-4 py-28 text-center">
          <p className="font-display text-2xl text-bone">No fixtures match that.</p>
          <p className="max-w-sm text-sm text-muted">
            Try widening the filters. The full schedule has sixteen fixtures
            across three series.
          </p>
          <button
            type="button"
            onClick={clear}
            className="mt-2 rounded-full border border-line-2 px-5 py-2 text-sm text-bone transition-colors hover:border-bone"
          >
            Reset filters
          </button>
        </div>
      ) : reduce ? (
        <div className="mt-10 grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
          {list.map((f) => (
            <FixtureCard key={f.id} fixture={f} className="h-full" />
          ))}
        </div>
      ) : (
        <motion.div
          layout
          className="mt-10 grid gap-6 sm:grid-cols-2 lg:grid-cols-3"
        >
          <AnimatePresence mode="popLayout">
            {list.map((f) => (
              <motion.div
                key={f.id}
                layout
                initial={{ opacity: 0, scale: 0.96 }}
                animate={{ opacity: 1, scale: 1 }}
                exit={{ opacity: 0, scale: 0.96 }}
                transition={{ duration: 0.35, ease: [0.16, 1, 0.3, 1] }}
              >
                <FixtureCard fixture={f} className="h-full" />
              </motion.div>
            ))}
          </AnimatePresence>
        </motion.div>
      )}
    </div>
  );
}
