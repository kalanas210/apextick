"use client";

import Link from "next/link";
import type { ReactNode } from "react";
import { cn } from "@/lib/cn";
import { apiErrorMessage } from "@/lib/api";

export interface Column<T> {
  key: string;
  header: ReactNode;
  cell: (row: T) => ReactNode;
  /** Right-aligned columns are numeric, and get tabular figures. */
  align?: "left" | "right";
  className?: string;
}

/**
 * The admin table. It owns the loading / error / empty / content branch that the
 * account order list open-codes, so every admin screen fails and empties the
 * same way rather than each inventing its own.
 */
export function DataTable<T>({
  columns,
  rows,
  rowKey,
  caption,
  isLoading,
  error,
  emptyTitle = "Nothing here yet",
  emptyHint,
  emptyAction,
  rowHref,
  footer,
  className,
}: {
  columns: Column<T>[];
  rows: T[] | undefined;
  rowKey: (row: T) => string | number;
  /** Screen-reader description of what the table lists. */
  caption: string;
  isLoading?: boolean;
  error?: unknown;
  emptyTitle?: string;
  emptyHint?: ReactNode;
  emptyAction?: ReactNode;
  rowHref?: (row: T) => string;
  footer?: ReactNode;
  className?: string;
}) {
  if (isLoading) {
    return (
      <div className="grid place-items-center py-16" aria-busy="true">
        <span className="h-6 w-6 animate-spin rounded-full border-2 border-line-2 border-t-accent" />
        <span className="sr-only">Loading {caption}…</span>
      </div>
    );
  }

  if (error) {
    return (
      <p className="rounded-2xl border border-line bg-ink-2 p-6 text-[0.86rem] text-muted">
        {apiErrorMessage(error, `Could not load ${caption}.`)}
      </p>
    );
  }

  if (!rows?.length) {
    return (
      <div className="rounded-2xl border border-line bg-ink-2 p-10 text-center">
        <p className="text-[0.9rem] text-muted">{emptyTitle}</p>
        {emptyHint && <p className="mt-2 text-[0.78rem] text-faint">{emptyHint}</p>}
        {emptyAction && <div className="mt-6 flex justify-center">{emptyAction}</div>}
      </div>
    );
  }

  return (
    <div className={cn("rounded-2xl border border-line bg-ink-2", className)}>
      <div className="overflow-x-auto">
        <table className="w-full min-w-[42rem] border-collapse text-left">
          <caption className="sr-only">{caption}</caption>
          <thead>
            <tr className="border-b border-line">
              {columns.map((c) => (
                <th
                  key={c.key}
                  scope="col"
                  className={cn(
                    "kicker whitespace-nowrap px-5 py-3 font-normal",
                    c.align === "right" && "text-right",
                    c.className,
                  )}
                >
                  {c.header}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {rows.map((row) => (
              <tr
                key={rowKey(row)}
                className="border-b border-line last:border-b-0 transition-colors hover:bg-bone/[0.03]"
              >
                {columns.map((c, i) => {
                  const content = c.cell(row);
                  const href = rowHref?.(row);
                  return (
                    <td
                      key={c.key}
                      className={cn(
                        "px-5 py-3.5 align-middle text-[0.86rem] text-bone",
                        c.align === "right" && "tnum text-right",
                        c.className,
                      )}
                    >
                      {/*
                        Only the first cell carries the row link. A whole row of
                        links would read as one entry per cell to a screen reader,
                        and nested interactive cells (buttons) could not exist.
                      */}
                      {href && i === 0 ? (
                        <Link
                          href={href}
                          className="ulink block focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent"
                        >
                          {content}
                        </Link>
                      ) : (
                        content
                      )}
                    </td>
                  );
                })}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {footer && <div className="border-t border-line px-5 py-3">{footer}</div>}
    </div>
  );
}
