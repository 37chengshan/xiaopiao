import { AnimatePresence, motion, useReducedMotion } from "framer-motion";
import { useMemo } from "react";
import { EASE_OUT } from "@/lib/motion";
import { cn } from "@/lib/utils";
import { useBizStrings } from "../strings";
import { fill } from "../text";
import type { SummaryLine } from "../summaryText";
import { Card } from "./primitives";

const TONE_COLOR: Record<SummaryLine["tone"], string> = {
  up: "var(--d-positive)",
  down: "var(--d-negative)",
  warn: "var(--d-warning)",
  flat: "var(--d-ink-3)",
};

/**
 * The "本周小结" card (docs/WEB-SPEC.md §A.5 #9): a fixed-height box, so a new sentence never
 * pushes the page around — the old text lifts out and the new one rises in behind it.
 */
export function SummaryCard({
  lines,
  updatedAt,
  className,
  index,
  minHeight = 232,
}: {
  lines: SummaryLine[];
  updatedAt: number;
  className?: string;
  index?: number;
  minHeight?: number;
}) {
  const t = useBizStrings();
  const reduce = useReducedMotion() ?? false;
  const key = useMemo(() => lines.map((line) => line.text).join("|"), [lines]);
  const time = new Date(updatedAt).toLocaleTimeString("zh-CN", { hour: "2-digit", minute: "2-digit", hour12: false });

  return (
    <Card
      className={className}
      index={index}
      title={t("summary.title")}
      action={<span className="text-[11px] text-d-ink-3">{t("summary.honest")}</span>}
    >
      <div className="flex flex-col" style={{ minHeight }}>
        <div className="relative flex-1">
          <AnimatePresence mode="wait" initial={false}>
            <motion.div
              key={key}
              initial={reduce ? { opacity: 0 } : { opacity: 0, y: 8 }}
              animate={{ opacity: 1, y: 0 }}
              exit={reduce ? { opacity: 0 } : { opacity: 0, y: -8 }}
              transition={{ duration: reduce ? 0.12 : 0.22, ease: EASE_OUT }}
            >
              {lines.length === 0 ? (
                <p className="py-8 text-center text-[12px] leading-5 text-d-ink-2">{t("summary.empty")}</p>
              ) : (
                <ul className="space-y-3">
                  {lines.map((line) => (
                    <li key={line.text} className="flex gap-2.5 text-[12px] leading-5">
                      <span className="mt-[7px] size-1.5 shrink-0 rounded-full" style={{ backgroundColor: TONE_COLOR[line.tone] }} aria-hidden />
                      <span className="text-d-ink-2">{line.text}</span>
                    </li>
                  ))}
                </ul>
              )}
            </motion.div>
          </AnimatePresence>
        </div>
        <p className={cn("mt-3 text-right text-[11px] tabular-nums text-d-ink-3")}>{fill(t("summary.updated"), time)}</p>
      </div>
    </Card>
  );
}
