import { renderToStaticMarkup } from "react-dom/server";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { PaymentConfig } from "@/lib/types";
import { KeycloakSignIn } from "./keycloak-signin";

const state = vi.hoisted(() => ({ config: undefined as unknown }));

vi.mock("next/navigation", () => ({ useRouter: () => ({ replace: vi.fn() }) }));

vi.mock("@/hooks/useSession", () => ({
  useSession: () => ({ isAuthenticated: false, isLoading: false, signIn: vi.fn() }),
}));

vi.mock("@/hooks/useBooking", () => ({ usePaymentConfig: () => ({ data: state.config }) }));

const config = (demo: boolean): PaymentConfig => ({
  provider: "mock",
  enabledProviders: ["mock"],
  stripePublishableKey: null,
  testMode: true,
  demo,
});

beforeEach(() => {
  state.config = undefined;
});

describe("KeycloakSignIn", () => {
  it("offers the shared demo account on a demo deployment, and says it is shared", () => {
    state.config = config(true);

    const html = renderToStaticMarkup(<KeycloakSignIn />);

    expect(html).toContain("kalana");
    expect(html).toContain("Anyone can sign in with it");
  });

  it("publishes the demo password nowhere else, not even before the API has answered", () => {
    // the sign-in page used to print it on every deployment
    state.config = config(false);
    expect(renderToStaticMarkup(<KeycloakSignIn />)).not.toContain("12345");

    state.config = undefined;
    expect(renderToStaticMarkup(<KeycloakSignIn />)).not.toContain("12345");
  });
});
