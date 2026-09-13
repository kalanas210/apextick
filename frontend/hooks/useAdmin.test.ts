import { beforeEach, describe, expect, it, vi } from "vitest";
import {
  useAdminEvent,
  useAdminOrder,
  useAdminOrders,
  useApplyLayout,
  useRefundOrder,
  useRetryRefund,
  useSetEventStatus,
  useUpdateEvent,
} from "./useAdmin";

/**
 * These hooks only assemble React Query options, so the stubs below hand those
 * options straight back and the assertions read the key, the guard and the
 * request the panel would really send — no renderer or live API needed.
 */
const http = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn(), put: vi.fn(), patch: vi.fn() }));
const queryClient = vi.hoisted(() => ({ invalidateQueries: vi.fn(), setQueryData: vi.fn() }));
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
  queryKey: unknown[];
  enabled: boolean;
  queryFn: () => Promise<unknown>;
}

interface MutationOptions {
  onSettled: () => void;
}

interface RefundMutationOptions {
  mutationFn: (input?: string) => Promise<unknown>;
  onSuccess: (detail: unknown) => void;
  onError: () => void;
  onSettled: () => void;
}

/** Every key the mutation just asked React Query to refetch. */
function invalidated(): unknown[][] {
  return queryClient.invalidateQueries.mock.calls.map(
    (call) => (call[0] as { queryKey: unknown[] }).queryKey,
  );
}

const withToken = { headers: { Authorization: "Bearer admin-token" } };

beforeEach(() => {
  vi.clearAllMocks();
  session.token = "admin-token";
});

describe("useAdminEvent", () => {
  it("reads the admin copy of the event, with the bearer token", async () => {
    http.get.mockResolvedValue({ data: { id: 7, status: "draft" } });

    const query = useAdminEvent(7) as unknown as QueryOptions;

    expect(query.queryKey).toEqual(["admin", "event", 7]);
    expect(query.enabled).toBe(true);
    await expect(query.queryFn()).resolves.toEqual({ id: 7, status: "draft" });
    // Not the public /api/events/{id}: that one is about to stop serving drafts.
    expect(http.get).toHaveBeenCalledWith("/api/admin/events/7", withToken);
  });

  it("waits for a token rather than asking anonymously", () => {
    session.token = undefined;

    expect((useAdminEvent(7) as unknown as QueryOptions).enabled).toBe(false);
  });

  it("stays idle when the route did not resolve an id", () => {
    // The page passes Number(params.id), which is NaN for a hand-typed URL.
    expect((useAdminEvent(Number("nope")) as unknown as QueryOptions).enabled).toBe(false);
  });
});

describe("event mutations", () => {
  it("refresh the admin copy after a save", () => {
    (useUpdateEvent(7) as unknown as MutationOptions).onSettled();

    expect(invalidated()).toContainEqual(["admin", "event", 7]);
    expect(invalidated()).toContainEqual(["admin", "events"]);
  });

  it("refresh the admin copy and the public catalog after a status change", () => {
    (useSetEventStatus(7) as unknown as MutationOptions).onSettled();

    expect(invalidated()).toContainEqual(["admin", "event", 7]);
    expect(invalidated()).toContainEqual(["events"]);
  });

  it("refresh the admin copy after a layout is generated", () => {
    (useApplyLayout(7) as unknown as MutationOptions).onSettled();

    // The builder switches to its read-only half on the event's own tiers.
    expect(invalidated()).toContainEqual(["admin", "event", 7]);
    expect(invalidated()).toContainEqual(["admin", "seats", 7]);
  });
});

describe("the order console", () => {
  it("searches by order number, email or name, and narrows to refunds owed", async () => {
    http.get.mockResolvedValue({ data: { content: [] } });

    await (useAdminOrders({ q: "APX-7", refundRequired: true, page: 0, size: 20 }) as unknown as QueryOptions).queryFn();

    expect(http.get).toHaveBeenCalledWith("/api/admin/orders", {
      ...withToken,
      params: { q: "APX-7", refundRequired: "true", page: 0, size: 20 },
    });
  });

  it("leaves the refund filter off the request unless it is asked for", async () => {
    http.get.mockResolvedValue({ data: { content: [] } });

    await (useAdminOrders({ refundRequired: false }) as unknown as QueryOptions).queryFn();

    expect(http.get).toHaveBeenCalledWith("/api/admin/orders", { ...withToken, params: {} });
  });

  it("reads one order in full, and only once the route names one", async () => {
    http.get.mockResolvedValue({ data: { order: { id: "o-1" } } });

    const query = useAdminOrder("o-1") as unknown as QueryOptions;

    expect(query.queryKey).toEqual(["admin", "order", "o-1"]);
    expect(query.enabled).toBe(true);
    await query.queryFn();
    expect(http.get).toHaveBeenCalledWith("/api/admin/orders/o-1", withToken);
    expect((useAdminOrder(undefined) as unknown as QueryOptions).enabled).toBe(false);
  });

  it("refunds with a reason, shows the answer at once and refreshes what a refund changes", async () => {
    const detail = { order: { id: "o-1", status: "REFUNDED" } };
    http.post.mockResolvedValue({ data: detail });

    const mutation = useRefundOrder("o-1") as unknown as RefundMutationOptions;

    await expect(mutation.mutationFn("Cannot attend")).resolves.toEqual(detail);
    expect(http.post).toHaveBeenCalledWith("/api/admin/orders/o-1/refund", { reason: "Cannot attend" }, withToken);

    mutation.onSuccess(detail);
    expect(queryClient.setQueryData).toHaveBeenCalledWith(["admin", "order", "o-1"], detail);
    mutation.onSettled();
    // the freed seats and the lost revenue show up everywhere they are counted
    expect(invalidated()).toContainEqual(["admin", "orders"]);
    expect(invalidated()).toContainEqual(["admin", "stats"]);
    expect(invalidated()).toContainEqual(["seats"]);
  });

  it("refetches the order when a refund is refused, because it has moved on", () => {
    (useRefundOrder("o-1") as unknown as RefundMutationOptions).onError();

    expect(invalidated()).toContainEqual(["admin", "order", "o-1"]);
  });

  it("asks the provider again for a refund it refused", async () => {
    http.post.mockResolvedValue({ data: { order: { id: "o-1" } } });

    await (useRetryRefund("o-1") as unknown as RefundMutationOptions).mutationFn();

    expect(http.post).toHaveBeenCalledWith("/api/admin/orders/o-1/refund/retry", null, withToken);
  });
});
