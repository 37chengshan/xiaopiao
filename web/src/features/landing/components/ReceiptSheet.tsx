import { motion, useMotionValueEvent, useTransform, type MotionValue } from "framer-motion";
import { useEffect, useRef } from "react";
import { yuan, type Cents } from "@/domain/types";
import { cn } from "@/lib/utils";
import { tornBottomClip } from "../lib/paper";

export interface ReceiptLine {
  id: string;
  label: string;
  cents: Cents;
}

interface Props {
  /** 0 = blank paper, 1 = fully printed and stamped. */
  progress: MotionValue<number>;
  lines: ReceiptLine[];
  title: string;
  meta: string;
  totalLabel: string;
  totalCents: Cents;
  stamp?: string;
  /** Torn-edge tooth count; small sheets need fewer. */
  teeth?: number;
  /** Tooth depth in px. */
  toothDepth?: number;
  className?: string;
}

const clamp01 = (v: number) => (v < 0 ? 0 : v > 1 ? 1 : v);

/** Scroll windows, shared by the total roll and the stamp land. */
const TOTAL_FROM = 0.62;
const TOTAL_TO = 0.74;
const STAMP_FROM = 0.82;
const STAMP_TO = 0.9;

/**
 * The paper shared by L1 (printed out of the printer) and L2 (pinned while it fills in).
 * Rows print one after another via `clip-path` (no layout), the total rolls, then the stamp lands.
 * Nothing here re-renders while scrolling: the only DOM write is the total's text node.
 */
export function ReceiptSheet({
  progress,
  lines,
  title,
  meta,
  totalLabel,
  totalCents,
  stamp,
  teeth = 28,
  toothDepth = 6,
  className,
}: Props) {
  const totalRef = useRef<HTMLSpanElement>(null);

  const stampScale = useTransform(progress, [STAMP_FROM, STAMP_TO], [1.4, 1]);
  const stampOpacity = useTransform(progress, [STAMP_FROM - 0.04, STAMP_FROM + 0.03], [0, 1]);
  const headerOpacity = useTransform(progress, [0.01, 0.08], [0.15, 1]);
  // The paper itself grows out of the printer: the sheet's bottom edge is clipped until the lines
  // (and then the total) have printed, so no blank paper ever shows ahead of the print head.
  const reveal = useTransform(progress, [0, 0.58], [1, 0]);
  const paperClip = useTransform(reveal, (v) => `inset(0 0 ${(v * 100).toFixed(2)}% 0)`);
  const totalOpacity = useTransform(progress, [0.56, 0.64], [0, 1]);

  useMotionValueEvent(progress, "change", (value) => {
    if (!totalRef.current) return;
    const t = clamp01((value - TOTAL_FROM) / (TOTAL_TO - TOTAL_FROM));
    totalRef.current.textContent = `¥${yuan(Math.round(totalCents * t))}`;
  });

  useEffect(() => {
    if (!totalRef.current) return;
    const value = progress.get();
    const t = clamp01((value - TOTAL_FROM) / (TOTAL_TO - TOTAL_FROM));
    totalRef.current.textContent = `¥${yuan(Math.round(totalCents * t))}`;
  }, [progress, totalCents]);

  return (
    <motion.div style={{ clipPath: paperClip }}>
      <div
        className={cn(
          "receipt-sheet relative bg-d-paper font-mono text-paper-ink shadow-none",
          "px-[1.1em] pt-[1.1em] tabular-nums",
          stamp ? "pb-[6em]" : "pb-[1.6em]",
          className,
        )}
        style={{ clipPath: tornBottomClip(teeth, toothDepth) }}
      >
        <motion.div
          style={{ opacity: headerOpacity }}
          className="flex items-baseline justify-between gap-[0.6em] border-b border-dashed border-paper-ink/25 pb-[0.5em] text-[0.92em]"
        >
          <span className="font-semibold tracking-[0.02em]">{title}</span>
          <span className="text-paper-ink/55">{meta}</span>
        </motion.div>

        <ul className="flex flex-col gap-[0.32em] py-[0.55em]">
          {lines.map((line, index) => (
            <Line
              key={line.id}
              progress={progress}
              index={index}
              total={lines.length}
              line={line}
            />
          ))}
        </ul>

        <motion.div
          style={{ opacity: totalOpacity }}
          className="flex items-baseline justify-between gap-[0.6em] border-t border-dashed border-paper-ink/25 pt-[0.5em] text-[1.15em] font-semibold"
        >
          <span>{totalLabel}</span>
          <span ref={totalRef}>¥0</span>
        </motion.div>

        {stamp ? (
          <motion.div
            style={{ scale: stampScale, opacity: stampOpacity, rotate: -8 }}
            className="pointer-events-none absolute right-[1.6em] bottom-[1.3em] flex h-[4.2em] w-[4.2em] items-center justify-center rounded-full border-[0.1em] border-stamp text-[0.8em] font-semibold tracking-[0.08em] text-stamp"
          >
            {stamp}
          </motion.div>
        ) : null}
      </div>
    </motion.div>
  );
}

function Line({
  progress,
  index,
  total,
  line,
}: {
  progress: MotionValue<number>;
  index: number;
  total: number;
  line: ReceiptLine;
}) {
  const start = 0.08 + (index / total) * 0.46;
  const reveal = useTransform(progress, [start, start + 0.05], [1, 0]);
  const clipPath = useTransform(reveal, (v) => `inset(0 0 ${(v * 100).toFixed(2)}% 0)`);
  const y = useTransform(reveal, [1, 0], [2.5, 0]);

  return (
    <motion.li
      style={{ clipPath, y }}
      className="flex items-baseline gap-[0.4em] text-[0.92em] leading-[1.35em]"
    >
      <span className="shrink-0">{line.label}</span>
      <span className="min-w-[0.5em] flex-1 translate-y-[-0.12em] border-b border-dotted border-paper-ink/30" />
      <span className="shrink-0">{yuan(line.cents)}</span>
    </motion.li>
  );
}
