import { useSyncExternalStore } from "react";
import { aggregate, type Insights } from "./logic/aggregator";
import { generateDemoCrowd, type DemoMember } from "./logic/crowd";
import type { OptionId } from "./logic/experiment";
import * as S from "./logic/state";

/**
 * In-memory module store shared by /merchant and /game (no localStorage: lives until page reload).
 * Pure transitions live in logic/state.ts; this file only holds the current value.
 */
let state: S.MerchantState = S.initialState;
const listeners = new Set<() => void>();

function set(next: S.MerchantState) {
  if (next === state) return;
  state = next;
  listeners.forEach((l) => l());
}

let crowd: DemoMember[] | null = null;
let insights: Insights | null = null;

export function getCrowd(): DemoMember[] {
  return (crowd ??= generateDemoCrowd());
}

export function getInsights(): Insights {
  return (insights ??= aggregate(getCrowd()));
}

export function getMerchantState(): S.MerchantState {
  return state;
}

export function useMerchantState(): S.MerchantState {
  return useSyncExternalStore(
    (cb) => {
      listeners.add(cb);
      return () => listeners.delete(cb);
    },
    () => state,
    () => state,
  );
}

export const merchantActions = {
  goToStep: (step: S.StepIndex) => set(S.goToStep(state, step)),
  selectTemplate: (id: string) => set(S.selectTemplate(state, id)),
  publish: () => set(S.publishExperiment(state, getCrowd())),
  recordPlayerChoice: (choice: OptionId) => set(S.recordPlayerChoice(state, choice)),
  finishExperiment: () => set(S.finishExperiment(state)),
  chooseSuggestion: (id: string) => set(S.chooseSuggestion(state, id)),
  startOver: () => set(S.startOver()),
};
