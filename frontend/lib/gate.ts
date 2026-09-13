import type { EventStatus, ProblemDetail, Ticket } from "./types";

/** The event a gate is admitting to, as the API names it on every admission. */
export interface GateEvent {
  id: number;
  slug: string;
  name: string;
  startsAt: string;
  timeZone: string;
  venue: string | null;
  status: EventStatus;
}

/**
 * How full the ground is: tickets admitted so far across every gate, out of the
 * tickets that can still admit anyone (cancelled ones do not count).
 */
export interface Admissions {
  eventId: number;
  admitted: number;
  issued: number;
}

/** An admission: the ticket let in, the event it was let in to, and the new count. */
export interface ScanResult {
  ticket: Ticket;
  event: GateEvent;
  admissions: Admissions;
}

/** An admission undone: the ticket, able to admit again, and the count without it. */
export interface UnadmitResult {
  ticket: Ticket;
  admissions: Admissions;
}

/** The members a refused scan carries beyond an ordinary problem detail. */
export interface GateProblem extends ProblemDetail {
  /** TICKET_ALREADY_USED: which ticket, and the gate it was first let in at. */
  ticketId?: string;
  usedGate?: string;
  /** TICKET_WRONG_EVENT: the event the ticket does admit to. */
  ticketEventId?: number;
  ticketEventName?: string;
  ticketEventStartsAt?: string;
  ticketEventTimeZone?: string;
  ticketEventVenue?: string;
  /** EVENT_NOT_ADMITTING: why this gate is shut. */
  eventStatus?: string;
}

export type ScanRefusal =
  | { kind: "already"; usedAt: string | null; gate: string | null; ticketId: string | null }
  | {
      kind: "wrong-event";
      name: string | null;
      startsAt: string | null;
      timeZone: string | null;
      venue: string | null;
    }
  | { kind: "event-closed"; status: string | null }
  | { kind: "cancelled" }
  | { kind: "unknown" }
  | { kind: "error" };

/** Sorts a refused scan by what the steward has to do about it. */
export function refusalOf(status: number | undefined, problem: GateProblem | undefined): ScanRefusal {
  switch (problem?.code) {
    case "TICKET_ALREADY_USED":
      return {
        kind: "already",
        usedAt: problem.usedAt ?? null,
        gate: problem.usedGate ?? null,
        ticketId: problem.ticketId ?? null,
      };
    case "TICKET_WRONG_EVENT":
      return {
        kind: "wrong-event",
        name: problem.ticketEventName ?? null,
        startsAt: problem.ticketEventStartsAt ?? null,
        timeZone: problem.ticketEventTimeZone ?? null,
        venue: problem.ticketEventVenue ?? null,
      };
    case "EVENT_NOT_ADMITTING":
      return { kind: "event-closed", status: problem.eventStatus ?? null };
    case "TICKET_CANCELLED":
      return { kind: "cancelled" };
  }
  return status === 404 ? { kind: "unknown" } : { kind: "error" };
}

/**
 * When an event starts, on the event's own clock: "Sat 26 Sep, 19:00". A steward telling a holder
 * which match their ticket is for means the time on the ticket, not the time wherever the scanner
 * happens to be. An unknown zone falls back to UTC and says so.
 */
export function kickoffLabel(startsAt: string | null | undefined, timeZone: string | null | undefined): string {
  if (!startsAt) return "";
  const at = new Date(startsAt);
  if (Number.isNaN(at.getTime())) return "";
  const options: Intl.DateTimeFormatOptions = {
    weekday: "short",
    day: "numeric",
    month: "short",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  };
  try {
    return new Intl.DateTimeFormat("en-GB", { ...options, timeZone: timeZone || "UTC" }).format(at);
  } catch {
    return `${new Intl.DateTimeFormat("en-GB", { ...options, timeZone: "UTC" }).format(at)} UTC`;
  }
}
