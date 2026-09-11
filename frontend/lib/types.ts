/**
 * Wire types for the booking API (`booking-service`). These mirror the Java DTOs
 * exactly; the presentation types for the static catalog live in `data/types.ts`.
 */

export type SeatStatus = 'AVAILABLE' | 'HELD' | 'BOOKED';
export type Sport = 'cricket' | 'football';
export type Currency = 'INR' | 'GBP' | 'USD';
/** Serialized as the hyphenated code, not the enum name. */
export type EventStatus =
    | 'onsale' | 'selling-fast' | 'final-release' | 'sold-out' | 'draft' | 'cancelled';
export type OrderStatus = 'PENDING_PAYMENT' | 'PAID' | 'CANCELLED' | 'EXPIRED';
export type TicketStatus = 'ISSUED' | 'USED' | 'CANCELLED';
export type PaymentStatus =
    | 'INITIATED' | 'REQUIRES_ACTION' | 'REDIRECTED' | 'SUCCEEDED'
    | 'FAILED' | 'CANCELLED' | 'REFUND_REQUIRED' | 'REFUNDED';

export interface Seat {
    id: number;
    label: string;
    row: number | null;
    col: number | null;
    sectionId: number;
    sectionCode: string;
    tierId: number;
    tierCode: string;
    price: number;
    status: SeatStatus;
    heldUntil: string | null;
    /** True when the current user is the one holding this seat. */
    mine: boolean;
}

export interface PriceTier {
    id: number;
    code: string;
    name: string;
    price: number;
    perks: string[];
    remaining: number;
    total: number;
}

export interface Section {
    id: number;
    code: string;
    name: string;
    tierId: number;
    tierCode: string;
    side: 'n' | 's' | 'e' | 'w';
    rows: number;
    seatsPerRow: number;
    available: number;
}

export interface Team {
    id: number;
    name: string;
    /** The API renames Java's shortCode to `short` on the wire. */
    short: string;
    monogram: string | null;
    color: string | null;
    flag: string | null;
    logo: string | null;
}

export interface Series {
    id: number;
    slug: string;
    name: string;
    shortName: string | null;
    sport: Sport;
    tint: string | null;
    kicker: string | null;
    blurb: string | null;
    image: string | null;
    currency: Currency;
    currencySymbol: string;
    cities: string[];
    scale: string | null;
}

/** A row in either event list: the public catalog and the admin one share this shape. */
export interface EventSummary {
    id: number;
    slug: string;
    name: string;
    seriesId: number | null;
    seriesSlug: string | null;
    sport: Sport;
    home: Team | null;
    away: Team | null;
    startsAt: string;
    /** Rendered in the event's own timeZone, not the viewer's. */
    date: string;
    time: string;
    timeZone: string;
    stadium: string;
    city: string | null;
    country: string | null;
    stage: string | null;
    status: EventStatus;
    image: string | null;
    blurb: string | null;
    currency: Currency;
    currencySymbol: string;
    fromPrice: number | null;
    availableSeats: number;
    totalSeats: number;
    salesStartAt: string | null;
    salesEndAt: string | null;
}

export interface EventDetail {
    id: number;
    slug: string;
    name: string;
    seriesId?: number | null;
    sport?: Sport;
    home?: Team | null;
    away?: Team | null;
    startsAt: string;
    timeZone: string;
    stadium: string;
    city: string;
    country: string;
    stage?: string | null;
    status: EventStatus;
    image?: string | null;
    blurb?: string | null;
    salesStartAt?: string | null;
    salesEndAt?: string | null;
    currency: Currency;
    currencySymbol: string;
    availableSeats: number;
    totalSeats: number;
    tiers: PriceTier[];
    sections: Section[];
}

export interface Hold {
    eventId: number;
    seatIds: number[];
    heldUntil: string;
    holdSeconds: number;
    seats: Seat[];
}

export interface OrderItem {
    id: number;
    seatId: number;
    label: string;
    sectionName: string;
    tierCode: string;
    tierName: string;
    unitPrice: number;
    ticketId: string | null;
}

export interface Order {
    id: string;
    orderNumber: string;
    status: OrderStatus;
    eventId: number;
    eventSlug: string;
    eventName: string;
    startsAt: string;
    currency: 'INR' | 'GBP' | 'USD';
    subtotal: number;
    fee: number;
    total: number;
    expiresAt: string | null;
    createdAt: string;
    paidAt: string | null;
    cancelReason: string | null;
    items: OrderItem[];
    ticketIds: string[];
}

export interface Ticket {
    id: string;
    orderId: string;
    status: TicketStatus;
    qrToken: string;
    issuedAt: string;
    usedAt: string | null;
    /** Relative path on the API, e.g. `/api/tickets/{id}/pdf`. */
    pdfUrl: string;
    seatId: number;
    seatLabel: string;
    sectionName: string;
    tierName: string;
}

export interface Payment {
    paymentId: string;
    orderId: string;
    provider: string;
    status: PaymentStatus;
    cardBrand: string | null;
    cardLast4: string | null;
    failureCode: string | null;
    failureMessage: string | null;
    /** Present for Stripe: hand to Stripe.js to confirm the PaymentIntent. */
    clientSecret: string | null;
    order: Order;
}

export interface PaymentConfig {
    provider: 'mock' | 'stripe';
    enabledProviders: string[];
    stripePublishableKey: string | null;
}

export interface Me {
    sub: string;
    username: string;
    email: string | null;
    name: string | null;
    roles: string[];
}

/** RFC-7807 problem detail returned by the API on errors. */
export interface ProblemDetail {
    title?: string;
    status?: number;
    detail?: string;
    code?: string;
    seatIds?: number[];
    /** One entry per rejected field; not a map — the API sends a list. */
    fieldErrors?: { field: string; message: string }[];
    retryAfterSeconds?: number;
    correlationId?: string;
    /** On TICKET_ALREADY_USED: when the ticket was first scanned. */
    usedAt?: string;
}

/** The API's pagination envelope. `page` is zero-based. */
export interface PageResponse<T> {
    content: T[];
    page: number;
    size: number;
    totalElements: number;
    totalPages: number;
    last: boolean;
}

/** Body of POST/PUT /api/admin/events. A PUT replaces the event wholesale. */
export interface EventUpsert {
    name: string;
    slug: string;
    sport: Sport;
    seriesId: number | null;
    homeTeamId: number | null;
    awayTeamId: number | null;
    /** ISO-8601 instant, e.g. `2027-01-01T18:00:00Z`. */
    startsAt: string;
    timeZone: string | null;
    venue: string;
    city: string | null;
    country: string | null;
    stage: string | null;
    status: EventStatus | null;
    image: string | null;
    blurb: string | null;
    currency: Currency;
    salesStartAt: string | null;
    salesEndAt: string | null;
}

export interface LayoutTierSpec {
    code: string;
    name: string;
    price: number;
    perks: string[];
    sortOrder: number;
}

export interface LayoutSectionSpec {
    code: string;
    name: string;
    tierCode: string;
    side: 'n' | 's' | 'e' | 'w';
    /** 1..26 — row letters are generated as A..Z. */
    rows: number;
    seatsPerRow: number;
    sortOrder: number;
}

export interface LayoutInput {
    tiers: LayoutTierSpec[];
    sections: LayoutSectionSpec[];
}

export interface LayoutResult {
    eventId: number;
    tiersCreated: number;
    sectionsCreated: number;
    seatsCreated: number;
}

export interface TierStat {
    tierId: number;
    tierCode: string;
    total: number;
    available: number;
    held: number;
    booked: number;
}

export interface EventStats {
    eventId: number;
    available: number;
    held: number;
    booked: number;
    total: number;
    revenue: number;
    currency: Currency;
    byTier: TierStat[];
}

export interface AdminSeat {
    id: number;
    label: string;
    sectionId: number;
    status: SeatStatus;
    /** Keycloak `sub` of whoever holds it. */
    heldBy: string | null;
    heldUntil: string | null;
    version: number;
}

export interface VerifyResult {
    ok: boolean;
    ticket: Ticket;
    reason: string | null;
}
