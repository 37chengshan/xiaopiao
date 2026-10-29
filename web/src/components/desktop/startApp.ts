import { syncReceiptPrefsFromCloud } from "@/features/receipt/prefsSync";
import { initTheme } from "@/lib/theme";

/**
 * App-wide startup, kept out of main.tsx / __root.tsx because QMuse import rejects initializers there.
 * Both the desktop layout and the legacy redirect layout call it; the guard makes it run once per page load.
 */
let started = false;

export function startApp() {
  if (started) return;
  started = true;
  initTheme();
  syncReceiptPrefsFromCloud();
}
