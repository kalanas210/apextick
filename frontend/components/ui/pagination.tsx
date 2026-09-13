"use client";

import { cn } from "@/lib/cn";
import { ChevronLeft, ChevronRight } from "./icons";

/**
 * Paging controls for the API's PageResponse envelope, whose `page` is zero-based.
 * That conversion happens here and nowhere else.
 */
export function Pagination({
  page,
  totalPages,
  totalElements,
  onPage,
  className,
}: {
  page: number;
  totalPages: number;
  totalElements: number;
  onPage: (page: number) => void;
  className?: string;
}) {
  if (totalPages <= 1) {
    return totalElements > 0 ? (
      <p className={cn("text-[0.78rem] text-faint", className)}>
        <span className="tnum">{totalElements}</span> in total
      </p>
    ) : null;
  }

  const step = "rounded-full border border-line-2 p-1.5 text-muted transition-colors hover:border-bone hover:text-bone disabled:pointer-events-none disabled:opacity-40";

  return (
    <div className={cn("flex items-center justify-between gap-4", className)}>
      <p className="text-[0.78rem] text-faint">
        Page <span className="tnum">{page + 1}</span> of{" "}
        <span className="tnum">{totalPages}</span> ·{" "}
        <span className="tnum">{totalElements}</span> in total
      </p>
      <div className="flex items-center gap-2">
        <button type="button" className={step} onClick={() => onPage(page - 1)} disabled={page <= 0}>
          <ChevronLeft className="h-4 w-4" />
          <span className="sr-only">Previous page</span>
        </button>
        <button
          type="button"
          className={step}
          onClick={() => onPage(page + 1)}
          disabled={page >= totalPages - 1}
        >
          <ChevronRight className="h-4 w-4" />
          <span className="sr-only">Next page</span>
        </button>
      </div>
    </div>
  );
}
