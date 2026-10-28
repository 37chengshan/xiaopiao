import { isReceiptHapticsOn } from "./hapticPref";

/** Vibration patterns for the receipt. navigator.vibrate is missing on iOS Safari and desktop: then everything is a silent no-op. */
export type HapticKind = "tick" | "arm" | "stamp" | "tear" | "success";

const PATTERNS: Record<HapticKind, number | number[]> = {
  tick: 6,
  arm: 10,
  stamp: 28,
  tear: 14,
  success: [14, 40, 26],
};

export function haptic(kind: HapticKind): void {
  if (!isReceiptHapticsOn()) return;
  try {
    navigator.vibrate?.(PATTERNS[kind]);
  } catch {
    /* not supported */
  }
}
