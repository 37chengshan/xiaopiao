import { useCallback, useSyncExternalStore } from "react";
import { useReducedMotion } from "framer-motion";

/** Reactive `matchMedia`. No-op without a DOM so the first client render matches SSR defaults. */
export function useMediaQuery(query: string, fallback = false): boolean {
  const subscribe = useCallback(
    (onChange: () => void) => {
      const mq = window.matchMedia(query);
      mq.addEventListener("change", onChange);
      return () => mq.removeEventListener("change", onChange);
    },
    [query],
  );
  const getSnapshot = useCallback(() => window.matchMedia(query).matches, [query]);
  return useSyncExternalStore(subscribe, getSnapshot, () => fallback);
}

/** Touch / pen devices: every pointer-driven effect in the landing is skipped there (§0.1). */
export function useCoarsePointer(): boolean {
  return useMediaQuery("(pointer: coarse)");
}

/** Desktop layout breakpoint used by the accordion and the sticky sections. */
export function useDesktop(): boolean {
  return useMediaQuery("(min-width: 1024px)", true);
}

/** L.1 — the halo (and `cursor: none`) only exists for a fine pointer with motion allowed. */
export function useHaloEnabled(): boolean {
  const reduceMotion = useReducedMotion();
  const coarse = useCoarsePointer();
  const fine = useMediaQuery("(pointer: fine)", true);
  return !reduceMotion && !coarse && fine;
}
