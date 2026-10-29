import { getLocale } from "@/lib/strings";
import { applyLocale, restoreLocale } from "@/lib/locale";

/**
 * Language choice is a preference, so it is the one thing the desktop shell may keep in localStorage
 * (docs/WEB-SPEC.md §0.1). Ledger data never goes there. Storage + `<html lang>` live in lib/locale.
 */
export function initLocalePref() {
  restoreLocale();
}

export function setLocalePref(locale: "zh" | "en") {
  applyLocale(locale);
}

export function currentLocale(): "zh" | "en" {
  return getLocale();
}
