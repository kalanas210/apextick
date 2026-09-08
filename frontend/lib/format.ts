/**
 * Presentation helpers. All formatting pins an explicit locale and UTC time
 * zone so server and client render byte-identical strings (no hydration drift).
 */

export function formatPrice(
  value: number,
  currency: "INR" | "GBP" | "USD",
): string {
  const config = {
    INR: { locale: "en-IN", symbol: "₹" },
    GBP: { locale: "en-GB", symbol: "£" },
    USD: { locale: "en-US", symbol: "$" },
  }[currency];
  return config.symbol + value.toLocaleString(config.locale);
}

export interface DateParts {
  weekday: string; // "Sat"
  day: string; // "21"
  month: string; // "Feb"
  monthLong: string; // "February"
  year: string; // "2026"
  full: string; // "Sat 21 Feb 2026"
}

export function formatDate(iso: string): DateParts {
  const d = new Date(`${iso}T00:00:00Z`);
  const f = (opts: Intl.DateTimeFormatOptions) =>
    new Intl.DateTimeFormat("en-GB", { timeZone: "UTC", ...opts }).format(d);
  const weekday = f({ weekday: "short" });
  const day = f({ day: "2-digit" });
  const month = f({ month: "short" });
  const monthLong = f({ month: "long" });
  const year = f({ year: "numeric" });
  return {
    weekday,
    day,
    month,
    monthLong,
    year,
    full: `${weekday} ${day} ${month} ${year}`,
  };
}

const STATUS_COPY: Record<string, string> = {
  onsale: "On sale",
  "selling-fast": "Selling fast",
  "final-release": "Final release",
};

export function statusLabel(status: string): string {
  return STATUS_COPY[status] ?? "On sale";
}

/**
 * An absolute instant, rendered in UTC and labelled as such. Admin screens
 * compare timestamps across events in different time zones, so a single frame
 * of reference beats each row silently using the reader's own offset.
 */
export function formatInstant(iso: string | null | undefined): string {
  if (!iso) return "—";
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return "—";
  return new Intl.DateTimeFormat("en-GB", {
    timeZone: "UTC",
    day: "2-digit",
    month: "short",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  }).format(d) + " UTC";
}

/** "4 minutes ago" — for the one question a gate asks about an already-used ticket. */
export function relativeTime(iso: string | null | undefined, now = Date.now()): string {
  if (!iso) return "";
  const then = new Date(iso).getTime();
  if (Number.isNaN(then)) return "";
  const seconds = Math.round((then - now) / 1000);
  const units: [Intl.RelativeTimeFormatUnit, number][] = [
    ["second", 60],
    ["minute", 60],
    ["hour", 24],
    ["day", 7],
    ["week", 4.35],
    ["month", 12],
    ["year", Infinity],
  ];
  const rtf = new Intl.RelativeTimeFormat("en-GB", { numeric: "auto" });
  let value = seconds;
  for (const [unit, span] of units) {
    if (Math.abs(value) < span) return rtf.format(Math.round(value), unit);
    value /= span;
  }
  return rtf.format(Math.round(value), "year");
}

/** Turns an event name into a URL-safe slug, for the create form's auto-fill. */
export function slugify(value: string): string {
  return value
    .toLowerCase()
    .normalize("NFKD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "")
    .slice(0, 80);
}
