"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import type { ReactNode } from "react";
import { cn } from "@/lib/cn";
import { X } from "./icons";

export type NoticeTone = "info" | "success" | "error";

const TONE: Record<NoticeTone, string> = {
  info: "border-line-2 bg-bone/[0.04] text-muted",
  success: "border-accent/40 bg-accent/10 text-accent",
  error: "border-[#ff6b6b]/40 bg-[#ff6b6b]/10 text-[#ff6b6b]",
};

/**
 * An inline banner, not a floating toast. The site has no floating surfaces, and
 * feedback that belongs to a form reads better next to it than in a corner.
 */
export function Notice({
  tone = "info",
  className,
  onDismiss,
  children,
}: {
  tone?: NoticeTone;
  className?: string;
  onDismiss?: () => void;
  children: ReactNode;
}) {
  return (
    <div
      role={tone === "error" ? "alert" : "status"}
      className={cn(
        "flex items-start gap-3 rounded-lg border px-3.5 py-2.5 text-[0.82rem]",
        TONE[tone],
        className,
      )}
    >
      <span className="min-w-0 flex-1">{children}</span>
      {onDismiss && (
        <button
          type="button"
          onClick={onDismiss}
          className="-mr-1 shrink-0 rounded p-0.5 opacity-70 transition-opacity hover:opacity-100"
        >
          <X className="h-4 w-4" />
          <span className="sr-only">Dismiss</span>
        </button>
      )}
    </div>
  );
}

export interface NoticeState {
  tone: NoticeTone;
  message: ReactNode;
}

/** One transient message at a time, cleared after `timeoutMs` unless dismissed first. */
export function useNotice(timeoutMs = 6000) {
  const [notice, setNotice] = useState<NoticeState | null>(null);
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);

  const clear = useCallback(() => {
    if (timer.current) clearTimeout(timer.current);
    setNotice(null);
  }, []);

  const show = useCallback(
    (tone: NoticeTone, message: ReactNode) => {
      if (timer.current) clearTimeout(timer.current);
      setNotice({ tone, message });
      // Errors stay put: they usually describe something the reader has to act on.
      if (tone !== "error" && timeoutMs > 0) {
        timer.current = setTimeout(() => setNotice(null), timeoutMs);
      }
    },
    [timeoutMs],
  );

  useEffect(() => () => {
    if (timer.current) clearTimeout(timer.current);
  }, []);

  return { notice, show, clear };
}
