import { useEffect, useRef, useState } from "react";

/** True once the element has been on screen; stays true (micro-motion should not restart on scroll-up). */
export function useInViewOnce<T extends Element>(rootMargin = "-15% 0px -10% 0px") {
  const ref = useRef<T | null>(null);
  const [seen, setSeen] = useState(false);

  useEffect(() => {
    const el = ref.current;
    if (!el || seen) return;
    if (typeof IntersectionObserver === "undefined") {
      setSeen(true);
      return;
    }
    const io = new IntersectionObserver(
      (entries) => {
        if (entries.some((e) => e.isIntersecting)) {
          setSeen(true);
          io.disconnect();
        }
      },
      { rootMargin },
    );
    io.observe(el);
    return () => io.disconnect();
  }, [rootMargin, seen]);

  return [ref, seen] as const;
}

/**
 * Counts up to `to` (cents) once `active` flips, on one rAF loop, and then keeps drifting by `drift`
 * every `period` ms so the number stays alive without looking like a random generator.
 */
export function useCountUp(to: number, active: boolean, { duration = 1100, drift = 0, period = 6200 } = {}) {
  const [value, setValue] = useState(0);
  const started = useRef(false);
  const driftRef = useRef(0);

  useEffect(() => {
    if (!active || started.current) return;
    started.current = true;
    const reduce = typeof window !== "undefined" && window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    if (reduce) {
      setValue(to);
      return;
    }
    let frame = 0;
    const startAt = performance.now();
    const tick = (now: number) => {
      const p = Math.min(1, (now - startAt) / duration);
      const eased = 1 - Math.pow(1 - p, 3);
      setValue(Math.round(to * eased));
      if (p < 1) frame = requestAnimationFrame(tick);
    };
    frame = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(frame);
  }, [active, to, duration]);

  useEffect(() => {
    if (!active || drift === 0) return;
    const id = window.setInterval(() => {
      driftRef.current = driftRef.current === 0 ? drift : 0;
      setValue(to + driftRef.current);
    }, period);
    return () => window.clearInterval(id);
  }, [active, drift, period, to]);

  return value;
}

/** A value that steps through `count` options on a timer, only while `active`. */
export function useRotator(count: number, active: boolean, period = 2400) {
  const [index, setIndex] = useState(0);
  useEffect(() => {
    if (!active || count < 2) return;
    const id = window.setInterval(() => setIndex((i) => (i + 1) % count), period);
    return () => window.clearInterval(id);
  }, [active, count, period]);
  return index;
}
