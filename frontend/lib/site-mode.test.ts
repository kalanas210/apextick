import { describe, expect, it } from "vitest";
import { authNote, checkoutNote, footerNote, siteMode } from "./site-mode";
import type { PaymentConfig } from "./types";

const config = (over: Partial<PaymentConfig>): PaymentConfig => ({
  provider: "stripe",
  enabledProviders: ["stripe"],
  stripePublishableKey: "pk_live_123",
  testMode: false,
  demo: false,
  ...over,
});

describe("siteMode", () => {
  it("claims nothing before the API has answered", () => {
    const mode = siteMode(undefined);

    expect(mode).toEqual({ demo: false, testMode: false });
    expect([footerNote(mode), authNote(mode), checkoutNote(mode)]).toEqual([null, null, null]);
  });

  it("never tells a buyer on live keys that no real money is charged", () => {
    // these lines used to be hard-coded, so a live deployment would have said so anyway
    const live = siteMode(config({}));

    expect(checkoutNote(live)).toBeNull();
    expect(footerNote(live)).toBeNull();
    expect(authNote(live)).toBeNull();
  });

  it("labels the demo, and test payments, each where it is true", () => {
    const demo = siteMode(config({ provider: "mock", testMode: true, demo: true }));
    expect(footerNote(demo)).toContain("sample data");
    expect(footerNote(demo)).toContain("test mode");
    expect(authNote(demo)).toContain("demonstration site");
    expect(checkoutNote(demo)).toContain("no real money is charged");

    // test keys on real fixtures: say test mode, and nothing about sample data
    const staging = siteMode(config({ stripePublishableKey: "pk_test_123", testMode: true }));
    expect(footerNote(staging)).toBe("Checkout runs in test mode.");
    expect(authNote(staging)).not.toContain("demonstration");
  });
});
