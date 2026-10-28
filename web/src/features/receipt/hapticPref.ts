import { useSyncExternalStore } from "react";
import { saveReceiptPrefs } from "@/services/receiptPrefsService";

/** Whether the receipt gives vibration feedback (print ticks, stamp, tear). On by default; synced to QMuse 云服务 via receiptPrefsService. Same style as soundPref. */
const KEY = "xiaopiao.receiptHaptics";
const listeners = new Set<() => void>();

export function isReceiptHapticsOn(): boolean {
  try {
    return localStorage.getItem(KEY) !== "off";
  } catch {
    return true;
  }
}

export function setReceiptHapticsOn(on: boolean, save = true) {
  try {
    localStorage.setItem(KEY, on ? "on" : "off");
  } catch {
    /* private mode */
  }
  listeners.forEach((l) => l());
  if (save) void saveReceiptPrefs({ hapticsOn: on }).catch((e) => console.warn("receipt prefs not saved", e));
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
