import { motion, useReducedMotion } from "framer-motion";
import { spring } from "../motion";

/** Single-hue progress ring (ink track, clay arc). `value` 0..1. */
export function Ring({ value, size = 56, label }: { value: number; size?: number; label: string }) {
  const reduce = useReducedMotion();
  const r = (size - 8) / 2;
  const c = 2 * Math.PI * r;
  const v = Math.max(0, Math.min(1, value));
  return (
    <svg width={size} height={size} viewBox={`0 0 ${size} ${size}`} role="img" aria-label={label} className="shrink-0 -rotate-90">
      <circle cx={size / 2} cy={size / 2} r={r} fill="none" strokeWidth={6} className="stroke-fill" />
      <motion.circle
        cx={size / 2}
        cy={size / 2}
        r={r}
        fill="none"
        strokeWidth={6}
        strokeLinecap="round"
        className="stroke-clay"
        strokeDasharray={c}
        initial={reduce ? false : { strokeDashoffset: c }}
        animate={{ strokeDashoffset: c * (1 - v) }}
        transition={spring}
      />
    </svg>
  );
}

/** Thin neutral meter, for "x of y" shares. */
export function Meter({ value, label }: { value: number; label: string }) {
  const v = Math.max(0, Math.min(1, value));
  return (
    <div role="img" aria-label={label} className="h-1.5 overflow-hidden rounded-full bg-fill">
      <motion.div className="h-full origin-left rounded-full bg-ink/70" style={{ width: `${v * 100}%` }} initial={{ scaleX: 0 }} animate={{ scaleX: 1 }} transition={spring} />
    </div>
  );
}
