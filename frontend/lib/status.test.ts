import { describe, expect, it } from "vitest";
import {
  EVENT_LABEL,
  EVENT_STATUSES,
  EVENT_TONE,
  ORDER_LABEL,
  ORDER_STATUSES,
  ORDER_TONE,
  SEAT_TONE,
  TICKET_TONE,
} from "./status";

describe("status vocabulary", () => {
  it("lists every event status exactly once, in lifecycle order", () => {
    expect(new Set(EVENT_STATUSES).size).toBe(EVENT_STATUSES.length);
    expect([...EVENT_STATUSES].sort()).toEqual(Object.keys(EVENT_LABEL).sort());
    expect(EVENT_STATUSES[0]).toBe("draft");
    expect(EVENT_STATUSES.at(-1)).toBe("cancelled");
  });

  it("lists every order status exactly once", () => {
    expect(new Set(ORDER_STATUSES).size).toBe(ORDER_STATUSES.length);
    expect([...ORDER_STATUSES].sort()).toEqual(Object.keys(ORDER_LABEL).sort());
  });

  it("gives every status both a label and a tone", () => {
    for (const s of EVENT_STATUSES) {
      expect(EVENT_LABEL[s]).toBeTruthy();
      expect(EVENT_TONE[s]).toBeTruthy();
    }
    for (const s of ORDER_STATUSES) {
      expect(ORDER_LABEL[s]).toBeTruthy();
      expect(ORDER_TONE[s]).toBeTruthy();
    }
  });

  it("styles every pill with both a border and a text colour", () => {
    const tones = [
      ...Object.values(EVENT_TONE),
      ...Object.values(ORDER_TONE),
      ...Object.values(SEAT_TONE),
      ...Object.values(TICKET_TONE),
    ];
    for (const tone of tones) {
      expect(tone).toMatch(/(^|\s)border-\S+/);
      expect(tone).toMatch(/(^|\s)text-\S+/);
    }
  });

  it("calls a paid order confirmed", () => {
    expect(ORDER_LABEL.PAID).toBe("Confirmed");
  });
});
