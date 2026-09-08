import type { EventStatus, OrderStatus, SeatStatus, TicketStatus } from "./types";

/**
 * Shared status vocabulary. Colour is never the only carrier — every pill also
 * says the word — and the tones stay inside the site's palette so an admin table
 * does not look like a different product.
 */

export const ORDER_TONE: Record<OrderStatus, string> = {
  PAID: "border-accent/50 text-accent",
  PENDING_PAYMENT: "border-line-2 text-bone",
  CANCELLED: "border-line-2 text-faint",
  EXPIRED: "border-line-2 text-faint",
};

export const ORDER_LABEL: Record<OrderStatus, string> = {
  PAID: "Confirmed",
  PENDING_PAYMENT: "Awaiting payment",
  CANCELLED: "Cancelled",
  EXPIRED: "Expired",
};

export const EVENT_TONE: Record<EventStatus, string> = {
  onsale: "border-accent/50 text-accent",
  "selling-fast": "border-accent/50 text-accent",
  "final-release": "border-line-strong text-bone",
  "sold-out": "border-line-2 text-muted",
  draft: "border-line-2 text-faint",
  cancelled: "border-[#ff6b6b]/40 text-[#ff6b6b]",
};

export const EVENT_LABEL: Record<EventStatus, string> = {
  onsale: "On sale",
  "selling-fast": "Selling fast",
  "final-release": "Final release",
  "sold-out": "Sold out",
  draft: "Draft",
  cancelled: "Cancelled",
};

/** In the order an operator moves an event through them. */
export const EVENT_STATUSES: EventStatus[] = [
  "draft",
  "onsale",
  "selling-fast",
  "final-release",
  "sold-out",
  "cancelled",
];

export const ORDER_STATUSES: OrderStatus[] = [
  "PENDING_PAYMENT",
  "PAID",
  "CANCELLED",
  "EXPIRED",
];

export const SEAT_TONE: Record<SeatStatus, string> = {
  AVAILABLE: "border-line-2 text-muted",
  HELD: "border-accent/50 text-accent",
  BOOKED: "border-line-strong text-bone",
};

export const TICKET_TONE: Record<TicketStatus, string> = {
  ISSUED: "border-accent/50 text-accent",
  USED: "border-line-2 text-muted",
  CANCELLED: "border-[#ff6b6b]/40 text-[#ff6b6b]",
};

/** The pill shape used across the admin tables. */
export const pillClass =
  "inline-flex items-center rounded-full border px-3 py-1 font-mono text-[0.6rem] uppercase tracking-[0.14em]";
