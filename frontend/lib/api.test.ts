import { AxiosError, AxiosHeaders } from "axios";
import { describe, expect, it, vi } from "vitest";
import { noteRefused, refusedSessions } from "./api";

function answered(status: number, authorization?: string) {
  const headers = new AxiosHeaders();
  if (authorization) headers.set("Authorization", authorization);
  const error = new AxiosError("Request failed", "ERR_BAD_REQUEST", { headers });
  error.response = { status, data: {}, statusText: "", headers: {}, config: { headers } };
  return error;
}

describe("lapsed sessions", () => {
  it("remembers the token a 401 refused, and says so once however many requests fail with it", () => {
    const listener = vi.fn();
    const unsubscribe = refusedSessions.subscribe(listener);

    noteRefused(answered(401, "Bearer lapsed-token"));
    noteRefused(answered(401, "Bearer lapsed-token"));

    expect(refusedSessions.current()).toBe("Bearer lapsed-token");
    expect(listener).toHaveBeenCalledTimes(1);
    unsubscribe();
  });

  it("takes no other failure for a lapsed session", () => {
    const listener = vi.fn();
    const unsubscribe = refusedSessions.subscribe(listener);

    noteRefused(answered(403, "Bearer not-allowed"));
    noteRefused(answered(401));
    noteRefused(new Error("offline"));

    expect(listener).not.toHaveBeenCalled();
    unsubscribe();
  });
});
