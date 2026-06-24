import Link from "next/link";
import Image from "next/image";
import type { CSSProperties } from "react";
import type { Fixture } from "@/data/types";
import { getSeries } from "@/data/events";
import { unsplash } from "@/data/images";
import { formatDate, formatPrice } from "@/lib/format";
import { withAlpha } from "@/lib/color";
import { cn } from "@/lib/cn";
import { Crest } from "@/components/ui/crest";
import { StatusPill } from "@/components/ui/tag";
import { ArrowUpRight, Pin } from "@/components/ui/icons";

interface FixtureCardProps {
  fixture: Fixture;
  priority?: boolean;
  className?: string;
}

export function FixtureCard({ fixture, priority, className }: FixtureCardProps) {
  const s = getSeries(fixture.seriesId);
  const date = formatDate(fixture.date);
  const from = Math.min(...fixture.tiers.map((t) => t.price));

  return (
    <Link
      href={`/events/${fixture.slug}`}
      style={{ "--tint": s.tint } as CSSProperties}
      className={cn(
        "group relative flex flex-col overflow-hidden rounded-xl border border-line bg-ink-2",
        "transition-[transform,border-color] duration-500 ease-out hover:-translate-y-1 hover:border-tint",
        className,
      )}
    >
      <div className="relative aspect-[16/11] overflow-hidden">
        <Image
          src={unsplash(fixture.image, { w: 900, q: 75 })}
          alt={`${fixture.home.name} versus ${fixture.away.name}, ${fixture.stadium}`}
          fill
          priority={priority}
          sizes="(max-width: 640px) 100vw, (max-width: 1024px) 50vw, 33vw"
          className="object-cover transition-transform duration-[900ms] ease-out group-hover:scale-[1.06]"
        />
        <div className="absolute inset-0 bg-gradient-to-t from-ink-2 via-ink-2/15 to-transparent" />
        <div
          className="absolute inset-0 opacity-0 transition-opacity duration-500 group-hover:opacity-100"
          style={{
            background: `linear-gradient(to top, ${withAlpha(s.tint, 0.22)}, transparent 60%)`,
          }}
        />

        <div className="absolute inset-x-0 top-0 flex items-center justify-between p-4">
          <StatusPill status={fixture.status} />
          <span className="rounded-full border border-white/15 bg-ink/40 px-3 py-1 font-mono text-[0.6rem] uppercase tracking-[0.18em] text-bone/85 backdrop-blur-sm">
            {s.shortName}
          </span>
        </div>

        <div className="absolute inset-x-0 bottom-0 flex items-end justify-between gap-3 p-4">
          <div className="flex items-center gap-2">
            <Crest team={fixture.home} size={42} />
            <Crest team={fixture.away} size={42} />
          </div>
          <span className="tnum text-right text-[0.8rem] leading-tight text-bone/85">
            {date.day} {date.month}
            <span className="block text-bone/55">{fixture.time}</span>
          </span>
        </div>
      </div>

      <div className="flex flex-1 flex-col p-5">
        <span className="kicker text-tint">{fixture.stage}</span>
        <h3 className="mt-3 font-display text-[1.3rem] font-semibold leading-[1.05] tracking-tight">
          {fixture.home.name}{" "}
          <span className="text-faint">v</span> {fixture.away.name}
        </h3>
        <div className="mt-2.5 flex items-center gap-1.5 text-[0.85rem] text-muted">
          <Pin className="h-3.5 w-3.5" />
          {fixture.stadium}, {fixture.city}
        </div>

        <div className="mt-5 flex items-center justify-between border-t border-line pt-4">
          <div>
            <span className="block text-[0.6rem] uppercase tracking-[0.16em] text-faint">
              From
            </span>
            <span className="tnum text-[0.95rem] text-bone">
              {formatPrice(from, s.currency)}
            </span>
          </div>
          <span className="grid h-9 w-9 place-items-center rounded-full border border-line-2 text-bone transition-colors duration-300 group-hover:border-tint group-hover:bg-tint group-hover:text-accent-ink">
            <ArrowUpRight className="h-4 w-4" />
          </span>
        </div>
      </div>
    </Link>
  );
}
