import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it } from "vitest";
import type { Order } from "@/lib/types";
import { OrderSummary } from "./order-summary";

/**
 * A component rendered to static markup: no DOM needed, so it runs in the same node environment as
 * the unit tests. The config used to collect *.test.ts only, and a test like this was silently skipped.
 */
const order = {
  id: "o-1",
  orderNumber: "APX-SUMMARY",
  eventName: "World Cup Final",
  currency: "GBP",
  subtotal: 100,
  fee: 5,
  total: 105,
  items: [
    { id: 1, seatId: 10, label: "A1", sectionName: "North Stand", tierCode: "gold", tierName: "Gold", unitPrice: 100, ticketId: null },
  ],
} as unknown as Order;

describe("OrderSummary", () => {
  it("prices the order the way the receipt will, and counts the payment window down", () => {
    const html = renderToStaticMarkup(<OrderSummary order={order} secondsLeft={75} />);

    expect(html).toContain("Order APX-SUMMARY");
    expect(html).toContain("North Stand");
    expect(html).toContain("£105.00");
    expect(html).toContain("1:15");
  });

  it("shows no countdown once the order is no longer waiting on payment", () => {
    expect(renderToStaticMarkup(<OrderSummary order={order} secondsLeft={null} />)).not.toContain('role="timer"');
  });
});
