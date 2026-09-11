/**
 * Booking rules the UI needs before the API has priced anything. The booking
 * service stays the authority (the order it creates carries the real fee and
 * total); these mirror the defaults in
 * booking-service/src/main/resources/application.yml, so change the two together.
 */

/** app.order.fee-percent (APP_ORDER_FEE_PERCENT). */
export const BOOKING_FEE_PERCENT = 5;

/** app.hold.max-seats (APP_HOLD_MAX_SEATS). */
export const MAX_SEATS_PER_ORDER = 8;

export interface OrderQuote {
  subtotal: number;
  fee: number;
  total: number;
}

/**
 * The totals the order service will charge for these seat prices. It computes
 * fee = subtotal × percent / 100 at 2 decimal places, rounding HALF_UP; this
 * does the same in integer cents so float drift cannot move a penny.
 */
export function quoteOrder(prices: number[], feePercent = BOOKING_FEE_PERCENT): OrderQuote {
  const subtotalCents = prices.reduce((sum, p) => sum + Math.round(p * 100), 0);
  // percent in basis points keeps a fractional rate such as 2.5 exact
  const basisPoints = Math.round(feePercent * 100);
  const feeCents = Math.floor((subtotalCents * basisPoints + 5000) / 10000);
  return {
    subtotal: subtotalCents / 100,
    fee: feeCents / 100,
    total: (subtotalCents + feeCents) / 100,
  };
}
