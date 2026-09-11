import { describe, expect, it } from "vitest";
import { BOOKING_FEE_PERCENT, quoteOrder } from "./booking-rules";

describe("quoteOrder", () => {
  it("matches the order service for a single £55 seat", () => {
    // the seeded Premier League Standard tier: the map used to say £3 / £58
    expect(quoteOrder([55])).toEqual({ subtotal: 55, fee: 2.75, total: 57.75 });
  });

  it("keeps the fee in pence rather than whole units", () => {
    expect(quoteOrder([55, 55])).toEqual({ subtotal: 110, fee: 5.5, total: 115.5 });
    expect(quoteOrder([1234.56])).toEqual({ subtotal: 1234.56, fee: 61.73, total: 1296.29 });
  });

  it("rounds a half penny up, like HALF_UP", () => {
    expect(quoteOrder([0.1]).fee).toBe(0.01); // 0.005
    expect(quoteOrder([0.09]).fee).toBe(0); // 0.0045
    expect(quoteOrder([150, 320, 320]).fee).toBe(39.5);
  });

  it("does not drift on float sums", () => {
    expect(quoteOrder([0.1, 0.2])).toEqual({ subtotal: 0.3, fee: 0.02, total: 0.32 });
  });

  it("handles a fractional fee rate", () => {
    expect(quoteOrder([10.1], 2.5).fee).toBe(0.25); // 0.2525
  });

  it("charges nothing for an empty selection", () => {
    expect(quoteOrder([])).toEqual({ subtotal: 0, fee: 0, total: 0 });
  });

  it("defaults to the booking service's configured rate", () => {
    expect(BOOKING_FEE_PERCENT).toBe(5);
    expect(quoteOrder([200])).toEqual(quoteOrder([200], 5));
  });
});
