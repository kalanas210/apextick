import { describe, expect, it } from "vitest";
import { formatInstant } from "./format";
import { salesState } from "./sales-window";
import type { EventDetail, EventStatus } from "./types";

/**
 * These cases are the same ones SalesWindow.assertOpen answers on the server, in
 * the same order, so a drift in either direction shows up as a failure here.
 */

const NOW = Date.parse("2026-02-21T12:00:00Z");
const hours = (n: number) => new Date(NOW + n * 3_600_000).toISOString();

const event = (over: Partial<EventDetail> = {}): EventDetail =>
  ({
    status: "onsale" as EventStatus,
    startsAt: hours(48),
    salesStartAt: hours(-48),
    salesEndAt: hours(46),
    ...over,
  }) as EventDetail;

describe("salesState", () => {
  it("is open inside the window, with nothing to say about it", () => {
    const state = salesState(event(), NOW);
    expect(state.open).toBe(true);
    expect(state.code).toBe("open");
    expect(state.detail).toBe("");
  });

  it("refuses every status the API will not sell", () => {
    const refused: EventStatus[] = ["sold-out", "cancelled", "draft"];
    expect(refused.map((status) => salesState(event({ status }), NOW).code)).toEqual([
      "sold-out",
      "cancelled",
      "not-on-sale",
    ]);
  });

  it("keeps selling through every purchasable status", () => {
    const selling: EventStatus[] = ["onsale", "selling-fast", "final-release"];
    expect(selling.every((status) => salesState(event({ status }), NOW).open)).toBe(true);
  });

  it("names the day sales open when the window is still ahead", () => {
    const salesStartAt = hours(6);
    const state = salesState(event({ salesStartAt }), NOW);
    expect(state.open).toBe(false);
    expect(state.code).toBe("not-yet-open");
    expect(state.detail).toBe(`Sales open on ${formatInstant(salesStartAt)}.`);
  });

  it("opens exactly on the instant sales start", () => {
    expect(salesState(event({ salesStartAt: hours(0) }), NOW).open).toBe(true);
  });

  it("closes once the sales end has passed, but not on the instant itself", () => {
    expect(salesState(event({ salesEndAt: hours(0) }), NOW).open).toBe(true);
    expect(salesState(event({ salesEndAt: hours(-1) }), NOW).code).toBe("closed");
  });

  it("never sells past kickoff, even with no explicit sales end", () => {
    const started = event({ salesEndAt: null, startsAt: hours(0) });
    expect(salesState(started, NOW).code).toBe("started");
    expect(salesState(started, NOW).title).toBe("Sales closed");
    expect(salesState(event({ salesEndAt: null, startsAt: hours(1) }), NOW).open).toBe(true);
  });

  it("treats missing or unparseable sales bounds as no bound at all", () => {
    expect(salesState(event({ salesStartAt: null, salesEndAt: undefined }), NOW).open).toBe(true);
    expect(salesState(event({ salesEndAt: "not a date" }), NOW).open).toBe(true);
  });

  it("reports the status before the clock — a sold-out fixture is not 'not on sale yet'", () => {
    const state = salesState(event({ status: "sold-out", salesStartAt: hours(6) }), NOW);
    expect(state.code).toBe("sold-out");
  });
});
