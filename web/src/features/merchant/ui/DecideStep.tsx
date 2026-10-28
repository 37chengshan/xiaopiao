import { AnimatePresence, motion } from "framer-motion";
import { CalendarClock, Check, Compass } from "lucide-react";
import { toast } from "sonner";
import { Group, IconTile, SectionHeader, XButton } from "@/components/app/kit";
import { EmptyState } from "@/components/app/States";
import { cn } from "@/lib/utils";
import type { Insights } from "../logic/aggregator";
import type { Experiment } from "../logic/experiment";
import { generateSuggestions } from "../logic/suggestions";
import { spring, springPop } from "../motion";

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
        icon={Compass}
        title="先有结果，再做决定"
        hint="发布并看过实验结果后，这里会给你 3 个可选的下一步，由你来选。"
        action={
          <XButton variant="secondary" onClick={onBackToResults}>
            回到上一步
          </XButton>
        }
      />
    );
  }
  const suggestions = generateSuggestions(experiment, insights);
  const chosen = suggestions.find((s) => s.id === chosenId) ?? null;

  return (
    <div className="pb-6">
      <p className="px-4 pt-1 text-[13px] leading-[18px] text-ink-2">
        下面 3 个只是备选思路，每个都写了好处和代价。选哪个、做不做，都由你说了算；这里不会承诺任何收入变化。
      </p>

      <SectionHeader>下一步的 3 个选择</SectionHeader>
      <Group>
        <div role="radiogroup" aria-label="下一步的 3 个选择">
          {suggestions.map((s) => {
            const selected = s.id === chosenId;
            return (
              <button
                key={s.id}
                type="button"
                role="radio"
                aria-checked={selected}
                onClick={() => {
                  onChoose(s.id);
                  toast(`已选择：${s.title}`, { description: "两周后复盘一下效果吧" });
                }}
                className={cn("flex w-full items-start gap-3 px-4 py-4 text-left transition-colors duration-100 active:bg-surface-2", selected && "bg-clay-soft/40")}
              >
                <span className="min-w-0 flex-1">
                  <span className="block text-[16px] font-medium leading-[22px] text-ink">{s.title}</span>
                  <span className="mt-0.5 block text-[13px] leading-[18px] text-ink-2">{s.summary}</span>
                  <span className="mt-2.5 block space-y-1 text-[13px] leading-[18px]">
                    <span className="flex gap-2">
                      <span className="w-7 shrink-0 font-medium text-positive">好处</span>
                      <span className="text-ink">{s.pros}</span>
                    </span>
                    <span className="flex gap-2">
                      <span className="w-7 shrink-0 font-medium text-negative">代价</span>
                      <span className="text-ink">{s.cons}</span>
                    </span>
                  </span>
                </span>
                <span
                  className={cn(
                    "mt-0.5 grid size-6 shrink-0 place-items-center rounded-full border transition-colors duration-150",
                    selected ? "border-clay bg-clay" : "border-ink-3",
                  )}
                  aria-hidden
                >
                  <AnimatePresence>
                    {selected && (
                      <motion.span initial={{ scale: 0.3, opacity: 0 }} animate={{ scale: 1, opacity: 1 }} exit={{ opacity: 0, transition: { duration: 0.1 } }} transition={springPop}>
                        <Check className="size-3.5 text-on-clay" strokeWidth={3} />
                      </motion.span>
                    )}
                  </AnimatePresence>
                </span>
              </button>
            );
          })}
        </div>
      </Group>

      <AnimatePresence initial={false}>
        {chosen && (
          <motion.div
            key={chosen.id}
            initial={{ opacity: 0, y: 10 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, transition: { duration: 0.12 } }}
            transition={spring}
            aria-live="polite"
          >
            <SectionHeader>你的决定</SectionHeader>
            <Group>
              <div className="px-4 py-3.5">
                <p className="text-[16px] font-medium leading-[22px] text-ink">好，就这个：{chosen.title}</p>
                <p className="mt-1 text-[13px] leading-[18px] text-ink-2">
                  <span className="font-medium text-ink">第一步　</span>
                  {chosen.firstStep}
                </p>
              </div>
              <div className="flex items-start gap-3 px-4 py-3.5">
                <IconTile icon={CalendarClock} />
                <p className="min-w-0 flex-1 pt-1 text-[13px] leading-[18px] text-ink-2">{chosen.reviewNote}</p>
              </div>
            </Group>
          </motion.div>
        )}
      </AnimatePresence>

      <div className="flex justify-center px-4 pt-5">
        <XButton variant="plain" size="sm" className="text-ink-2" onClick={onRestart}>
          换个模板，重新来一遍
        </XButton>
      </div>
    </div>
  );
}
