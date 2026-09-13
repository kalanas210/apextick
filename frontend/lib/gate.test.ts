import { describe, expect, it } from "vitest";
import { kickoffLabel, refusalOf } from "./gate";

describe("refusalOf", () => {
  it("keeps when an already-used ticket was first scanned", () => {
    expect(refusalOf(409, { code: "TICKET_ALREADY_USED", usedAt: "2026-09-26T13:25:00Z" })).toEqual({
      kind: "already",
      usedAt: "2026-09-26T13:25:00Z",
    });
  });

  it("names the event a wrong-event ticket belongs to", () => {
    expect(
      refusalOf(409, {
        code: "TICKET_WRONG_EVENT",
        ticketEventName: "India v Pakistan",
        ticketEventStartsAt: "2026-09-26T13:30:00Z",
        ticketEventTimeZone: "Asia/Kolkata",
        ticketEventVenue: "Narendra Modi Stadium",
      }),
    ).toEqual({
      kind: "wrong-event",
      name: "India v Pakistan",
      startsAt: "2026-09-26T13:30:00Z",
      timeZone: "Asia/Kolkata",
      venue: "Narendra Modi Stadium",
    });
  });

  it("tells a shut gate apart from a bad ticket", () => {
    expect(refusalOf(409, { code: "EVENT_NOT_ADMITTING", eventStatus: "cancelled" })).toEqual({
      kind: "event-closed",
      status: "cancelled",
    });
    expect(refusalOf(409, { code: "TICKET_CANCELLED" })).toEqual({ kind: "cancelled" });
  });

  it("calls a 404 an unknown ticket and anything else an error", () => {
    expect(refusalOf(404, { code: "NOT_FOUND" })).toEqual({ kind: "unknown" });
    expect(refusalOf(500, undefined)).toEqual({ kind: "error" });
    expect(refusalOf(undefined, undefined)).toEqual({ kind: "error" });
  });
});

describe("kickoffLabel", () => {
  it("reads the kickoff on the event's own clock, not the scanner's", () => {
    // 13:30 UTC is 19:00 in Ahmedabad
    const label = kickoffLabel("2026-09-26T13:30:00Z", "Asia/Kolkata");
    expect(label).toContain("19:00");
    expect(label).toContain("Sat");
  });

  it("falls back to UTC, and says so, for a zone it does not know", () => {
    expect(kickoffLabel("2026-09-26T13:30:00Z", "Not/AZone")).toMatch(/13:30 UTC$/);
  });

  it("is empty for a missing or broken timestamp", () => {
    expect(kickoffLabel(null, "UTC")).toBe("");
    expect(kickoffLabel("not a date", "UTC")).toBe("");
  });
});
