import { motion, useReducedMotion } from "framer-motion";
import { Group } from "@/components/app/kit";
import { cn } from "@/lib/utils";
import type { ShareCell } from "../logic/aggregator";
import { spring } from "../motion";

export const pct = (v: number | null) => (v === null ? "—" : `${Math.round(v * 100)}%`);

/** Neutral horizontal bar list in a grouped surface. `highlightTop` paints the largest bar in clay. */
export function BarList({ cells, highlightTop = false, ariaLabel }: { cells: ShareCell[]; highlightTop?: boolean; ariaLabel: string }) {
  const reduce = useReducedMotion();
  const top = Math.max(0, ...cells.map((c) => c.share ?? 0));
  return (
    <Group>
      <ul aria-label={ariaLabel}>
        {cells.map((c, i) => {
          const isTop = highlightTop && !c.suppressed && c.share === top;
          return (
            <li key={c.key} className="px-4 py-3">
              <div className="flex items-baseline justify-between gap-3">
                <span className="text-[15px] leading-[22px] text-ink">{c.label}</span>
                <span className={cn("num text-[15px]", c.suppressed ? "text-ink-3" : "text-ink")}>
                  {c.suppressed ? "不足 20 人" : pct(c.share)}
                </span>
              </div>
              <div className={cn("mt-2 h-2 overflow-hidden rounded-full", c.suppressed ? "hatch bg-surface-2" : "bg-fill")}>
                {c.suppressed ? null : (
                  <motion.div
                    className={cn("h-full origin-left rounded-full", isTop ? "bg-clay" : "bg-ink-3/60")}
                    style={{ width: `${(c.share ?? 0) * 100}%` }}
                    initial={reduce ? { opacity: 0 } : { scaleX: 0 }}
                    animate={reduce ? { opacity: 1 } : { scaleX: 1 }}
                    transition={{ ...spring, delay: reduce ? 0 : i * 0.04 }}
                  />
                )}
              </div>
            </li>
          );
        })}
      </ul>
    </Group>
  );
}
