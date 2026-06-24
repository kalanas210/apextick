import Image from "next/image";
import type { Team } from "@/data/types";
import { cn } from "@/lib/cn";
import { withAlpha } from "@/lib/color";

interface CrestProps {
  team: Team;
  size?: number;
  className?: string;
}

/**
 * National sides show a public-domain country flag, clubs that supply a badge
 * render it on a clean tile, and everything else falls back to a coded
 * monogram crest.
 */
export function Crest({ team, size = 56, className }: CrestProps) {
  if (team.flag) {
    return (
      <div
        className={cn(
          "relative shrink-0 overflow-hidden rounded-[7px] border border-line-2",
          className,
        )}
        style={{ width: size, height: size }}
        aria-hidden
      >
        <Image
          src={`https://flagcdn.com/w160/${team.flag}.png`}
          alt=""
          fill
          sizes={`${size}px`}
          className="object-cover"
        />
      </div>
    );
  }

  if (team.logo) {
    return (
      <div
        className={cn(
          "relative shrink-0 overflow-hidden rounded-[7px] border border-line-2 bg-bone",
          className,
        )}
        style={{ width: size, height: size }}
        aria-hidden
      >
        <Image
          src={team.logo}
          alt=""
          fill
          sizes={`${size}px`}
          className="object-contain p-[14%]"
        />
      </div>
    );
  }

  return (
    <div
      className={cn(
        "relative grid shrink-0 place-items-center overflow-hidden rounded-[7px] border",
        className,
      )}
      style={{
        width: size,
        height: size,
        borderColor: withAlpha(team.color, 0.5),
        background: `linear-gradient(152deg, ${withAlpha(team.color, 0.2)}, ${withAlpha(
          team.color,
          0.04,
        )})`,
      }}
      aria-hidden
    >
      <span
        className="absolute inset-x-0 top-0 h-[3px]"
        style={{ background: team.color }}
      />
      <span
        className="font-display font-bold leading-none"
        style={{ color: team.color, fontSize: Math.round(size * 0.4) }}
      >
        {team.monogram}
      </span>
    </div>
  );
}

interface MatchupProps {
  home: Team;
  away: Team;
  size?: number;
  className?: string;
}

/** Two crests separated by a hairline divider and a "v" mark. */
export function Matchup({ home, away, size = 48, className }: MatchupProps) {
  return (
    <div className={cn("flex items-center gap-3", className)}>
      <Crest team={home} size={size} />
      <span className="font-mono text-xs uppercase tracking-[0.2em] text-faint">
        v
      </span>
      <Crest team={away} size={size} />
    </div>
  );
}
