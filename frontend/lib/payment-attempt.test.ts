import { describe, expect, it } from "vitest";
import { settlesAttempt } from "./payment-attempt";

describe("settlesAttempt", () => {
  it("keeps the key when the request got no answer, since it may still have charged", () => {
    expect(settlesAttempt(undefined, undefined)).toBe(false);
  });

  it("keeps the key after a server error, for the same reason", () => {
    expect(settlesAttempt(500, "INTERNAL_ERROR")).toBe(false);
    expect(settlesAttempt(502, undefined)).toBe(false);
  });

  it("keeps the key while that attempt is still being processed", () => {
    expect(settlesAttempt(409, "PAYMENT_IN_PROGRESS")).toBe(false);
  });

  it("ends the attempt on a decline or any other answer", () => {
    expect(settlesAttempt(402, undefined)).toBe(true);
    expect(settlesAttempt(409, "ORDER_NOT_PAYABLE")).toBe(true);
    expect(settlesAttempt(422, "IDEMPOTENCY_KEY_MISSING")).toBe(true);
    expect(settlesAttempt(401, undefined)).toBe(true);
  });
});
