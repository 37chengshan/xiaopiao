import { motion } from "framer-motion";
import { Lock } from "lucide-react";
import { useId } from "react";
import { cn } from "@/lib/utils";
import type { StepIndex } from "../logic/state";
import { springSnap } from "../motion";

const STEPS = [
  { n: "①", label: "洞察" },
  { n: "②", label: "实验" },
  { n: "③", label: "结果" },
  { n: "④", label: "决定" },
] as const;

/**
 * Four-step segmented control (Apple style). Same look as the shared Segmented, but steps can be locked:
 * a locked step is dimmed to ink-3, shows a small lock and can't be pressed.
 */
export function Stepper({
  step,
  reachable,
  onGo,
}: {
  step: StepIndex;
  reachable: (i: number) => boolean;
  onGo: (i: StepIndex) => void;
}) {
  const id = useId();
  return (
    <nav aria-label="四步流程" className="sticky top-11 z-10 bg-bg px-4 pb-2 pt-1">
      <ol className="flex rounded-[10px] bg-fill p-[2px]">
        {STEPS.map((s, i) => {
          const active = i === step;
          const ok = reachable(i);
          return (
            <li key={s.label} className="flex-1">
              <button
                type="button"
                disabled={!ok}
                aria-current={active ? "step" : undefined}
                onClick={() => onGo(i as StepIndex)}
                className={cn(
                  "relative flex h-8 w-full items-center justify-center gap-1 rounded-[8px] text-[13px] font-medium",
                  active ? "text-ink" : ok ? "text-ink-2" : "cursor-not-allowed text-ink-3",
                )}
              >
                {active && (
                  <motion.span
                    layoutId={`step-${id}`}
                    className="absolute inset-0 rounded-[8px] bg-surface shadow-[0_1px_3px_oklch(0_0_0/12%)] dark:bg-surface-2"
                    transition={springSnap}
                  />
                )}
                <span className="relative">
                  {s.n} {s.label}
                </span>
                {!ok && <Lock className="relative size-3" strokeWidth={2} aria-label="未解锁" />}
              </button>
            </li>
          );
        })}
      </ol>
    </nav>
  );
}
