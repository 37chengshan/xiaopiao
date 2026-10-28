import { motion, useMotionValue, useTransform, type MotionValue } from "framer-motion";
import type { ExperimentOption } from "@/features/merchant/logic/experiment";
import { cn } from "@/lib/utils";

/** Soft floating-card shadow; on dark the hairline ring does the separating. */
export const CARD_SHADOW = "shadow-[0_1px_2px_oklch(0_0_0/6%),0_14px_32px_-14px_oklch(0_0_0/22%)] ring-1 ring-hairline";

function Half({
  o,
  side,
  hint,
  compact,
}: {
  o: ExperimentOption;
  side: "left" | "right";
  /** 0–1 drag progress toward this side */
  hint: MotionValue<number>;
  compact?: boolean;
}) {
  const wash = useTransform(hint, (v) => v * 0.9);
  const lift = useTransform(hint, (v) => 1 + v * 0.04);
  return (
    <div className="relative flex flex-1 flex-col items-center justify-center px-3 text-center">
      <motion.div aria-hidden className="absolute inset-0 bg-clay-soft" style={{ opacity: wash }} />
      <motion.div className="relative" style={{ scale: lift }}>
        <p className={cn("font-semibold leading-snug text-ink", compact ? "text-[15px]" : "text-[18px]")}>{o.label}</p>
        {o.blurb && <p className="mt-1 text-[13px] leading-[18px] text-ink-2">{o.blurb}</p>}
        <p className="mt-3 text-[11px] font-medium tracking-[0.02em] text-ink-3">{side === "left" ? "← 左滑" : "右滑 →"}</p>
      </motion.div>
    </div>
  );
}

/** The question card face — used by the live swipe deck and the merchant's consumer preview. */
export function QuestionFace({
  kicker,
  question,
  options,
  leftHint,
  rightHint,
  compact,
  className,
}: {
  kicker: string;
  question: string;
  options: readonly [ExperimentOption, ExperimentOption];
  leftHint?: MotionValue<number>;
  rightHint?: MotionValue<number>;
  compact?: boolean;
  className?: string;
}) {
  const idle = useMotionValue(0);
  return (
    <div className={cn("flex h-full flex-col overflow-hidden rounded-[24px] bg-surface", CARD_SHADOW, className)}>
      <div className={cn("shrink-0 px-6 text-center", compact ? "pb-4 pt-5" : "pb-5 pt-7")}>
        <p className="text-[11px] font-medium tracking-[0.02em] text-ink-3">{kicker}</p>
        <h2 className={cn("mt-2 font-serif font-semibold tracking-[-0.01em] text-ink", compact ? "text-[18px] leading-[26px]" : "text-[24px] leading-[32px]")}>
          {question}
        </h2>
      </div>
      <div className="flex min-h-0 flex-1 border-t border-hairline">
        <Half o={options[0]} side="left" hint={leftHint ?? idle} compact={compact} />
        <div className="w-px bg-hairline" aria-hidden />
        <Half o={options[1]} side="right" hint={rightHint ?? idle} compact={compact} />
      </div>
    </div>
  );
}
