import { cn } from "@/lib/cn";
import { EVENT_LABEL } from "@/lib/status";
import type { EventStatus } from "@/lib/types";

/**
 * The dot carries tone, never meaning — the pill always says the word too, and
 * it says the event's real status. It used to know only the three selling
 * states, so a sold-out, cancelled or unannounced fixture read "On sale".
 */
const dotColor: Record<EventStatus, string> = {
  onsale: "bg-muted",
  "selling-fast": "bg-accent",
  "final-release": "bg-bone",
  "sold-out": "bg-faint",
  draft: "bg-faint",
  cancelled: "bg-[#ff6b6b]",
};

export function StatusPill({
  status,
  className,
}: {
  status: EventStatus;
  className?: string;
}) {
  return (
    <span
      className={cn(
        "inline-flex items-center gap-2 rounded-full border border-line-2 px-3 py-1",
        "font-mono text-[0.65rem] uppercase tracking-[0.18em] text-bone-2",
        status === "cancelled" && "border-[#ff6b6b]/40 text-[#ff6b6b]",
        className,
      )}
    >
      <span className="relative flex h-1.5 w-1.5">
        {status === "selling-fast" && (
          <span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-accent opacity-60" />
        )}
        <span className={cn("relative h-1.5 w-1.5 rounded-full", dotColor[status] ?? "bg-muted")} />
      </span>
      {EVENT_LABEL[status] ?? status}
    </span>
  );
}

export function Pill({
  children,
  className,
}: {
  children: React.ReactNode;
  className?: string;
}) {
  return (
    <span
      className={cn(
        "inline-flex items-center gap-2 rounded-full border border-line-2 px-3 py-1",
        "font-mono text-[0.65rem] uppercase tracking-[0.18em] text-muted",
        className,
      )}
    >
      {children}
    </span>
  );
}
