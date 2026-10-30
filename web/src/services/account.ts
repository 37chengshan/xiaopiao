import { useSyncExternalStore } from "react";

export interface AccountUser { id: string; email: string; username: string; verified: boolean }
const base = (import.meta.env.VITE_API_BASE_URL ?? "").replace(/\/$/, "");
let current: AccountUser | null = null;
const listeners = new Set<() => void>();
function update(user: AccountUser | null) { current = user; listeners.forEach((fn) => fn()); }
export function useAccount() {
  return useSyncExternalStore((listener) => { listeners.add(listener); return () => { listeners.delete(listener); }; }, () => current);
}
export async function accountRequest(action: string, fields?: Record<string, string>): Promise<{ user?: AccountUser }> {
  const response = await fetch(`${base}/v1/auth/${action}`, {
    method: fields ? "POST" : "GET", credentials: "include",
    headers: fields ? { "Content-Type": "application/json", Accept: "application/json" } : { Accept: "application/json" },
    signal: AbortSignal.timeout(20_000),
    body: fields ? JSON.stringify(fields) : undefined,
  });
  if (!response.headers.get("Content-Type")?.includes("application/json")) throw new Error("登录服务尚未连接，请确认当前预览已接入账号接口");
  const body = await response.json().catch(() => { throw new Error("登录服务返回异常，请稍后重试"); });
  if (!response.ok) { if (response.status === 401) update(null); throw new Error(body.message ?? "请求失败，请重试"); }
  if (["me", "login", "register", "verify"].includes(action) && (!body.user || typeof body.user.id !== "string" || typeof body.user.verified !== "boolean")) throw new Error("登录服务返回异常，请稍后重试");
  if (body.user) update(body.user);
  if (action === "logout") update(null);
  return body;
}
let refresh: Promise<void> | undefined;
export function refreshAccount() {
  return refresh ??= accountRequest("me").then(() => {}, () => {});
}
export function accountSnapshot() { return current; }
export function agentUrl() { return `${base}/v1/chat`; }
