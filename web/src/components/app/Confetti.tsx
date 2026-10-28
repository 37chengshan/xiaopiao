import { motion, useReducedMotion } from "framer-motion";
import { useMemo } from "react";
import { seeded } from "../../logic/time";

const COLORS = ["var(--coral)", "var(--stamp)", "var(--good)", "var(--chart-3)", "var(--chart-2)", "var(--chart-5)"];

/** One-shot confetti burst from the top centre. Purely decorative; skipped for reduced motion. */
export function Confetti({ seed = 7, count = 36 }: { seed?: number; count?: number }) {
  const reduce = useReducedMotion();
  const pieces = useMemo(() => {
    const r = seeded(seed);
    return Array.from({ length: count }, (_, i) => ({
      x: (r() - 0.5) * 360,
      y: 140 + r() * 260,
      rot: (r() - 0.5) * 900,
      w: 6 + r() * 6,
      h: 8 + r() * 8,
      color: COLORS[i % COLORS.length],
      delay: r() * 0.25,
    }));
  }, [seed, count]);
  if (reduce) return null;
  return (
    <div className="pointer-events-none absolute inset-x-0 top-0 z-30 flex justify-center overflow-visible" aria-hidden>
      {pieces.map((p, i) => (
        <motion.span
          key={i}
          className="absolute top-8 block rounded-[2px]"
          style={{ width: p.w, height: p.h, background: p.color }}
          initial={{ x: 0, y: 0, opacity: 1, rotate: 0, scale: 0.6 }}
          animate={{ x: p.x, y: [0, -60, p.y], opacity: [1, 1, 0], rotate: p.rot, scale: 1 }}
          transition={{ duration: 1.6, delay: p.delay, ease: "easeOut" }}
        />
      ))}
    </div>
  );
}
