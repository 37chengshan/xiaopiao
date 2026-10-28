import { useMemo } from "react";
import { computeResults } from "@/features/merchant/logic/experiment";
import { cn } from "@/lib/utils";
import { insightActions, getCrowdInsights, useInsightState, type StepIndex } from "../store";
import { useBizStrings } from "../strings";
import { DecideBoard } from "./DecideBoard";
import { ExperimentBoard } from "./ExperimentBoard";
import { InsightBoard } from "./InsightBoard";
import { ResultsBoard } from "./ResultsBoard";
import { BizPage } from "./primitives";

const STEPS = ["step.insights", "step.launch", "step.results", "step.decide"] as const;

/** `/app/biz/insights` — 洞察 → 实验 → 结果 → 决定, one step at a time, never jumping ahead. */
export function InsightsPage() {
  const t = useBizStrings();
  const state = useInsightState();
  const insights = getCrowdInsights();
  const game = useMemo(() => {
    if (!state.experiment) return { plays: 0, lead: null, sample: 0 };
    const results = computeResults(state.experiment);
    const lead = results.leader ? state.experiment.options.find((o) => o.id === results.leader) : null;
    return {
      plays: results.playerCount,
      sample: state.experiment.votes.length,
      lead: lead ? `${lead.emoji} ${lead.label}` : null,
    };
  }, [state.experiment]);

  const published = !!state.experiment && state.experiment.status !== "draft";
  const reachable = (step: StepIndex) => step <= 1 || published;

  return (
    <BizPage>
      <div className="grid grid-cols-12 gap-5">
        <nav aria-label={t("insights.crowd")} className="col-span-12 lg:col-span-2">
          <ol className="flex gap-1.5 overflow-x-auto lg:flex-col lg:gap-0.5 lg:overflow-visible">
            {STEPS.map((key, index) => {
              const step = index as StepIndex;
              const active = state.step === step;
              const locked = !reachable(step);
              return (
                <li key={key}>
                  <button
                    type="button"
                    aria-current={active ? "step" : undefined}
                    disabled={locked}
                    onClick={() => insightActions.goToStep(step)}
                    className={cn(
                      "flex w-full items-center gap-2 rounded-lg px-2.5 py-1.5 text-left text-[13px] font-medium transition-colors",
                      active ? "bg-d-fill text-d-ink" : "text-d-ink-2 hover:bg-d-fill/50",
                      locked && "cursor-not-allowed opacity-40 hover:bg-transparent",
                    )}
                  >
                    <span
                      className={cn(
                        "grid size-5 shrink-0 place-items-center rounded-full text-[10px] tabular-nums",
                        active ? "bg-d-ink text-d-canvas" : "bg-d-fill text-d-ink-2",
                      )}
                    >
                      {index + 1}
                    </span>
                    {t(key)}
                  </button>
                </li>
              );
            })}
          </ol>
        </nav>

        <div className="col-span-12 lg:col-span-10">
          {state.step === 0 && (
            <InsightBoard insights={insights} game={game} onNext={() => insightActions.goToStep(1)} />
          )}
          {state.step === 1 && (
            <ExperimentBoard
              templateId={state.templateId}
              experiment={state.experiment}
              onSelect={insightActions.selectTemplate}
              onPublish={insightActions.publish}
              onNext={() => insightActions.goToStep(2)}
            />
          )}
          {state.step === 2 && (
            <ResultsBoard
              experiment={state.experiment}
              onEnd={insightActions.endExperiment}
              onNext={() => insightActions.goToStep(3)}
            />
          )}
          {state.step === 3 && (
            <DecideBoard
              experiment={state.experiment}
              insights={insights}
              chosenId={state.chosenId}
              onChoose={insightActions.choose}
              onRestart={insightActions.restart}
            />
          )}
        </div>
      </div>
    </BizPage>
  );
}
