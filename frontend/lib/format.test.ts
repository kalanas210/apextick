import { describe, expect, it } from "vitest";
import { formatDate, formatInstant, relativeTime, slugify, statusLabel } from "./format";

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
