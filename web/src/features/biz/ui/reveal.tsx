import { motion, useReducedMotion } from "framer-motion";
import { useEffect, useRef, useState, type ReactNode } from "react";
import { EASE_OUT } from "@/lib/motion";
import { cn } from "@/lib/utils";

/**
 * First-entry motion (docs/WEB-SPEC.md §A.4): KPI and cards float up 8px and fade in, 40ms apart,
 * finishing inside 400ms. Reduced motion keeps only a 120ms fade.
 */
export function Reveal({
  index = 0,
  children,
  className,
}: {
  index?: number;
  children: ReactNode;
  className?: string;
}) {
  const reduce = useReducedMotion() ?? false;
  return (
    <motion.div
      className={className}
      initial={reduce ? { opacity: 0 } : { opacity: 0, y: 8 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: reduce ? 0.12 : 0.26, delay: Math.min(index * 0.04, 0.24), ease: EASE_OUT }}
    >
      {children}
    </motion.div>
  );
}

/** Counts a number up on first paint; reduced motion shows the final value straight away. */
export function useCountUp(target: number, duration = 600): number {
  const reduce = useReducedMotion() ?? false;
  const [value, setValue] = useState(() => (reduce ? target : 0));
  const frame = useRef(0);

  useEffect(() => {
    if (reduce) {
      setValue(target);
      return;
    }
    const start = performance.now();
    const from = 0;
    const tick = (now: number) => {
      const t = Math.min(1, (now - start) / duration);
      const eased = 1 - Math.pow(1 - t, 3);
      setValue(Math.round(from + (target - from) * eased));
      if (t < 1) frame.current = requestAnimationFrame(tick);
    };
    frame.current = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(frame.current);
  }, [target, duration, reduce]);

  return value;
}

/** Money or count that rolls up once, then sits still. */
export function CountUp({
  cents,
  format,
  className,
}: {
  cents: number;
  format: (value: number) => string;
  className?: string;
}) {
  const value = useCountUp(cents);
  return <span className={cn("tabular-nums", className)}>{format(value)}</span>;
}

/** Skeleton block for a chart that is still loading its chunk: pulsing, never a white flash. */
export function ChartSkeleton({ height }: { height: number }) {
  return <div className="biz-skeleton rounded-lg bg-d-fill" style={{ height }} aria-hidden />;
}
