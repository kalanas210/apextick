/**
 * Wire types for the booking API (`booking-service`). These mirror the Java DTOs
 * exactly; the presentation types for the static catalog live in `data/types.ts`.
 */

export type SeatStatus = 'AVAILABLE' | 'HELD' | 'BOOKED';
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

export interface EventDetail {
    id: number;
    slug: string;
    name: string;
    startsAt: string;
    timeZone: string;
    stadium: string;
    city: string;
    country: string;
    status: string;
    currency: 'INR' | 'GBP' | 'USD';
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
    fieldErrors?: Record<string, string>;
}
