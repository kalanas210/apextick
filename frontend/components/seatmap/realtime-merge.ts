import type { SeatStatusChange } from "@/lib/realtime";
import type { Seat } from "@/lib/types";

/**
 * Folds a live seat broadcast into the seats a map already has, so the map moves without a refetch.
 * A HELD broadcast never overrides a seat the buyer holds themselves -- their own holds are confirmed
 * by their own responses, and a broadcast names no holder -- but anything else about a seat is taken
 * as told: another buyer's hold, a release, a booking, a seat an admin has just taken off sale.
 */
export function mergeSeatChanges(current: Seat[] | undefined, changes: SeatStatusChange[]): Seat[] | undefined {
  if (!current) return current;
  const byId = new Map(changes.map((c) => [c.seatId, c]));
  return current.map((seat) => {
    const change = byId.get(seat.id);
    if (!change) return seat;
    return change.status === "HELD" && seat.mine
      ? seat
      : { ...seat, status: change.status, heldUntil: change.heldUntil, mine: false };
  });
}
