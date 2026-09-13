import type { PaymentConfig } from "./types";

/**
 * What this deployment may honestly say about itself, taken from the API's own answer. Until that answer
 * arrives nothing is claimed: a site taking real money must never tell a buyer that none is charged, not
 * even for the moment before the config loads.
 */
export interface SiteMode {
  /** Seeded with the demo season and its shared account: fixtures, prices and seats are sample data. */
  demo: boolean;
  /** No real money can be taken: the mock gateway, or Stripe with test keys. */
  testMode: boolean;
}

export function siteMode(config: PaymentConfig | undefined): SiteMode {
  return { demo: config?.demo === true, testMode: config?.testMode === true };
}

/** The footer's line about the site. */
export function footerNote({ demo, testMode }: SiteMode): string | null {
  if (demo && testMode) {
    return "A demonstration site: fixtures, prices and seats are sample data, and checkout runs in test mode.";
  }
  if (demo) return "A demonstration site: fixtures, prices and seats are sample data.";
  if (testMode) return "Checkout runs in test mode.";
  return null;
}

/** Under the sign-in, registration and password-reset forms. */
export function authNote({ demo, testMode }: SiteMode): string | null {
  if (demo && testMode) return "This is a demonstration site: checkout runs in test mode.";
  if (demo) return "This is a demonstration site.";
  if (testMode) return "Checkout runs in test mode.";
  return null;
}

/** Beside the button that starts a purchase. */
export function checkoutNote({ testMode }: SiteMode): string | null {
  return testMode ? "Checkout runs in test mode. Pay with a test card; no real money is charged." : null;
}
