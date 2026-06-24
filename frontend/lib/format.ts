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
