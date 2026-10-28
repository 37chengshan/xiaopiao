import { AnimatePresence, motion } from "framer-motion";
import { Check } from "lucide-react";
import { toast } from "sonner";
import { cn } from "@/lib/utils";
import type { Insights } from "../logic/aggregator";
import type { Experiment } from "../logic/experiment";
import { generateSuggestions } from "../logic/suggestions";
import { spring } from "../tokens";
import { Card, EmptyState, GhostButton, PrimaryButton } from "./common";

export function DecideStep({
  experiment,
  insights,
  chosenId,
  onChoose,
  onRestart,
  onBackToResults,
}: {
  experiment: Experiment | null;
  insights: Insights;
  chosenId: string | null;
  onChoose: (id: string) => void;
  onRestart: () => void;
  onBackToResults: () => void;
}) {
  if (!experiment || experiment.status === "draft") {
    return (
      <EmptyState
        emoji="🤔"
        title="先有结果，再做决定"
        body="发布并看过实验结果后，这里会给你 3 个可选的下一步，由你来选。"
        action={<PrimaryButton onClick={onBackToResults}>回到上一步</PrimaryButton>}
      />
    );
  }
  const suggestions = generateSuggestions(experiment, insights);
  const chosen = suggestions.find((s) => s.id === chosenId) ?? null;

  return (
    <div className="space-y-3">
      <Card className="border-transparent bg-(--mb-coral-soft)">
        <p className="text-[14px] font-bold">你来决定 👆</p>
        <p className="mt-1 text-[12px] leading-relaxed text-(--mb-ink-soft)">
          下面 3 个只是备选思路，每个都写了好处和代价。选哪个、做不做，都由你说了算；这里不会承诺任何收入变化。
        </p>
      </Card>

      <div role="radiogroup" aria-label="下一步的 3 个选择" className="space-y-2.5">
        {suggestions.map((s, i) => {
          const selected = s.id === chosenId;
          return (
            <motion.button
              key={s.id}
              type="button"
              role="radio"
              aria-checked={selected}
              onClick={() => {
                onChoose(s.id);
                toast(`已选择：${s.title}`, { description: "两周后复盘一下效果吧" });
              }}
              initial={{ opacity: 0, y: 16 }}
              animate={{ opacity: 1, y: 0, scale: selected ? 1.02 : 1 }}
              transition={{ ...spring, delay: i * 0.06 }}
              whileTap={{ scale: 0.98 }}
              className={cn(
                "relative w-full rounded-2xl border bg-(--mb-card) p-4 text-left outline-offset-2 focus-visible:outline-2 focus-visible:outline-(--mb-ink)",
                selected ? "border-(--mb-coral) shadow-[0_0_0_3px_var(--mb-coral-soft)]" : "border-(--mb-line)",
              )}
            >
              <AnimatePresence>
                {selected ? (
                  <motion.span
                    initial={{ scale: 0, rotate: -90 }}
                    animate={{ scale: 1, rotate: 0 }}
                    exit={{ scale: 0 }}
                    transition={spring}
                    className="absolute right-3 top-3 grid size-6 place-items-center rounded-full bg-(--mb-coral) text-white"
                  >
                    <Check className="size-3.5" aria-label="已选择" />
                  </motion.span>
                ) : null}
              </AnimatePresence>
              <p className="pr-8 text-[15px] font-bold">
                <span aria-hidden>{s.emoji} </span>
                {s.title}
              </p>
              <p className="mt-1 text-[12px] leading-relaxed text-(--mb-ink-soft)">{s.summary}</p>
              <dl className="mt-3 space-y-1.5 text-[12px] leading-relaxed">
                <div className="flex gap-2">
                  <dt className="shrink-0 font-bold text-(--mb-ink)">好处</dt>
                  <dd className="text-(--mb-ink-soft)">{s.pros}</dd>
                </div>
                <div className="flex gap-2">
                  <dt className="shrink-0 font-bold text-(--mb-stamp)">代价</dt>
                  <dd className="text-(--mb-ink-soft)">{s.cons}</dd>
                </div>
              </dl>
            </motion.button>
          );
        })}
      </div>

      <AnimatePresence>
        {chosen ? (
          <motion.div
            initial={{ opacity: 0, y: 16, scale: 0.97 }}
            animate={{ opacity: 1, y: 0, scale: 1 }}
            exit={{ opacity: 0 }}
            transition={spring}
            aria-live="polite"
          >
            <Card className="border-(--mb-coral)">
              <p className="text-[14px] font-bold">好，就这个：{chosen.title}</p>
              <p className="mt-1.5 text-[12px] leading-relaxed text-(--mb-ink-soft)">
                <b className="text-(--mb-ink)">第一步：</b>
                {chosen.firstStep}
              </p>
              <p className="mt-1.5 rounded-xl bg-(--mb-canvas) px-3 py-2 text-[12px] leading-relaxed">
                🗓️ {chosen.reviewNote}
              </p>
            </Card>
          </motion.div>
        ) : null}
      </AnimatePresence>

      <GhostButton onClick={onRestart}>换个模板，重新来一遍</GhostButton>
    </div>
  );
}
