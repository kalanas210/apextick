import { formatInstant } from "./format";
import type { EventDetail, EventStatus } from "./types";

/**
 * The client-side mirror of the booking service's sales window
 * (booking-service/src/main/java/com/apextick/booking/catalog/SalesWindow.java).
 * The server stays the authority — every hold, order and payment re-asks it —
 * but the seat map has to know the same answer *before* the buyer picks, or the
 * only way to find out a fixture stopped selling is to be refused after
 * choosing seats. Keep the two in step; the order of the checks is the same.
 */

/** Statuses that still accept holds, orders and payments — EventStatus.isPurchasable(). */
const PURCHASABLE: readonly EventStatus[] = ["onsale", "selling-fast", "final-release"];

export type SalesStateCode =
  | "open"
  | "sold-out"
  | "cancelled"
  | "not-on-sale"
  | "not-yet-open"
  | "closed"
  | "started";

export interface SalesState {
  /** Whether seats may be picked and reserved right now. */
  open: boolean;
  code: SalesStateCode;
  /** Headline for the panel — "Sales closed". */
  title: string;
  /** The one line that says why, and what the reader can do about it. */
  detail: string;
}

/** Milliseconds for an API timestamp, or `null` when it is absent or unparseable. */
function instant(iso: string | null | undefined): number | null {
  if (!iso) return null;
  const ms = new Date(iso).getTime();
  return Number.isNaN(ms) ? null : ms;
}

/** What the fixture-detail row says about selling right now. */
export function salesState(
  event: Pick<EventDetail, "status" | "startsAt"> &
    Partial<Pick<EventDetail, "salesStartAt" | "salesEndAt">>,
  now: number = Date.now(),
): SalesState {
  if (!PURCHASABLE.includes(event.status)) {
    if (event.status === "sold-out") {
      return {
        open: false,
        code: "sold-out",
        title: "Sold out",
        detail: "Every seat for this fixture has gone. Seats reappear here if an order is cancelled.",
      };
    }
    if (event.status === "cancelled") {
      return {
        open: false,
        code: "cancelled",
        title: "Fixture cancelled",
        detail: "This fixture was called off, so its seats are no longer on sale.",
      };
    }
    return {
      open: false,
      code: "not-on-sale",
      title: "Not on sale",
      detail: "This fixture is not selling seats at the moment.",
    };
  }

  const opensAt = instant(event.salesStartAt);
  if (opensAt !== null && now < opensAt) {
    return {
      open: false,
      code: "not-yet-open",
      title: "Not on sale yet",
      detail: `Sales open on ${formatInstant(event.salesStartAt)}.`,
    };
  }

  const endsAt = instant(event.salesEndAt);
  if (endsAt !== null && now > endsAt) {
    return {
      open: false,
      code: "closed",
      title: "Sales closed",
      detail: `Sales for this fixture closed on ${formatInstant(event.salesEndAt)}.`,
    };
  }

  // No explicit sales end means sales run up to kickoff, never past it.
  const kickoff = instant(event.startsAt);
  if (kickoff !== null && now >= kickoff) {
    return {
      open: false,
      code: "started",
      title: "Sales closed",
      detail: "This match has already kicked off.",
    };
  }

  return { open: true, code: "open", title: "On sale", detail: "" };
}
