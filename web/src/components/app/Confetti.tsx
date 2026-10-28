import { motion, useReducedMotion } from "framer-motion";
import { useMemo } from "react";
import { seeded } from "../../logic/time";

const COLORS = ["var(--clay)", "var(--ink-3)", "var(--chart-3)", "var(--chart-4)", "var(--chart-5)"];

/** One-shot, quiet confetti from the top centre (small, few, fading). Purely decorative; skipped for reduced motion. */
export function Confetti({ seed = 7, count = 18 }: { seed?: number; count?: number }) {
  const reduce = useReducedMotion();
  const pieces = useMemo(() => {
    const r = seeded(seed);
    return Array.from({ length: count }, (_, i) => ({
      x: (r() - 0.5) * 300,
      y: 120 + r() * 180,
      rot: (r() - 0.5) * 900,
      w: 4 + r() * 4,
      h: 6 + r() * 6,
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
          animate={{ x: p.x, y: [0, -40, p.y], opacity: [0.9, 0.9, 0], rotate: p.rot, scale: 1 }}
          transition={{ duration: 1.3, delay: p.delay, ease: "easeOut" }}
        />
      ))}
    </div>
  );
}
