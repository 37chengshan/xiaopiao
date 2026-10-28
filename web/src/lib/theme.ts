import { useSyncExternalStore } from "react";

/** 跟随系统 / 浅色 / 深色 (docs/DESIGN.md §6). Only the UI preference is stored locally. */
export type ThemePref = "system" | "light" | "dark";

const KEY = "xiaopiao.theme";
const media = () => window.matchMedia("(prefers-color-scheme: dark)");
const listeners = new Set<() => void>();

function readPref(): ThemePref {
  try {
    const v = localStorage.getItem(KEY);
    return v === "light" || v === "dark" ? v : "system";
  } catch {
    return "system";
  }
}

let pref: ThemePref = "system";

function apply() {
  const dark = pref === "dark" || (pref === "system" && media().matches);
  const root = document.documentElement;
  root.classList.toggle("dark", dark);
  const bg = getComputedStyle(root).getPropertyValue("--bg").trim();
  let meta = document.querySelector<HTMLMetaElement>('meta[name="theme-color"]');
  if (!meta) {
    meta = document.createElement("meta");
    meta.name = "theme-color";
    document.head.appendChild(meta);
  }
  if (bg) meta.content = bg;
  listeners.forEach((l) => l());
}

/** Call once before the first render so there is no light→dark flash. */
export function initTheme() {
  pref = readPref();
  apply();
  media().addEventListener("change", () => pref === "system" && apply());
}

export function setThemePref(next: ThemePref) {
  pref = next;
  try {
    localStorage.setItem(KEY, next);
  } catch {
    /* private mode: choice lasts for this visit */
  }
  apply();
}

function subscribe(l: () => void) {
  listeners.add(l);
  return () => listeners.delete(l);
}

/** Current preference and resolved appearance, re-rendering on change. */
export function useTheme() {
  const current = useSyncExternalStore(subscribe, () => pref);
  const dark = useSyncExternalStore(subscribe, () => document.documentElement.classList.contains("dark"));
  return { pref: current, dark, setPref: setThemePref };
}
