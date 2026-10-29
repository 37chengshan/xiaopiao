import { getLocale, setLocale, type Locale } from "@/lib/strings";

/**
 * Language preference + `<html lang>` (docs/WEB-SPEC.md §0.1). Shared by the landing page and the
 * desktop shell so one toggle covers the whole site; the choice is the only thing we keep on device.
 */
const KEY = "xiaopiao.d.locale";

export function syncDocumentLang() {
  const root = document.documentElement;
  const locale = getLocale();
  // `data-locale` is ours alone (CSS keys off it); `lang` is also asserted against the QMuse
  // preview runtime, which rewrites it to the document default after load.
  root.setAttribute("data-locale", locale);
  root.lang = locale === "zh" ? "zh-CN" : "en";
}

/** Switch language everywhere: store, re-render, and tell assistive tech. */
export function applyLocale(locale: Locale) {
  setLocale(locale);
  syncDocumentLang();
  try {
    localStorage.setItem(KEY, locale);
  } catch {
    /* private mode: the choice lasts this visit */
  }
}

/** Re-apply the saved choice on mount (the document starts in zh). */
export function restoreLocale() {
  try {
    const saved = localStorage.getItem(KEY);
    if (saved === "zh" || saved === "en") setLocale(saved);
  } catch {
    /* private mode: stay on the default */
  }
  syncDocumentLang();
}

/**
 * Apply the saved choice as soon as this module is imported, so the first render already speaks the
 * right language (copy memoised during that render would otherwise stay stale after an effect).
 */
if (typeof window !== "undefined") {
  try {
    const saved = localStorage.getItem(KEY);
    if (saved === "zh" || saved === "en") setLocale(saved);
  } catch {
    /* private mode: stay on the default */
  }
  syncDocumentLang();
}

/** Keep `lang` truthful: the preview runtime rewrites <html lang> to zh-CN shortly after boot. */
if (typeof window !== "undefined" && typeof MutationObserver !== "undefined") {
  const expected = () => (getLocale() === "zh" ? "zh-CN" : "en");
  new MutationObserver(() => {
    if (document.documentElement.lang !== expected()) document.documentElement.lang = expected();
  }).observe(document.documentElement, { attributes: true, attributeFilter: ["lang"] });
}
