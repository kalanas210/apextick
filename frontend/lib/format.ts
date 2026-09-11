/**
 * Presentation helpers. All formatting pins an explicit locale and UTC time
 * zone so server and client render byte-identical strings (no hydration drift).
 */

/**
 * The market each currency is normally shown to, so the symbol and digit
 * grouping read naturally (₹1,25,000, not ₹125,000). Any other currency still
 * formats, with en-GB grouping.
 */
const CURRENCY_LOCALE: Record<string, string> = {
  INR: "en-IN",
  GBP: "en-GB",
  USD: "en-US",
  EUR: "en-IE",
  LKR: "en-LK",
};

const currencyFormats = new Map<string, Intl.NumberFormat | null>();

/** Null when `code` is not shaped like an ISO 4217 code, which Intl rejects. */
function currencyFormat(code: string, whole = false): Intl.NumberFormat | null {
  const key = `${code}|${whole}`;
  let format = currencyFormats.get(key);
  if (format === undefined) {
    try {
      format = new Intl.NumberFormat(CURRENCY_LOCALE[code] ?? "en-GB", {
        style: "currency",
        currency: code,
        currencyDisplay: "narrowSymbol",
        ...(whole && { minimumFractionDigits: 0, maximumFractionDigits: 0 }),
      });
    } catch {
      format = null;
    }
    currencyFormats.set(key, format);
  }
  return format;
}

/**
 * A price in its currency's minor units: £57.75 and £5.50 (never £5.5), ¥1,200.
 * A whole amount drops the decimals (£55), so catalogue prices stay clean. An
 * unrecognised but well-formed code formats as "XYZ 5.50", and a malformed one
 * falls back to the code and the number rather than throwing.
 */
export function formatPrice(value: number, currency: string | null | undefined): string {
  const code = (currency ?? "").trim().toUpperCase();
  const format = currencyFormat(code);
  // 2 for most currencies, 0 for JPY, 3 for BHD
  const digits = format?.resolvedOptions().maximumFractionDigits ?? 2;
  const scale = 10 ** digits;
  const whole = Math.round(value * scale) % scale === 0;

  if (!format) {
    const amount = new Intl.NumberFormat("en-GB", {
      minimumFractionDigits: whole ? 0 : digits,
      maximumFractionDigits: whole ? 0 : digits,
    }).format(value);
    return code ? `${code} ${amount}` : amount;
  }
  return whole ? currencyFormat(code, true)!.format(value) : format.format(value);
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
