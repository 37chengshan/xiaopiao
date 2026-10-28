import { motion, useReducedMotion } from "framer-motion";
import { useEffect, useRef, useState } from "react";
import { DUR, EASE_OUT } from "@/lib/motion";
import type { StepIndex } from "../logic/state";
import { getInsights, merchantActions, useMerchantState } from "../store";
import { DecideStep } from "./DecideStep";
import { InsightsSkeleton, InsightsStep } from "./InsightsStep";
import { LaunchStep } from "./LaunchStep";
import { ResultsStep } from "./ResultsStep";
import { Stepper } from "./Stepper";
import { TopBar } from "./TopBar";

export function MerchantPage() {
  const state = useMerchantState();
  const insights = getInsights();
  const reduce = useReducedMotion() ?? false;
  const [loading, setLoading] = useState(true);
  const topRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const t = window.setTimeout(() => setLoading(false), 450);
    return () => window.clearTimeout(t);
  }, []);

  // Each step starts at the top of the scroll column.
  useEffect(() => {
    topRef.current?.closest("main")?.scrollTo({ top: 0 });
  }, [state.step]);

  const hasExp = !!state.experiment && state.experiment.status !== "draft";
  const reachable = (i: number) => i <= 1 || hasExp;
  const go = (i: StepIndex) => merchantActions.goToStep(i);

  return (
    <div ref={topRef}>
      <TopBar />
      <Stepper step={state.step} reachable={reachable} onGo={go} />

      <motion.div
        key={state.step}
        initial={reduce ? false : { opacity: 0 }}
        animate={{ opacity: 1 }}
        transition={{ duration: DUR.fade, ease: EASE_OUT }}
      >
        {state.step === 0 &&
          (loading ? <InsightsSkeleton /> : <InsightsStep insights={insights} onNext={() => go(1)} />)}
        {state.step === 1 && (
          <LaunchStep
            templateId={state.templateId}
            experiment={state.experiment}
            onSelect={merchantActions.selectTemplate}
            onPublish={merchantActions.publish}
            onLaunched={() => go(2)}
          />
        )}
        {state.step === 2 && (
          <ResultsStep
            experiment={state.experiment}
            onFinish={merchantActions.finishExperiment}
            onNext={() => go(3)}
            onBackToLaunch={() => go(1)}
          />
        )}
        {state.step === 3 && (
          <DecideStep
            experiment={state.experiment}
            insights={insights}
            chosenId={state.chosenSuggestionId}
            onChoose={merchantActions.chooseSuggestion}
            onRestart={merchantActions.startOver}
            onBackToResults={() => go(2)}
          />
        )}
      </motion.div>
    </div>
  );
}
