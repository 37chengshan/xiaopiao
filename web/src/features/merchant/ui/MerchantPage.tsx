import { motion } from "framer-motion";
import { useEffect, useRef, useState } from "react";
import { PageHeader } from "@/components/app/PageHeader";
import { XButton } from "@/components/app/kit";
import type { StepIndex } from "../logic/state";
import { getInsights, merchantActions, useMerchantState } from "../store";
import { DataSheet } from "./DataSheet";
import { DecideStep } from "./DecideStep";
import { InsightsSkeleton, InsightsStep } from "./InsightsStep";
import { LaunchStep } from "./LaunchStep";
import { ResultsStep } from "./ResultsStep";
import { Stepper } from "./Stepper";

export function MerchantPage() {
  const state = useMerchantState();
  const insights = getInsights();
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
      <PageHeader
        title="楼下的芋泥奶茶铺"
        subtitle="小店视角"
        right={
          <DataSheet>
            <XButton variant="plain" size="sm">
              数据怎么来的
            </XButton>
          </DataSheet>
        }
      />
      <Stepper step={state.step} reachable={reachable} onGo={go} />

      <motion.div
        key={state.step}
        initial={{ opacity: 0, y: 6 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.18, ease: [0.23, 1, 0.32, 1] }}
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
