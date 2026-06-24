import type { Fixture } from "@/data/types";

export type SeatState = "available" | "held" | "sold";

export interface GeneratedSeat {
  id: string;
  sectionId: string;
  sectionName: string;
  side: "n" | "s" | "e" | "w";
  tierId: string;
  tierName: string;
  row: number;
  col: number;
  label: string;
  state: SeatState;
  price: number;
}

/** Deterministic 0..1 hash so seat layouts are stable across server and client. */
function hash(input: string): number {
  let h = 2166136261;
  for (let i = 0; i < input.length; i++) {
    h ^= input.charCodeAt(i);
    h = Math.imul(h, 16777619);
  }
  return (h >>> 0) / 4294967295;
}

function rowLetter(row: number): string {
  return String.fromCharCode(65 + row);
}

function stateFor(tierId: string, seed: number): SeatState {
  // Better seats sell harder, giving the map a believable spread.
  const sold = tierId === "gold" ? 0.42 : tierId === "premium" ? 0.26 : 0.16;
  const held = sold + 0.07;
  if (seed < sold) return "sold";
  if (seed < held) return "held";
  return "available";
}

/** Build every seat for a fixture, grouped by section in render order. */
export function buildSeatSections(
  fixture: Fixture,
): { sectionId: string; sectionName: string; side: GeneratedSeat["side"]; tierId: string; seats: GeneratedSeat[] }[] {
  return fixture.sections.map((section) => {
    const tier = fixture.tiers.find((t) => t.id === section.tierId)!;
    const seats: GeneratedSeat[] = [];
    for (let r = 0; r < section.rows; r++) {
      for (let c = 0; c < section.seatsPerRow; c++) {
        const label = `${rowLetter(r)}${c + 1}`;
        const seed = hash(`${fixture.id}:${section.id}:${label}`);
        seats.push({
          id: `${section.id}-${label}`,
          sectionId: section.id,
          sectionName: section.name,
          side: section.side,
          tierId: section.tierId,
          tierName: tier.name,
          row: r,
          col: c,
          label,
          state: stateFor(section.tierId, seed),
          price: tier.price,
        });
      }
    }
    return {
      sectionId: section.id,
      sectionName: section.name,
      side: section.side,
      tierId: section.tierId,
      seats,
    };
  });
}
