import { useSyncExternalStore } from "react";

/** Whether the receipt printer plays its print/stamp sounds (docs/DESIGN.md §7). On by default; UI preference only. */
const KEY = "xiaopiao.receiptSound";
const listeners = new Set<() => void>();

export function isReceiptSoundOn(): boolean {
  try {
    return localStorage.getItem(KEY) !== "off";
  } catch {
    return true;
  }
}

export function setReceiptSoundOn(on: boolean) {
  try {
    localStorage.setItem(KEY, on ? "on" : "off");
  } catch {
    /* private mode */
  }
  listeners.forEach((l) => l());
}

export function useReceiptSound(): [boolean, (on: boolean) => void] {
  const on = useSyncExternalStore(
    (l) => {
      listeners.add(l);
      return () => listeners.delete(l);
    },
    isReceiptSoundOn,
  );
  return [on, setReceiptSoundOn];
}
