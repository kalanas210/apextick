import type { ReactNode } from "react";
import { cn } from "@/lib/cn";

interface MarqueeProps {
  children: ReactNode;
  /** Seconds for one full loop. Higher is slower. */
  speed?: number;
  reverse?: boolean;
  className?: string;
  fade?: boolean;
}

/**
 * Continuous horizontal ticker. The track holds two identical halves and slides
 * by 50%, so the loop is seamless. Pauses on hover, stops under reduced motion.
 */
export function Marquee({
  children,
  speed = 36,
  reverse = false,
  className,
  fade = true,
}: MarqueeProps) {
  return (
    <div className={cn("marquee overflow-hidden", fade && "mask-fade-r", className)}>
      <div
        className={cn(
          "flex w-max",
          reverse ? "marquee-track-rev" : "marquee-track",
        )}
        style={{ animationDuration: `${speed}s` }}
      >
        <div className="flex shrink-0">{children}</div>
        <div className="flex shrink-0" aria-hidden>
          {children}
        </div>
      </div>
    </div>
  );
}
