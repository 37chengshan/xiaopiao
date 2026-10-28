import { getLocale, setLocale } from "@/lib/strings";

/**
 * Language choice is a preference, so it is the one thing the desktop shell may keep in localStorage
 * (docs/WEB-SPEC.md §0.1). Ledger data never goes there.
 */
const KEY = "xiaopiao.d.locale";

export function initLocalePref() {
  try {
    const saved = localStorage.getItem(KEY);
    if (saved === "zh" || saved === "en") setLocale(saved);
  } catch {
    /* private mode: stay on the default */
  }
}

export function setLocalePref(locale: "zh" | "en") {
  setLocale(locale);
  try {
    localStorage.setItem(KEY, locale);
  } catch {
    /* private mode: the choice lasts this visit */
  }
}

export function currentLocale(): "zh" | "en" {
  return getLocale();
}
