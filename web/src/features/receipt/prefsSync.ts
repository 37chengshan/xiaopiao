import { loadReceiptPrefs } from "@/services/receiptPrefsService";
import { setReceiptHapticsOn } from "./hapticPref";
import { setReceiptSoundOn } from "./soundPref";

/** Pulls this account's receipt preferences from QMuse 云服务 into the local toggles (localStorage stays a per-device cache). */
export function syncReceiptPrefsFromCloud() {
  loadReceiptPrefs()
    .then((p) => {
      if (!p) return;
      setReceiptSoundOn(p.soundOn, false);
      setReceiptHapticsOn(p.hapticsOn, false);
    })
    .catch((e) => console.warn("receipt prefs not loaded", e));
}
