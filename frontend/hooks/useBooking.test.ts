import { beforeEach, describe, expect, it, vi } from "vitest";
import { useCancelOrder, useMyOrders } from "./useBooking";

/**
 * Same shape as useAdmin.test.ts: the stubs hand the React Query options back,
 * so the assertions read the guard and the request the UI would really send —
 * no renderer or live API needed.
 */
const http = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn() }));
const queryClient = vi.hoisted(() => ({ invalidateQueries: vi.fn() }));
const session = vi.hoisted(() => ({ token: undefined as string | undefined }));

vi.mock("@tanstack/react-query", () => ({
  useQuery: (options: unknown) => options,
  useMutation: (options: unknown) => options,
  useQueryClient: () => queryClient,
}));
vi.mock("./useSession", () => ({ useAccessToken: () => session.token }));
vi.mock("@/lib/api", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/lib/api")>()),
  api: http,
}));

interface QueryOptions {
  enabled: boolean;
}

interface MutationOptions {
  mutationFn: () => Promise<unknown>;
  onSettled: () => void;
}

beforeEach(() => {
  vi.clearAllMocks();
  session.token = "buyer-token";
});

describe("useMyOrders", () => {
  it("stays idle until a caller actually needs the list", () => {
    expect((useMyOrders(false) as unknown as QueryOptions).enabled).toBe(false);
    expect((useMyOrders() as unknown as QueryOptions).enabled).toBe(true);
  });

  it("never asks for someone's orders without a token", () => {
    session.token = undefined;
    expect((useMyOrders(true) as unknown as QueryOptions).enabled).toBe(false);
  });
});

describe("useCancelOrder", () => {
  it("posts the cancel the seat map's way out depends on, with the bearer token", async () => {
    http.post.mockResolvedValue({ data: { id: "o-1", status: "CANCELLED" } });

    const mutation = useCancelOrder("o-1") as unknown as MutationOptions;
    await expect(mutation.mutationFn()).resolves.toEqual({
      id: "o-1",
      status: "CANCELLED",
    });

    expect(http.post).toHaveBeenCalledWith("/api/orders/o-1/cancel", null, {
      headers: { Authorization: "Bearer buyer-token" },
    });
  });

  it("refetches the order and the list, so the seats read as free again", () => {
    const mutation = useCancelOrder("o-1") as unknown as MutationOptions;
    mutation.onSettled();

    expect(
      queryClient.invalidateQueries.mock.calls.map(
        (call) => (call[0] as { queryKey: unknown[] }).queryKey,
      ),
    ).toEqual([["order", "o-1"], ["orders"]]);
  });
});
