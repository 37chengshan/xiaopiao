import { useEffect, useRef, useState } from "react";

/** Debounce a changing value (search boxes: 300ms, docs/WEB-SPEC.md §A.3). */
export function useDebounced<T>(value: T, ms = 300): T {
  const [out, setOut] = useState(value);
  useEffect(() => {
    const id = window.setTimeout(() => setOut(value), ms);
    return () => window.clearTimeout(id);
  }, [value, ms]);
  return out;
}

export function useMediaQuery(query: string): boolean {
  const [matches, setMatches] = useState(() => (typeof window === "undefined" ? false : window.matchMedia(query).matches));
  useEffect(() => {
    const mq = window.matchMedia(query);
    const on = () => setMatches(mq.matches);
    on();
    mq.addEventListener("change", on);
    return () => mq.removeEventListener("change", on);
  }, [query]);
  return matches;
}

/** localStorage flag that survives reloads (sidebar collapse, dismissed notices are session-only instead). */
export function useFlag(key: string, initial = false): [boolean, (v: boolean) => void] {
  const [value, setValue] = useState(() => {
    try {
      const raw = localStorage.getItem(key);
      return raw === null ? initial : raw === "1";
    } catch {
      return initial;
    }
  });
  const set = (v: boolean) => {
    setValue(v);
    try {
      localStorage.setItem(key, v ? "1" : "0");
    } catch {
      /* private mode: the choice lasts this visit */
    }
  };
  return [value, set];
}

type Hotkeys = {
  onRecord?: () => void;
  onSearch?: () => void;
  onPrint?: () => void;
  onPalette?: () => void;
  onToggleSidebar?: () => void;
};

/** Text entry only: a focused checkbox or radio must not swallow N / P / slash. */
const TEXT_INPUT_TYPES = new Set(["", "text", "search", "number", "date", "datetime-local", "month", "week", "time", "email", "password", "tel", "url"]);

function isTyping(target: EventTarget | null): boolean {
  if (!(target instanceof HTMLElement)) return false;
  if (target.isContentEditable) return true;
  if (target instanceof HTMLTextAreaElement || target instanceof HTMLSelectElement) return true;
  if (target instanceof HTMLInputElement) return TEXT_INPUT_TYPES.has(target.type);
  return false;
}

/**
 * Global shortcuts (docs/WEB-SPEC.md §A.3): N 记一笔, / 搜索, P 打印今天, ⌘K 命令面板, ⌘\ 折叠侧边栏.
 * Letter keys stay out of the way while a field has focus.
 */
export function useHotkeys(h: Hotkeys) {
  const ref = useRef(h);
  ref.current = h;
  useEffect(() => {
    const on = (e: KeyboardEvent) => {
      const mod = e.metaKey || e.ctrlKey;
      if (mod && e.key.toLowerCase() === "k") {
        e.preventDefault();
        ref.current.onPalette?.();
        return;
      }
      if (mod && e.key === "\\") {
        e.preventDefault();
        ref.current.onToggleSidebar?.();
        return;
      }
      if (mod || e.altKey || isTyping(e.target)) return;
      const key = e.key.toLowerCase();
      if (key === "n") {
        e.preventDefault();
        ref.current.onRecord?.();
      } else if (key === "/") {
        e.preventDefault();
        ref.current.onSearch?.();
      } else if (key === "p") {
        e.preventDefault();
        ref.current.onPrint?.();
      }
    };
    window.addEventListener("keydown", on);
    return () => window.removeEventListener("keydown", on);
  }, []);
}

/** Session-only dismissal for the onboarding notice (reappears on reload, as specified). */
let noticeDismissed = false;
const noticeListeners = new Set<() => void>();

export function dismissNotice() {
  noticeDismissed = true;
  noticeListeners.forEach((f) => f());
}

export function useNoticeDismissed(): boolean {
  const [, force] = useState(0);
  useEffect(() => {
    const f = () => force((n) => n + 1);
    noticeListeners.add(f);
    return () => {
      noticeListeners.delete(f);
    };
  }, []);
  return noticeDismissed;
}

/**
 * True while the first skeleton must stay up: at least `minMs` from mount, and while data is loading.
 * Keeps the first paint honest instead of flashing a spinner for 40ms.
 */
export function useFirstPaint(loading: boolean, delayMs = 60, maxWaitMs = 1400): boolean {
  // The skeleton is a fallback for genuinely slow data, never a designed frame: it only appears if
  // loading is still true after `delayMs`, and gives up after `maxWaitMs` so a hung ledger reaches
  // the page's empty state instead of an endless skeleton.
  const [late, setLate] = useState(false);
  const [expired, setExpired] = useState(false);
  useEffect(() => {
    const slow = window.setTimeout(() => setLate(true), delayMs);
    const max = window.setTimeout(() => setExpired(true), maxWaitMs);
    return () => {
      window.clearTimeout(slow);
      window.clearTimeout(max);
    };
  }, [delayMs, maxWaitMs]);
  return loading && late && !expired;
}
