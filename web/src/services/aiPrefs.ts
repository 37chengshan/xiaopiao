import { useSyncExternalStore } from "react";
const key = "xiaopiao.ai.auto-entry";
const event = "xiaopiao-ai-preference";
function read() { try { return localStorage.getItem(key) === "true"; } catch { return false; } }
function subscribe(listener: () => void) {
  window.addEventListener(event, listener); window.addEventListener("storage", listener);
  return () => { window.removeEventListener(event, listener); window.removeEventListener("storage", listener); };
}
export function useAutoEntry(): [boolean, (value: boolean) => void] {
  const enabled = useSyncExternalStore(subscribe, read);
  return [enabled, value => { localStorage.setItem(key, String(value)); window.dispatchEvent(new Event(event)); }];
}
