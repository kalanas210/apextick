import { beforeEach, describe, expect, it, vi } from "vitest";
import { useScanTicket } from "./useGate";

/** As in useAdmin.test.ts: the stubs hand the React Query options straight back. */
const http = vi.hoisted(() => ({ post: vi.fn() }));
const session = vi.hoisted(() => ({ token: undefined as string | undefined }));

vi.mock("@tanstack/react-query", () => ({
  useMutation: (options: unknown) => options,
}));
vi.mock("./useSession", () => ({ useAccessToken: () => session.token }));
vi.mock("@/lib/api", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/lib/api")>()),
  api: http,
}));

interface MutationOptions {
  mutationFn: (input: { qrToken: string; eventId: number }) => Promise<unknown>;
}

beforeEach(() => {
  vi.clearAllMocks();
  session.token = "steward-token";
});

describe("useScanTicket", () => {
  it("scans against the chosen event on the gate API, with the steward's token", async () => {
    http.post.mockResolvedValue({ data: { ticket: { id: "t-1" } } });

    const mutation = useScanTicket() as unknown as MutationOptions;

    await expect(mutation.mutationFn({ qrToken: "qr-1", eventId: 7 })).resolves.toEqual({
      ticket: { id: "t-1" },
    });
    // Not /api/admin: a steward's device holds the scanner role, which never reaches it.
    expect(http.post).toHaveBeenCalledWith(
      "/api/gate/scans",
      { qrToken: "qr-1", eventId: 7 },
      { headers: { Authorization: "Bearer steward-token" } },
    );
  });
});
