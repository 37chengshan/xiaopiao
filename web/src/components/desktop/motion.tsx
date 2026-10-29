import { animate, motion, useReducedMotion } from "framer-motion";
import { useEffect, useRef, useState, type ReactNode } from "react";
import { cn } from "@/lib/utils";

/**
 * Desktop motion kit (docs/WEB-SPEC.md §A.4). Only transform / opacity / clip-path; every helper falls
 * back to a short fade when the user asks for reduced motion.
 */
export const EASE_OUT: [number, number, number, number] = [0.23, 1, 0.32, 1];
/** Sidebar active block sliding between items. */
export const SPRING_SLIDE = { type: "spring", stiffness: 400, damping: 35 } as const;
/** Shared-element morphs (receipt widget → print preview). */
export const SPRING_SHEET = { type: "spring", stiffness: 300, damping: 26 } as const;

/** Card / KPI entrance: y 8→0 + fade, 40ms apart, never longer than 400ms in total. */
export function Stagger({ index = 0, disabled = false, className, children }: { index?: number; disabled?: boolean; className?: string; children: ReactNode }) {
  const reduce = useReducedMotion() ?? false;
  if (disabled || reduce) {
    return (
      <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} transition={{ duration: 0.12 }} className={className}>
        {children}
      </motion.div>
    );
  }
  // 40ms apart, but the whole entrance never exceeds 400ms (delay 200 + 200 duration).
  const delay = Math.min(index * 0.04, 0.2);
  return (
    <motion.div initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.2, ease: EASE_OUT, delay }} className={className}>
      {children}
    </motion.div>
  );
}

/** KPI numbers roll from 0 once, 600ms ease-out (starting again only when the value type changes). */
export function useCountUp(value: number, ms = 600): number {
  const reduce = useReducedMotion() ?? false;
  const [shown, setShown] = useState(reduce ? value : 0);
  const first = useRef(true);
  useEffect(() => {
    if (reduce) {
      setShown(value);
      return;
    }
    const from = first.current ? 0 : shown;
    first.current = false;
    const controls = animate(from, value, {
      duration: ms / 1000,
      ease: EASE_OUT,
      onUpdate: (v) => setShown(Math.round(v)),
    });
    return () => controls.stop();
    // `shown` is read once on purpose: the animation should start where the last one ended, not restart on every tick.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [value, ms, reduce]);
  return shown;
}

/**
 * Skeleton → content crossfade. The skeleton itself stays up at least `minMs` (see useFirstPaint),
 * then fades out over 160ms while the content fades in.
 */
export function Loadable({ loading, skeleton, children, className }: { loading: boolean; skeleton: ReactNode; children: ReactNode; className?: string }) {
  return (
    <div className={cn("relative", className)}>
      {loading ? (
        <motion.div key="skeleton" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} transition={{ duration: 0.16 }}>
          {skeleton}
        </motion.div>
      ) : (
        <motion.div key="content" initial={{ opacity: 0 }} animate={{ opacity: 1 }} transition={{ duration: 0.16 }}>
          {children}
        </motion.div>
      )}
    </div>
  );
}
