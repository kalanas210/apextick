import { describe, expect, it } from "vitest";
import type { Seat } from "@/lib/types";
import { mergeSeatChanges } from "./realtime-merge";

const seat = (over: Partial<Seat>): Seat =>
  ({
    id: 1,
    label: "A1",
    row: 0,
    col: 0,
    sectionId: 11,
    sectionCode: "north",
    tierId: 1,
    tierCode: "gold",
    price: 650,
    status: "AVAILABLE",
    heldUntil: null,
    mine: false,
    ...over,
  }) as Seat;

describe("mergeSeatChanges", () => {
  it("shows another buyer's hold, and their release", () => {
    const seats = [seat({ id: 1 }), seat({ id: 2, status: "HELD", heldUntil: "2026-09-13T10:05:00Z" })];

    const merged = mergeSeatChanges(seats, [
      { seatId: 1, status: "HELD", heldUntil: "2026-09-13T10:06:00Z" },
      { seatId: 2, status: "AVAILABLE", heldUntil: null },
    ]);

    expect(merged?.map((s) => [s.id, s.status, s.heldUntil])).toEqual([
      [1, "HELD", "2026-09-13T10:06:00Z"],
      [2, "AVAILABLE", null],
    ]);
  });

  it("keeps the buyer's own hold selected when its broadcast comes back", () => {
    const mine = seat({ id: 3, status: "HELD", mine: true, heldUntil: "2026-09-13T10:05:00Z" });

    expect(mergeSeatChanges([mine], [{ seatId: 3, status: "HELD", heldUntil: "2026-09-13T10:09:00Z" }])).toEqual([mine]);
  });

  it("lets go of the buyer's own seat once it is released or booked for them", () => {
    const merged = mergeSeatChanges([seat({ id: 4, status: "HELD", mine: true })], [
      { seatId: 4, status: "BOOKED", heldUntil: null },
    ]);

    expect(merged?.[0]).toMatchObject({ status: "BOOKED", mine: false });
  });

  it("marks a seat an admin has taken off sale", () => {
    expect(mergeSeatChanges([seat({ id: 5 })], [{ seatId: 5, status: "BLOCKED", heldUntil: null }])?.[0].status).toBe(
      "BLOCKED",
    );
  });

  it("leaves the cache alone before the seats have loaded", () => {
    expect(mergeSeatChanges(undefined, [{ seatId: 1, status: "HELD", heldUntil: null }])).toBeUndefined();
  });
});
