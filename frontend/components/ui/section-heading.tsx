import Link from "next/link";
import type { ReactNode } from "react";
import { cn } from "@/lib/cn";
import { ArrowUpRight } from "./icons";

interface SectionHeadingProps {
  kicker?: string;
  index?: string;
  title: ReactNode;
  description?: ReactNode;
  action?: { href: string; label: string };
  className?: string;
}

/**
 * Editorial section header: a hairline, a mono kicker with optional index,
 * a tight display title, and an optional supporting line plus action link.
 */
export function SectionHeading({
  kicker,
  index,
  title,
  description,
  action,
  className,
}: SectionHeadingProps) {
  return (
    <header className={cn("border-t border-line pt-5", className)}>
      <div className="flex items-center justify-between gap-4">
        <div className="flex items-center gap-4">
          {index && <span className="tnum text-xs text-faint">{index}</span>}
          {kicker && <span className="kicker">{kicker}</span>}
        </div>
        {action && (
          <Link
            href={action.href}
            className="group hidden items-center gap-1.5 font-mono text-[0.7rem] uppercase tracking-[0.18em] text-muted transition-colors hover:text-bone sm:inline-flex"
          >
            {action.label}
            <ArrowUpRight className="h-3.5 w-3.5 transition-transform duration-300 group-hover:translate-x-0.5 group-hover:-translate-y-0.5" />
          </Link>
        )}
      </div>

      <div className="mt-7 grid gap-6 lg:grid-cols-12 lg:items-end">
        <h2 className="display col-span-12 text-[clamp(2.1rem,5.4vw,4.4rem)] lg:col-span-8">
          {title}
        </h2>
        {description && (
          <p className="col-span-12 max-w-md text-[0.98rem] leading-relaxed text-muted lg:col-span-4">
            {description}
          </p>
        )}
      </div>
    </header>
  );
}
