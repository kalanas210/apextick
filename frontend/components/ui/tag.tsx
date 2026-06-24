import { cn } from "@/lib/cn";
import { statusLabel } from "@/lib/format";
import type { FixtureStatus } from "@/data/types";

const dotColor: Record<FixtureStatus, string> = {
  onsale: "bg-muted",
  "selling-fast": "bg-accent",
  "final-release": "bg-bone",
};

export function StatusPill({
  status,
  className,
}: {
  status: FixtureStatus;
  className?: string;
}) {
  return (
    <span
      className={cn(
        "inline-flex items-center gap-2 rounded-full border border-line-2 px-3 py-1",
        "font-mono text-[0.65rem] uppercase tracking-[0.18em] text-bone-2",
        className,
      )}
    >
      <span className="relative flex h-1.5 w-1.5">
        {status === "selling-fast" && (
          <span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-accent opacity-60" />
        )}
        <span className={cn("relative h-1.5 w-1.5 rounded-full", dotColor[status])} />
      </span>
      {statusLabel(status)}
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
