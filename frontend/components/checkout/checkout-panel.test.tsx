import { AxiosError, AxiosHeaders } from "axios";
import { renderToStaticMarkup } from "react-dom/server";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { Order, PaymentConfig } from "@/lib/types";
import { CheckoutPanel } from "./checkout-panel";

/**
 * The checkout's branches, rendered to static markup with its data hooks stubbed: which card form a
 * buyer is offered, and what they are told when the order, the payment options or their session fail.
 */
const state = vi.hoisted(() => ({
  session: { isAuthenticated: true, isLoading: false, expired: false },
  order: { data: undefined as unknown, isLoading: false, error: null as unknown },
  config: { data: undefined as unknown, isError: false },
}));

vi.mock("next/navigation", () => ({ useRouter: () => ({ push: vi.fn(), replace: vi.fn() }) }));

vi.mock("@/hooks/useSession", () => ({
  useSession: () => ({ ...state.session, error: undefined, token: undefined, profile: undefined, signIn: vi.fn() }),
}));

vi.mock("@/hooks/useBooking", () => ({
  useOrder: () => ({ ...state.order, refetch: vi.fn() }),
  usePaymentConfig: () => ({ ...state.config, refetch: vi.fn() }),
  usePayOrder: () => ({ isPending: false, mutateAsync: vi.fn() }),
  useCancelOrder: () => ({ isPending: false, mutateAsync: vi.fn() }),
}));

const order = (over: Partial<Order> = {}) =>
  ({
    id: "o-1",
    orderNumber: "APX-CHECKOUT",
    status: "PENDING_PAYMENT",
    eventSlug: "world-cup-final",
    eventName: "World Cup Final",
    currency: "GBP",
    subtotal: 100,
    fee: 5,
    total: 105,
    expiresAt: new Date(Date.now() + 5 * 60_000).toISOString(),
    items: [
      { id: 1, seatId: 10, label: "A1", sectionName: "North Stand", tierCode: "gold", tierName: "Gold", unitPrice: 100, ticketId: null },
    ],
    ...over,
  }) as Order;

const mock: PaymentConfig = { provider: "mock", enabledProviders: ["mock"], stripePublishableKey: null };

function answered(status: number) {
  const headers = new AxiosHeaders();
  const error = new AxiosError("Request failed", "ERR_BAD_RESPONSE", { headers });
  error.response = { status, data: {}, statusText: "", headers: {}, config: { headers } };
  return error;
}

const render = () => renderToStaticMarkup(<CheckoutPanel orderId="o-1" />);

/** The mock form's prefilled card: seeing it means a raw card number can be typed and posted. */
const RAW_CARD_FORM = "4242 4242 4242 4242";

beforeEach(() => {
  state.session = { isAuthenticated: true, isLoading: false, expired: false };
  state.order = { data: order(), isLoading: false, error: null };
  state.config = { data: mock, isError: false };
});

describe("CheckoutPanel", () => {
  it("shows no card form while the payment options are still loading", () => {
    // it used to render the raw-card form here, under a "Paying with Stripe" label
    state.config = { data: undefined, isError: false };

    const html = render();

    expect(html).toContain("Loading the payment options");
    expect(html).not.toContain(RAW_CARD_FORM);
  });

  it("takes a card on the demo gateway once the config says that is the gateway", () => {
    const html = render();

    expect(html).toContain("the demo gateway");
    expect(html).toContain(RAW_CARD_FORM);
  });

  it("offers a retry, and no card form, when the payment options cannot be loaded", () => {
    state.config = { data: undefined, isError: true };

    const html = render();

    expect(html).toContain("could not be loaded");
    expect(html).toContain("Try again");
    expect(html).not.toContain(RAW_CARD_FORM);
  });

  it("tells an order that does not exist from one the service could not load", () => {
    state.order = { data: undefined, isLoading: false, error: answered(404) };
    expect(render()).toContain("Order not found");

    state.order = { data: undefined, isLoading: false, error: answered(503) };
    const html = render();
    expect(html).toContain("Could not load this order");
    expect(html).toContain("Try again");
  });

  it("asks a buyer whose session lapsed to sign in again, with their seats still held", () => {
    // the order query fails with 401 too, and that used to read as "Order not found"
    state.session = { isAuthenticated: false, isLoading: false, expired: true };
    state.order = { data: undefined, isLoading: false, error: answered(401) };

    const html = render();

    expect(html).toContain("Your session expired");
    expect(html).toContain("Sign in again");
    expect(html).toContain("seats stay held");
    expect(html).not.toContain("Order not found");
  });

  it("sends a buyer whose order expired back to the seat map instead of taking a card", () => {
    state.order = { data: order({ status: "EXPIRED", expiresAt: null }), isLoading: false, error: null };

    const html = render();

    expect(html).toContain("This order expired");
    expect(html).toContain("/events/world-cup-final/seats");
    expect(html).not.toContain(RAW_CARD_FORM);
  });
});
