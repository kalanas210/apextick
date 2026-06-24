/** Category colors for seating tiers. A legible key, distinct from event tints. */
export const TIER_COLORS: Record<string, string> = {
  standard: "#6fa8c7",
  premium: "#b9c0c9",
  gold: "#e8b23a",
  suite: "#c9f23f",
};

export const HELD_COLOR = "#e0a82e";

export function tierColor(tierId: string): string {
  return TIER_COLORS[tierId] ?? "#6fa8c7";
}
