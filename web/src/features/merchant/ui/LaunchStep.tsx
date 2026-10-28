import { AnimatePresence, motion, useReducedMotion } from "framer-motion";
import { useState } from "react";
import { Rocket } from "lucide-react";
import { cn } from "@/lib/utils";
import { TEMPLATES, STATUS_LABELS, type Experiment, type ExperimentTemplate } from "../logic/experiment";
import { spring, tokens } from "../tokens";
import { Card, PrimaryButton, SectionTitle } from "./common";

function TemplateCard({
  t,
  selected,
  locked,
  onSelect,
}: {
  t: ExperimentTemplate;
  selected: boolean;
  locked: boolean;
  onSelect: () => void;
}) {
  return (
    <motion.button
      type="button"
      role="radio"
      aria-checked={selected}
      disabled={locked}
      onClick={onSelect}
      whileTap={{ scale: 0.98 }}
      animate={{ scale: selected ? 1.015 : 1 }}
      transition={spring}
      className={cn(
        "w-full rounded-2xl border bg-(--mb-card) p-3.5 text-left outline-offset-2 focus-visible:outline-2 focus-visible:outline-(--mb-ink) disabled:cursor-not-allowed disabled:opacity-50",
        selected ? "border-(--mb-coral) shadow-[0_0_0_3px_var(--mb-coral-soft)]" : "border-(--mb-line)",
      )}
    >
      <div className="flex items-center gap-2 text-[22px]" aria-hidden>
        <span>{t.options[0].emoji}</span>
        <span className="text-[11px] font-bold text-(--mb-ink-soft)">VS</span>
        <span>{t.options[1].emoji}</span>
      </div>
      <p className="mt-1.5 text-[15px] font-bold">{t.title}</p>
      <p className="mt-0.5 text-[12px] text-(--mb-ink-soft)">{t.question}</p>
    </motion.button>
  );
}

/** What a customer sees in the game: a faithful mini preview. */
function ConsumerPreview({ t }: { t: ExperimentTemplate }) {
  return (
    <Card aria-label="顾客看到的样子" className="bg-(--mb-canvas)">
      <SectionTitle hint="顾客视角预览">顾客会这样看到</SectionTitle>
      <div className="rounded-2xl bg-(--mb-card) p-3 shadow-sm">
        <p className="text-center text-[13px] font-bold">{t.question}</p>
        <div className="mt-3 grid grid-cols-2 gap-2">
          {t.options.map((o) => (
            <div key={o.id} className="rounded-xl border border-(--mb-line) bg-(--mb-canvas) p-2.5 text-center">
              <div className="text-2xl" aria-hidden>
                {o.emoji}
              </div>
              <p className="mt-1 text-[13px] font-bold">{o.label}</p>
              <p className="mt-0.5 text-[11px] leading-snug text-(--mb-ink-soft)">{o.blurb}</p>
            </div>
          ))}
        </div>
        <p className="mt-2 text-center text-[10px] text-(--mb-ink-soft)">← 左滑选左边 · 右滑选右边 →</p>
      </div>
    </Card>
  );
}

const CONFETTI = [tokens.coral, tokens.butter, tokens.mint, tokens.sky, tokens.grape, tokens.stamp];

function LaunchBurst() {
  return (
    <motion.div
      aria-hidden
      className="pointer-events-none fixed inset-0 z-40 grid place-items-center"
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      exit={{ opacity: 0 }}
    >
      <motion.div
        className="absolute inset-0 bg-(--mb-canvas)/80 backdrop-blur-sm"
        initial={{ opacity: 0 }}
        animate={{ opacity: 1 }}
      />
      {Array.from({ length: 18 }).map((_, i) => {
        const a = (i / 18) * Math.PI * 2;
        return (
          <motion.span
            key={i}
            className="absolute size-2.5 rounded-full"
            style={{ background: CONFETTI[i % CONFETTI.length] }}
            initial={{ x: 0, y: 0, scale: 0 }}
            animate={{ x: Math.cos(a) * 130, y: Math.sin(a) * 130, scale: [0, 1.4, 0.8], opacity: [1, 1, 0] }}
            transition={{ duration: 1, delay: 0.15, ease: "easeOut" }}
          />
        );
      })}
      <motion.div
        className="relative grid size-24 place-items-center rounded-full bg-(--mb-coral) text-5xl shadow-lg"
        initial={{ scale: 0, rotate: -30 }}
        animate={{ scale: [0, 1.25, 1], rotate: [-30, 8, 0] }}
        transition={{ type: "spring", stiffness: 300, damping: 14 }}
      >
        🚀
      </motion.div>
      <motion.p
        className="absolute top-[58%] text-[17px] font-extrabold"
        initial={{ opacity: 0, y: 10 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ delay: 0.3 }}
      >
        实验上线啦！
      </motion.p>
    </motion.div>
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
  const [bursting, setBursting] = useState(false);
  const reduce = useReducedMotion();
  const template = TEMPLATES.find((t) => t.id === templateId) ?? null;
  const live = experiment && experiment.status !== "draft";

  const launch = () => {
    onPublish();
    if (reduce) return onLaunched();
    setBursting(true);
    window.setTimeout(() => {
      setBursting(false);
      onLaunched();
    }, 1500);
  };

  return (
    <div className="space-y-3">
      <div role="radiogroup" aria-label="选择实验模板" className="space-y-2.5">
        <SectionTitle hint="选一个想问顾客的问题">实验模板</SectionTitle>
        {TEMPLATES.map((t) => (
          <TemplateCard key={t.id} t={t} selected={t.id === templateId} locked={!!live} onSelect={() => onSelect(t.id)} />
        ))}
      </div>

      <AnimatePresence mode="wait">
        {template ? (
          <motion.div
            key={template.id}
            initial={{ opacity: 0, y: 12 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -8 }}
            transition={spring}
          >
            <ConsumerPreview t={template} />
          </motion.div>
        ) : (
          <motion.p
            key="hint"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            className="rounded-2xl border border-dashed border-(--mb-line) px-4 py-6 text-center text-[12px] text-(--mb-ink-soft)"
          >
            先选一个模板，这里会显示顾客看到的样子
          </motion.p>
        )}
      </AnimatePresence>

      {experiment ? (
        <p className="px-1 text-center text-[12px] text-(--mb-ink-soft)">
          当前状态：<b className="text-(--mb-ink)">{STATUS_LABELS[experiment.status]}</b>
          {live ? " · 已发布，模板已锁定" : "（草稿，还没发给顾客）"}
        </p>
      ) : null}

      {live ? (
        <PrimaryButton onClick={onLaunched}>去看结果</PrimaryButton>
      ) : (
        <PrimaryButton disabled={!template} onClick={launch}>
          <Rocket className="size-4" aria-hidden /> 发布给顾客玩
        </PrimaryButton>
      )}
      <AnimatePresence>{bursting ? <LaunchBurst /> : null}</AnimatePresence>
    </div>
  );
}
