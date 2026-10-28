import { useSyncExternalStore } from "react";
import { saveReceiptPrefs } from "@/services/receiptPrefsService";

/** Whether the receipt printer plays its print/stamp sounds (docs/DESIGN.md §7). On by default; synced to QMuse 云服务 via receiptPrefsService. */
const KEY = "xiaopiao.receiptSound";
const listeners = new Set<() => void>();

export function isReceiptSoundOn(): boolean {
  try {
    return localStorage.getItem(KEY) !== "off";
  } catch {
    return true;
  }
}

export function setReceiptSoundOn(on: boolean, save = true) {
  try {
    localStorage.setItem(KEY, on ? "on" : "off");
  } catch {
    /* private mode */
  }
  listeners.forEach((l) => l());
  if (save) void saveReceiptPrefs({ soundOn: on }).catch((e) => console.warn("receipt prefs not saved", e));
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
