import type { EventDetail, Seat as ApiSeat } from "@/lib/types";
import type { MapSeat } from "./parts";

export interface MapSection {
  sectionId: number;
  /** The section's stable code ("north"), which is what `?section=` links carry. */
  sectionCode: string;
  sectionName: string;
  side: "n" | "s" | "e" | "w";
  tierId: string;
  seats: MapSeat[];
}

/** Folds API seats into the sections described by the event, ready to render. */
export function buildSections(event: EventDetail, seats: ApiSeat[]): MapSection[] {
  const tierNameByCode = new Map(event.tiers.map((t) => [t.code, t.name]));
  const sectionById = new Map(event.sections.map((s) => [s.id, s]));
  const grouped = new Map<number, MapSeat[]>();

  for (const seat of seats) {
    const section = sectionById.get(seat.sectionId);
    if (!section) continue;
    const state: MapSeat["state"] =
      seat.status === "BOOKED" ? "sold"
        // a seat this user is holding stays pickable — it is already theirs
        : seat.status === "HELD" && !seat.mine ? "held"
          : "available";
    const list = grouped.get(seat.sectionId) ?? [];
    list.push({
      id: String(seat.id),
      label: seat.label,
      col: seat.col ?? list.length,
      sectionName: section.name,
      tierId: seat.tierCode,
      tierName: tierNameByCode.get(seat.tierCode) ?? seat.tierCode,
      state,
      price: seat.price,
    });
    grouped.set(seat.sectionId, list);
  }

  return event.sections
    .filter((s) => grouped.has(s.id))
    .map((s) => ({
      sectionId: s.id,
      sectionCode: s.code,
      sectionName: s.name,
      side: s.side,
      tierId: s.tierCode,
      seats: (grouped.get(s.id) ?? []).sort(
        (a, b) => a.label.localeCompare(b.label, undefined, { numeric: true }),
      ),
    }));
}

/**
 * Whether a stand is the one a deep link asked for: `?section=north` from the
 * stadium diagram, or `?tier=gold` from a tier card.
 */
export function isLinkedSection(
  section: Pick<MapSection, "sectionCode" | "tierId">,
  link: { section?: string; tier?: string },
): boolean {
  return (
    (!!link.section && section.sectionCode === link.section) ||
    (!!link.tier && section.tierId === link.tier)
  );
}
