import { AnimatePresence, motion } from "framer-motion";
import { CalendarClock, Check, ChevronDown, Compass } from "lucide-react";
import { useState } from "react";
import { toast } from "sonner";
import { Group, IconTile, SectionHeader, XButton } from "@/components/app/kit";
import { EmptyState } from "@/components/app/States";
import { cn } from "@/lib/utils";
import type { Insights } from "../logic/aggregator";
import type { Experiment } from "../logic/experiment";
import { generateSuggestions } from "../logic/suggestions";
import { copy } from "../copy";
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
  const [openId, setOpenId] = useState<string | null>(null);
  if (!experiment || experiment.status === "draft") {
    return (
      <EmptyState
        icon={Compass}
        title={copy.decide.emptyTitle}
        hint={copy.decide.emptyHint}
        action={
          <XButton variant="secondary" onClick={onBackToResults}>
            {copy.decide.emptyAction}
          </XButton>
        }
      />
    );
  }
  const suggestions = generateSuggestions(experiment, insights);
  const chosen = suggestions.find((s) => s.id === chosenId) ?? null;

  return (
    <div className="pb-6">
      <SectionHeader>{copy.decide.pick}</SectionHeader>
      <Group>
        <div role="radiogroup" aria-label={copy.decide.pick}>
          {suggestions.map((s) => {
            const selected = s.id === chosenId;
            const open = openId === s.id;
            return (
              <div key={s.id} className={cn("transition-colors duration-150", selected && "bg-clay-soft/40")}>
                <div className="flex items-start gap-1 pl-4 pr-2">
                  <button
                    type="button"
                    aria-expanded={open}
                    onClick={() => setOpenId(open ? null : s.id)}
                    className="min-w-0 flex-1 py-3.5 pr-2 text-left active:opacity-60"
                  >
                    <span className="block text-[16px] font-medium leading-[22px] text-ink [overflow-wrap:anywhere]">{s.title}</span>
                    <span className="mt-0.5 flex items-center gap-1 text-[13px] leading-[18px] text-ink-2">
                      {open ? copy.decide.less : copy.decide.more}
                      <ChevronDown className={cn("size-3.5 transition-transform duration-200 ease-[var(--ease-out)]", open && "rotate-180")} aria-hidden />
                    </span>
                  </button>
                  <button
                    type="button"
                    role="radio"
                    aria-checked={selected}
                    aria-label={s.title}
                    onClick={() => {
                      onChoose(s.id);
                      toast(`已选择：${s.title}`, { description: "两周后复盘一下效果吧" });
                    }}
                    className="grid size-11 shrink-0 place-items-center active:opacity-60"
                  >
                    <span className={cn("grid size-6 place-items-center rounded-full border transition-colors duration-150", selected ? "border-clay bg-clay" : "border-ink-3")}>
                      <AnimatePresence>
                        {selected && (
                          <motion.span initial={{ scale: 0.3, opacity: 0 }} animate={{ scale: 1, opacity: 1 }} exit={{ opacity: 0, transition: { duration: 0.1 } }} transition={springPop}>
                            <Check className="size-3.5 text-on-clay" strokeWidth={3} />
                          </motion.span>
                        )}
                      </AnimatePresence>
                    </span>
                  </button>
                </div>
                <AnimatePresence initial={false}>
                  {open && (
                    <motion.div
                      initial={{ height: 0, opacity: 0 }}
                      animate={{ height: "auto", opacity: 1 }}
                      exit={{ height: 0, opacity: 0 }}
                      transition={spring}
                      className="overflow-hidden"
                    >
                      <div className="space-y-1.5 px-4 pb-4 text-[13px] leading-[18px]">
                        <p className="flex gap-2">
                          <span className="shrink-0 font-medium text-positive">{copy.decide.pros}</span>
                          <span className="text-ink">{s.pros}</span>
                        </p>
                        <p className="flex gap-2">
                          <span className="shrink-0 font-medium text-negative">{copy.decide.cons}</span>
                          <span className="text-ink">{s.cons}</span>
                        </p>
                      </div>
                    </motion.div>
                  )}
                </AnimatePresence>
              </div>
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
            <SectionHeader>{copy.decide.yours}</SectionHeader>
            <Group>
              <div className="px-4 py-3.5">
                <p className="text-[16px] font-medium leading-[22px] text-ink [overflow-wrap:anywhere]">{chosen.title}</p>
                <p className="mt-1 text-[13px] leading-[18px] text-ink-2">
                  <span className="font-medium text-ink">{copy.decide.first}　</span>
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
          {copy.decide.restart}
        </XButton>
      </div>
    </div>
  );
}
