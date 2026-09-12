import type { Order } from "./types";

/**
 * The unpaid order that is keeping a set of seats locked.
 *
 * The API refuses to release seats an order still covers (409 ORDER_PENDING) and
 * refuses to open a second order over them (409 ORDER_ALREADY_PENDING), but
 * neither problem detail names the order itself — only the seats. The caller's
 * own order list is what turns that refusal into a link they can act on.
 *
 * Matching on the seat ids is exact; the most recent pending order for the event
 * is the fallback for the refusal that carries no seats. `orders` is expected in
 * the order `/api/orders/me` returns them, newest first.
 *
 * When seats *were* named and no order covers them, that is not a case for the
 * fallback: the answer hangs a "Cancel that order" button off whatever comes
 * back, and cancelling an order that is not the blocker destroys a good order
 * without freeing a seat. Returning nothing sends the buyer to their order list
 * instead, which is the honest answer to "we cannot tell which one".
 */
export function findPendingOrder(
  orders: Order[] | undefined,
  eventId: number,
  seatIds: number[] = [],
): Order | undefined {
  const pending = (orders ?? []).filter(
    (o) => o.status === "PENDING_PAYMENT" && o.eventId === eventId,
  );
  if (seatIds.length === 0) {
    return pending[0];
  }
  const wanted = new Set(seatIds);
  return pending.find((o) => o.items.some((i) => wanted.has(i.seatId)));
}
