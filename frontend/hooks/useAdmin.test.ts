import { beforeEach, describe, expect, it, vi } from "vitest";
import { useAdminEvent, useApplyLayout, useSetEventStatus, useUpdateEvent } from "./useAdmin";

/**
 * These hooks only assemble React Query options, so the stubs below hand those
 * options straight back and the assertions read the key, the guard and the
 * request the panel would really send — no renderer or live API needed.
 */
const http = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn(), put: vi.fn(), patch: vi.fn() }));
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
  queryKey: unknown[];
  enabled: boolean;
  queryFn: () => Promise<unknown>;
}

interface MutationOptions {
  onSettled: () => void;
}

/** Every key the mutation just asked React Query to refetch. */
function invalidated(): unknown[][] {
  return queryClient.invalidateQueries.mock.calls.map(
    (call) => (call[0] as { queryKey: unknown[] }).queryKey,
  );
}

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
    expect(http.get).toHaveBeenCalledWith("/api/admin/events/7", {
      headers: { Authorization: "Bearer admin-token" },
    });
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
