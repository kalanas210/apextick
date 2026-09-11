import { describe, expect, it } from "vitest";
import {
  formatDate,
  formatInstant,
  formatPrice,
  relativeTime,
  slugify,
  statusLabel,
} from "./format";

/** Intl separates a code from the amount with a no-break space. */
const plain = (s: string) => s.replace(/ /g, " ");

describe("formatPrice", () => {
  it("keeps whole amounts free of decimals", () => {
    expect(formatPrice(55, "GBP")).toBe("£55");
    expect(formatPrice(1500, "USD")).toBe("$1,500");
  });

  it("shows every minor digit once there are pence", () => {
    expect(formatPrice(5.5, "GBP")).toBe("£5.50");
    expect(formatPrice(115.5, "GBP")).toBe("£115.50");
    expect(formatPrice(57.75, "GBP")).toBe("£57.75");
    expect(formatPrice(2.75, "USD")).toBe("$2.75");
  });

  it("groups digits the way each market reads them", () => {
    expect(formatPrice(125000, "INR")).toBe("₹1,25,000");
    expect(formatPrice(1234.5, "INR")).toBe("₹1,234.50");
  });

  it("formats currencies beyond the three the catalogue uses", () => {
    expect(formatPrice(12.5, "EUR")).toBe("€12.50");
    expect(plain(formatPrice(1500.5, "LKR"))).toBe("Rs 1,500.50");
  });

  it("uses the currency's own minor units", () => {
    expect(formatPrice(1200, "JPY")).toBe("¥1,200");
    expect(formatPrice(1200.4, "JPY")).toBe("¥1,200");
    expect(plain(formatPrice(5.5, "BHD"))).toBe("BHD 5.500");
  });

  it("is not fooled by floating-point noise", () => {
    expect(formatPrice(0.1 + 0.2, "GBP")).toBe("£0.30");
    expect(formatPrice(110.00000000001, "GBP")).toBe("£110");
  });

  it("accepts a lower-case code", () => {
    expect(formatPrice(5.5, "gbp")).toBe("£5.50");
  });

  it("never throws on a currency it cannot place", () => {
    expect(plain(formatPrice(5.5, "XYZ"))).toBe("XYZ 5.50");
    expect(formatPrice(5.5, "EURO")).toBe("EURO 5.50");
    expect(formatPrice(5.5, "")).toBe("5.50");
    expect(formatPrice(40, null)).toBe("40");
  });
});

describe("formatDate", () => {
  it("splits a calendar date into display parts", () => {
    expect(formatDate("2026-02-21")).toEqual({
      weekday: "Sat",
      day: "21",
      month: "Feb",
      monthLong: "February",
      year: "2026",
      full: "Sat 21 Feb 2026",
    });
  });

  it("reads the date in UTC, so it never slips a day at midnight", () => {
    expect(formatDate("2026-01-01").full).toBe("Thu 01 Jan 2026");
  });
});

describe("statusLabel", () => {
  it("maps the public sale statuses to copy", () => {
    expect(statusLabel("selling-fast")).toBe("Selling fast");
    expect(statusLabel("final-release")).toBe("Final release");
  });

  it("falls back to 'On sale' for anything it does not know", () => {
    expect(statusLabel("mystery")).toBe("On sale");
  });
});

describe("formatInstant", () => {
  it("renders an instant in UTC and says so", () => {
    expect(formatInstant("2026-02-21T19:30:00+05:30")).toBe("21 Feb 2026, 14:00 UTC");
  });

  it("shows a dash for a missing or unparseable value", () => {
    expect(formatInstant(null)).toBe("—");
    expect(formatInstant(undefined)).toBe("—");
    expect(formatInstant("not a date")).toBe("—");
  });
});

describe("relativeTime", () => {
  const now = Date.parse("2026-02-21T12:00:00Z");

  it("picks the largest sensible unit", () => {
    expect(relativeTime("2026-02-21T11:56:00Z", now)).toBe("4 minutes ago");
    expect(relativeTime("2026-02-21T09:00:00Z", now)).toBe("3 hours ago");
    expect(relativeTime("2026-02-19T12:00:00Z", now)).toBe("2 days ago");
  });

  it("uses natural words for the nearest units", () => {
    expect(relativeTime("2026-02-20T12:00:00Z", now)).toBe("yesterday");
    expect(relativeTime("2026-02-21T12:00:00Z", now)).toBe("now");
  });

  it("returns an empty string for a missing or unparseable value", () => {
    expect(relativeTime(null, now)).toBe("");
    expect(relativeTime("nope", now)).toBe("");
  });
});

describe("slugify", () => {
  it("lower-cases, strips accents and joins words with hyphens", () => {
    expect(slugify("Atlético Madrid v Real Sociedad")).toBe("atletico-madrid-v-real-sociedad");
  });

  it("trims leading and trailing separators", () => {
    expect(slugify("  --India vs. Pakistan!--  ")).toBe("india-vs-pakistan");
  });

  it("caps the slug at 80 characters", () => {
    expect(slugify("a".repeat(120))).toHaveLength(80);
  });
});
