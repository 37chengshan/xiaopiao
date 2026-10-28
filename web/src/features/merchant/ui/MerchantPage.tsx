import { AnimatePresence, LayoutGroup, motion } from "framer-motion";
import { Info } from "lucide-react";
import { useEffect, useState } from "react";
import { Toaster } from "@/components/ui/sonner";
import { cn } from "@/lib/utils";
import type { StepIndex } from "../logic/state";
import { getInsights, merchantActions, useMerchantState } from "../store";
import { spring } from "../tokens";
import { DataDrawer, DemoPill, PhoneFrame } from "./common";
import { DecideStep } from "./DecideStep";
import { InsightsSkeleton, InsightsStep } from "./InsightsStep";
import { LaunchStep } from "./LaunchStep";
import { ResultsStep } from "./ResultsStep";
import { PrimaryButton } from "./common";

const STEPS = [
  { label: "人群洞察", short: "洞察", emoji: "🔍" },
  { label: "发起实验", short: "实验", emoji: "🧪" },
  { label: "看结果", short: "结果", emoji: "📊" },
  { label: "你来决定", short: "决定", emoji: "✋" },
] as const;

function Stepper({
  step,
  reachable,
  onGo,
}: {
  step: StepIndex;
  reachable: (i: number) => boolean;
  onGo: (i: StepIndex) => void;
}) {
  return (
    <nav aria-label="四步流程" className="sticky top-0 z-30 -mx-4 bg-(--mb-canvas)/90 px-4 pb-3 pt-2 backdrop-blur">
      <LayoutGroup id="merchant-stepper">
        <ol className="grid grid-cols-4 gap-1.5">
          {STEPS.map((s, i) => {
            const active = i === step;
            const ok = reachable(i);
            return (
              <li key={s.label}>
                <button
                  type="button"
                  disabled={!ok}
                  aria-current={active ? "step" : undefined}
                  onClick={() => onGo(i as StepIndex)}
                  className={cn(
                    "relative flex w-full flex-col items-center gap-0.5 rounded-xl px-1 py-2 text-[11px] font-semibold outline-offset-2 focus-visible:outline-2 focus-visible:outline-(--mb-ink) disabled:cursor-not-allowed disabled:opacity-40",
                    active ? "text-white" : "text-(--mb-ink-soft)",
                  )}
                >
                  {active ? (
                    <motion.span
                      layoutId="step-pill"
                      transition={spring}
                      className="absolute inset-0 rounded-xl bg-(--mb-coral)"
                    />
                  ) : null}
                  <span aria-hidden className="relative text-[16px] leading-none">
                    {s.emoji}
                  </span>
                  <span className="relative">
                    <span className="mr-0.5 opacity-70">{i + 1}</span>
                    {s.short}
                  </span>
                </button>
              </li>
            );
          })}
        </ol>
      </LayoutGroup>
    </nav>
  );
}

export function MerchantPage() {
  const state = useMerchantState();
  const insights = getInsights();
  const [loading, setLoading] = useState(true);
  const [dir, setDir] = useState(1);

  useEffect(() => {
    const t = window.setTimeout(() => setLoading(false), 450);
    return () => window.clearTimeout(t);
  }, []);

  const hasExp = !!state.experiment && state.experiment.status !== "draft";
  const reachable = (i: number) => i <= 1 || hasExp;
  const go = (i: StepIndex) => {
    setDir(i >= state.step ? 1 : -1);
    merchantActions.goToStep(i);
  };

  return (
    <PhoneFrame>
      <header className="flex items-start justify-between gap-3 pb-2">
        <div className="min-w-0">
          <p className="text-[12px] font-semibold text-(--mb-coral-deep)">小店 · 老板视角</p>
          <h1 className="text-[24px] font-extrabold leading-tight tracking-tight">楼下的芋泥奶茶铺</h1>
        </div>
        <div className="flex shrink-0 flex-col items-end gap-1.5">
          <DemoPill />
          <DataDrawer>
            <button
              type="button"
              className="inline-flex items-center gap-1 rounded-full px-1.5 py-0.5 text-[12px] font-semibold text-(--mb-ink-soft) underline-offset-2 outline-offset-2 hover:underline focus-visible:outline-2 focus-visible:outline-(--mb-ink)"
            >
              <Info className="size-3.5" aria-hidden />
              数据怎么来的
            </button>
          </DataDrawer>
        </div>
      </header>

      <Stepper step={state.step} reachable={reachable} onGo={go} />

      <main className="mt-1">
        <AnimatePresence mode="wait" custom={dir} initial={false}>
          <motion.div
            key={state.step}
            custom={dir}
            variants={{
              enter: (d: number) => ({ opacity: 0, x: 36 * d }),
              center: { opacity: 1, x: 0 },
              exit: (d: number) => ({ opacity: 0, x: -36 * d }),
            }}
            initial="enter"
            animate="center"
            exit="exit"
            transition={spring}
          >
            {state.step === 0 ? (
              loading ? (
                <InsightsSkeleton />
              ) : (
                <div className="space-y-3">
                  <InsightsStep insights={insights} />
                  <PrimaryButton onClick={() => go(1)}>下一步：发起一个小实验</PrimaryButton>
                </div>
              )
            ) : null}
            {state.step === 1 ? (
              <LaunchStep
                templateId={state.templateId}
                experiment={state.experiment}
                onSelect={merchantActions.selectTemplate}
                onPublish={merchantActions.publish}
                onLaunched={() => go(2)}
              />
            ) : null}
            {state.step === 2 ? (
              <ResultsStep
                experiment={state.experiment}
                onFinish={merchantActions.finishExperiment}
                onNext={() => go(3)}
                onBackToLaunch={() => go(1)}
              />
            ) : null}
            {state.step === 3 ? (
              <DecideStep
                experiment={state.experiment}
                insights={insights}
                chosenId={state.chosenSuggestionId}
                onChoose={merchantActions.chooseSuggestion}
                onRestart={() => {
                  merchantActions.startOver();
                  setDir(-1);
                }}
                onBackToResults={() => go(2)}
              />
            ) : null}
          </motion.div>
        </AnimatePresence>
      </main>
      <Toaster position="top-center" />
    </PhoneFrame>
  );
}
