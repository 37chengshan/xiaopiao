import { afterEach, expect, it, vi } from "vitest";
import { accountRequest, accountSnapshot } from "./account";

afterEach(() => vi.unstubAllGlobals());
it("rejects HTML 200 from an unconnected preview instead of silently clearing the form", async () => {
  vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response("<!doctype html><html>SPA fallback</html>", { headers: { "Content-Type": "text/html" } })));
  await expect(accountRequest("login", { identifier: "fixture", password: "test-only-passphrase" })).rejects.toThrow("登录服务尚未连接");
});
it("requires an authenticated identity on a successful login response", async () => {
  vi.stubGlobal("fetch", vi.fn().mockResolvedValue(Response.json({ ok: true })));
  await expect(accountRequest("login", { identifier: "fixture", password: "test-only-passphrase" })).rejects.toThrow("登录服务返回异常");
});
it("keeps the authenticated profile in memory and clears it after logout", async () => {
  const user = { id: "fixture-owner", email: "fixture@example.test", username: "fixture", verified: true };
  vi.stubGlobal("fetch", vi.fn().mockResolvedValueOnce(Response.json({ user })).mockResolvedValueOnce(Response.json({ ok: true })));
  await accountRequest("login", { identifier: "fixture", password: "test-only-passphrase" });
  expect(accountSnapshot()).toEqual(user);
  await accountRequest("logout", {});
  expect(accountSnapshot()).toBeNull();
});
