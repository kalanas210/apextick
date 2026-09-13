import { beforeEach, describe, expect, it, vi } from "vitest";
import { useAdmissions, useScanTicket } from "./useGate";

/** As in useAdmin.test.ts: the stubs hand the React Query options straight back. */
const http = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn() }));
const queryClient = vi.hoisted(() => ({ setQueryData: vi.fn() }));
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
  mutationFn: (input: { qrToken: string; eventId: number; gate?: string }) => Promise<unknown>;
  onSuccess: (result: unknown) => void;
}

beforeEach(() => {
  vi.clearAllMocks();
  session.token = "steward-token";
});

describe("useScanTicket", () => {
  it("scans against the chosen event on the gate API, with the steward's token", async () => {
    http.post.mockResolvedValue({ data: { ticket: { id: "t-1" } } });

    const mutation = useScanTicket() as unknown as MutationOptions;

    await expect(mutation.mutationFn({ qrToken: "qr-1", eventId: 7, gate: "North 3" })).resolves.toEqual({
      ticket: { id: "t-1" },
    });
    // Not /api/admin: a steward's device holds the scanner role, which never reaches it.
    expect(http.post).toHaveBeenCalledWith(
      "/api/gate/scans",
      { qrToken: "qr-1", eventId: 7, gate: "North 3" },
      { headers: { Authorization: "Bearer steward-token" } },
    );
  });

  it("moves this device's admissions count as soon as a ticket is let in", () => {
    const admissions = { eventId: 7, admitted: 124, issued: 300 };

    (useScanTicket() as unknown as MutationOptions).onSuccess({ event: { id: 7 }, admissions });

    expect(queryClient.setQueryData).toHaveBeenCalledWith(["gate", "admissions", 7], admissions);
  });
});

describe("useAdmissions", () => {
  it("counts the event's admissions across every gate", async () => {
    http.get.mockResolvedValue({ data: { eventId: 7, admitted: 124, issued: 300 } });

    const query = useAdmissions(7) as unknown as QueryOptions;

    expect(query.queryKey).toEqual(["gate", "admissions", 7]);
    expect(query.enabled).toBe(true);
    await expect(query.queryFn()).resolves.toEqual({ eventId: 7, admitted: 124, issued: 300 });
    expect(http.get).toHaveBeenCalledWith("/api/gate/events/7/admissions", {
      headers: { Authorization: "Bearer steward-token" },
    });
  });

  it("stays idle until the gate has an event", () => {
    expect((useAdmissions(null) as unknown as QueryOptions).enabled).toBe(false);
  });
});
