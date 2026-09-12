import { describe, expect, it } from "vitest";
import { findPendingOrder } from "./orders";
import type { Order, OrderStatus } from "./types";

const order = (
  id: string,
  status: OrderStatus,
  eventId: number,
  seatIds: number[],
): Order =>
  ({
    id,
    status,
    eventId,
    items: seatIds.map((seatId) => ({ id: seatId, seatId })),
  }) as Order;

describe("findPendingOrder", () => {
  const orders = [
    order("newest", "PENDING_PAYMENT", 7, [30, 31]),
    order("older", "PENDING_PAYMENT", 7, [10, 11]),
    order("paid", "PAID", 7, [20, 21]),
    order("other-event", "PENDING_PAYMENT", 8, [10, 11]),
  ];

  it("picks the order that actually covers the refused seats", () => {
    expect(findPendingOrder(orders, 7, [11])?.id).toBe("older");
  });

  it("falls back to the newest pending order when the refusal named no seats", () => {
    expect(findPendingOrder(orders, 7)?.id).toBe("newest");
  });

  it("never crosses events, even on a seat id that matches", () => {
    expect(findPendingOrder([orders[3]], 7, [10])).toBeUndefined();
  });

  it("ignores orders that are no longer blocking anything", () => {
    expect(findPendingOrder([orders[2]], 7, [20])).toBeUndefined();
  });

  it("copes with the order list not having loaded yet", () => {
    expect(findPendingOrder(undefined, 7, [11])).toBeUndefined();
  });
});
