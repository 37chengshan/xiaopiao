import { AnimatePresence, motion, useReducedMotion } from "framer-motion";
import { Check } from "lucide-react";
import { useState } from "react";
import { toast } from "sonner";
import { Group, SectionHeader, XButton } from "@/components/app/kit";
import { QuestionFace } from "@/features/game/ui/QuestionCard";
import { cn } from "@/lib/utils";
import { STATUS_LABELS, TEMPLATES, type Experiment, type ExperimentTemplate } from "../logic/experiment";
import { spring, springPop } from "../motion";

function TemplateRow({ t, selected, locked, onSelect }: { t: ExperimentTemplate; selected: boolean; locked: boolean; onSelect: () => void }) {
  return (
    <button
      type="button"
      role="radio"
      aria-checked={selected}
      disabled={locked}
      onClick={onSelect}
      className={cn(
        "flex min-h-[60px] w-full items-center gap-3 px-4 py-3 text-left transition-colors duration-100 active:bg-surface-2 disabled:cursor-not-allowed",
        locked && !selected && "opacity-50",
      )}
    >
      <span className="min-w-0 flex-1">
        <span className="block text-[16px] font-medium leading-[22px] text-ink">{t.title}</span>
        <span className="block text-[13px] leading-[18px] text-ink-2">{t.question}</span>
      </span>
      <span className="grid size-6 shrink-0 place-items-center" aria-hidden>
        <AnimatePresence>
          {selected && (
            <motion.span initial={{ scale: 0.4, opacity: 0 }} animate={{ scale: 1, opacity: 1 }} exit={{ opacity: 0, transition: { duration: 0.1 } }} transition={springPop}>
              <Check className="size-[22px] text-clay" strokeWidth={2.25} />
            </motion.span>
          )}
        </AnimatePresence>
      </span>
    </button>
  );
}

export function LaunchStep({
  templateId,
  experiment,
  onSelect,
  onPublish,
  onLaunched,
}: {
  templateId: string | null;
  experiment: Experiment | null;
  onSelect: (id: string) => void;
  onPublish: () => void;
  onLaunched: () => void;
}) {
  const [launching, setLaunching] = useState(false);
  const reduce = useReducedMotion();
  const template = TEMPLATES.find((t) => t.id === templateId) ?? null;
  const live = !!experiment && experiment.status !== "draft";

  const launch = () => {
    if (!template) return;
    onPublish();
    setLaunching(true);
    toast("实验上线啦", { description: "顾客玩一轮，结果就会出现在这里" });
    window.setTimeout(onLaunched, reduce ? 400 : 900);
  };

  return (
    <div className="pb-6">
      <SectionHeader>选一个想问顾客的问题</SectionHeader>
      <Group>
        <div role="radiogroup" aria-label="选择实验模板">
          {TEMPLATES.map((t) => (
            <TemplateRow key={t.id} t={t} selected={t.id === templateId} locked={live} onSelect={() => onSelect(t.id)} />
          ))}
        </div>
      </Group>

      <SectionHeader>顾客会这样看到</SectionHeader>
      <div className="px-4" aria-label="顾客看到的样子">
        <AnimatePresence mode="popLayout" initial={false}>
          {template ? (
            <motion.div
              key={template.id}
              initial={{ opacity: 0, y: 8 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, transition: { duration: 0.12 } }}
              transition={spring}
              className="h-[236px]"
            >
              <QuestionFace compact kicker="小店想问你" question={template.question} options={template.options} />
            </motion.div>
          ) : (
            <motion.div key="hint" initial={{ opacity: 0 }} animate={{ opacity: 1 }} className="grid h-[236px] place-items-center rounded-[24px] border border-dashed border-ink-3/50 px-8 text-center text-[13px] text-ink-2">
              先选一个模板，这里会显示顾客看到的样子
            </motion.div>
          )}
        </AnimatePresence>
      </div>

      {experiment && (
        <p className="px-4 pt-3 text-center text-[13px] text-ink-2">
          当前状态：<span className="font-medium text-ink">{STATUS_LABELS[experiment.status]}</span>
          {live ? " · 已发布，模板已锁定" : " · 还没发给顾客"}
        </p>
      )}

      <div className="px-4 pt-5">
        {live && !launching ? (
          <XButton variant="primary" className="w-full" onClick={onLaunched}>
            去看结果
          </XButton>
        ) : (
          <XButton variant="clay" className="w-full" disabled={!template} onClick={launching ? undefined : launch}>
            {launching ? (
              <>
                <AnimatedCheck />
                已发布
              </>
            ) : (
              "发布给顾客玩"
            )}
          </XButton>
        )}
      </div>
    </div>
  );
}

/** A checkmark that draws itself. Calm: one stroke, 280ms. */
function AnimatedCheck() {
  return (
    <svg viewBox="0 0 24 24" className="size-5" fill="none" stroke="currentColor" strokeWidth={2.5} strokeLinecap="round" strokeLinejoin="round" aria-hidden>
      <motion.path d="M5 12.5l4.5 4.5L19 7.5" initial={{ pathLength: 0 }} animate={{ pathLength: 1 }} transition={{ duration: 0.28, ease: [0.23, 1, 0.32, 1] }} />
    </svg>
  );
}
