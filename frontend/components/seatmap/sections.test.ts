import { describe, expect, it } from "vitest";
import type { EventDetail, Seat } from "@/lib/types";
import { buildSections, isLinkedSection } from "./sections";

const event = {
  tiers: [
    { id: 1, code: "gold", name: "Gold", price: 650, perks: [], remaining: 2, total: 2 },
    { id: 2, code: "standard", name: "Standard", price: 150, perks: [], remaining: 1, total: 1 },
  ],
  sections: [
    { id: 11, code: "north", name: "North Stand", tierId: 1, tierCode: "gold", side: "n", rows: 1, seatsPerRow: 2, available: 1 },
    { id: 12, code: "south", name: "South Stand", tierId: 2, tierCode: "standard", side: "s", rows: 1, seatsPerRow: 1, available: 1 },
    { id: 13, code: "west", name: "West Stand", tierId: 2, tierCode: "standard", side: "w", rows: 1, seatsPerRow: 1, available: 0 },
  ],
} as unknown as EventDetail;

const seat = (over: Partial<Seat>): Seat =>
  ({
    id: 1,
    sectionId: 11,
    sectionCode: "north",
    label: "A1",
    col: 0,
    tierCode: "gold",
    price: 650,
    status: "AVAILABLE",
    heldUntil: null,
    mine: false,
    ...over,
  }) as Seat;

describe("buildSections", () => {
  const seats = [
    seat({ id: 2, label: "A10", col: 1, status: "HELD" }),
    seat({ id: 1, label: "A2", col: 0, status: "HELD", mine: true }),
    seat({ id: 3, sectionId: 12, sectionCode: "south", label: "A1", tierCode: "standard", price: 150, status: "BOOKED" }),
    seat({ id: 4, sectionId: 99, label: "Z1" }),
  ];
  const sections = buildSections(event, seats);

  it("keeps the section code the deep links use", () => {
    expect(sections.map((s) => [s.sectionId, s.sectionCode])).toEqual([
      [11, "north"],
      [12, "south"],
    ]);
  });

  it("drops sections with no seats and seats with no section", () => {
    expect(sections.find((s) => s.sectionCode === "west")).toBeUndefined();
    expect(sections.flatMap((s) => s.seats).map((s) => s.id)).not.toContain("4");
  });

  it("sorts seats by label, numerically", () => {
    expect(sections[0].seats.map((s) => s.label)).toEqual(["A2", "A10"]);
  });

  it("keeps the user's own hold pickable and blocks everyone else's", () => {
    const [mine, theirs] = sections[0].seats;
    expect(mine.state).toBe("available");
    expect(theirs.state).toBe("held");
    expect(sections[1].seats[0].state).toBe("sold");
  });

  it("names each seat's tier", () => {
    expect(sections[0].seats[0].tierName).toBe("Gold");
  });
});

describe("isLinkedSection", () => {
  const north = { sectionCode: "north", tierId: "gold" };

  it("matches the stadium diagram's ?section= code", () => {
    expect(isLinkedSection(north, { section: "north" })).toBe(true);
    expect(isLinkedSection(north, { section: "south" })).toBe(false);
  });

  it("does not match a numeric section id", () => {
    expect(isLinkedSection(north, { section: "11" })).toBe(false);
  });

  it("matches a ?tier= link", () => {
    expect(isLinkedSection(north, { tier: "gold" })).toBe(true);
    expect(isLinkedSection(north, { tier: "standard" })).toBe(false);
  });

  it("highlights nothing without a link", () => {
    expect(isLinkedSection(north, {})).toBe(false);
    expect(isLinkedSection(north, { section: "", tier: "" })).toBe(false);
  });
});
