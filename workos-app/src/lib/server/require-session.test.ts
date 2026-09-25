import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const cookiesMock = vi.fn();

vi.mock("next/headers", () => ({
  cookies: () => cookiesMock(),
}));

// Imported after the mock so the module under test picks up the mocked `next/headers`.
const { requireSessionPersonId } = await import("./require-session");

function withCookie(token: string | undefined) {
  cookiesMock.mockResolvedValue({
    get: (name: string) => (name === "workos_session" && token ? { value: token } : undefined),
  });
}

describe("requireSessionPersonId", () => {
  beforeEach(() => {
    vi.stubGlobal("fetch", vi.fn());
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    cookiesMock.mockReset();
  });

  it("returns null and never calls Spring when no session cookie is present", async () => {
    withCookie(undefined);

    const result = await requireSessionPersonId();

    expect(result).toBeNull();
    expect(fetch).not.toHaveBeenCalled();
  });

  it("forwards the session cookie to Spring's /api/auth/me and resolves the returned person id", async () => {
    withCookie("token-abc");
    (fetch as ReturnType<typeof vi.fn>).mockResolvedValue({
      ok: true,
      json: async () => ({ id: "person-1" }),
    });

    const result = await requireSessionPersonId();

    expect(result).toBe("person-1");
    expect(fetch).toHaveBeenCalledWith(
      "http://localhost:8080/api/auth/me",
      expect.objectContaining({
        headers: { Cookie: "workos_session=token-abc" },
      }),
    );
  });

  it("returns null when Spring rejects the session (expired/unknown token)", async () => {
    withCookie("stale-token");
    (fetch as ReturnType<typeof vi.fn>).mockResolvedValue({ ok: false });

    const result = await requireSessionPersonId();

    expect(result).toBeNull();
  });

  it("fails closed (returns null) if the call to Spring throws", async () => {
    withCookie("token-abc");
    (fetch as ReturnType<typeof vi.fn>).mockRejectedValue(new Error("ECONNREFUSED"));

    const result = await requireSessionPersonId();

    expect(result).toBeNull();
  });
});
