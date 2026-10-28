import { useSyncExternalStore } from "react";

/** Whether the receipt gives vibration feedback (print ticks, stamp, tear). On by default; UI preference only. Same style as soundPref. */
const KEY = "xiaopiao.receiptHaptics";
const listeners = new Set<() => void>();

export function isReceiptHapticsOn(): boolean {
  try {
    return localStorage.getItem(KEY) !== "off";
  } catch {
    return true;
  }
}

export function setReceiptHapticsOn(on: boolean) {
  try {
    localStorage.setItem(KEY, on ? "on" : "off");
  } catch {
    /* private mode */
  }
  listeners.forEach((l) => l());
}

export function useReceiptHaptics(): [boolean, (on: boolean) => void] {
  const on = useSyncExternalStore(
    (l) => {
      listeners.add(l);
      return () => listeners.delete(l);
    },
    isReceiptHapticsOn,
  );
  return [on, setReceiptHapticsOn];
}
