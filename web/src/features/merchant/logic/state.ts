import type { DemoMember } from "./crowd";
import {
  addVotes,
  createDraft,
  endExperiment,
  findTemplate,
  publish,
  type Experiment,
  type OptionId,
} from "./experiment";

export type StepIndex = 0 | 1 | 2 | 3;

export interface MerchantState {
  step: StepIndex;
  templateId: string | null;
  experiment: Experiment | null;
  chosenSuggestionId: string | null;
  /** number of game plays recorded (each stands in for one customer) */
  players: number;
}

export const initialState: MerchantState = {
  step: 0,
  templateId: null,
  experiment: null,
  chosenSuggestionId: null,
  players: 0,
};

export function goToStep(s: MerchantState, step: StepIndex): MerchantState {
  return s.step === step ? s : { ...s, step };
}

/** Picking a template creates a fresh draft (only before an experiment is live). */
export function selectTemplate(s: MerchantState, templateId: string): MerchantState {
  const template = findTemplate(templateId);
  if (!template || s.experiment?.status === "running") return s;
  return {
    ...s,
    templateId,
    experiment: createDraft(template, `exp-${templateId}`),
    chosenSuggestionId: null,
  };
}

export function publishExperiment(s: MerchantState, crowd: readonly DemoMember[]): MerchantState {
  if (!s.experiment || s.experiment.status !== "draft" || !s.templateId) return s;
  const template = findTemplate(s.templateId);
  if (!template) return s;
  return { ...s, experiment: publish(s.experiment, template, crowd) };
}

/** A game play stands in for one customer's 游戏表态; only a running experiment counts it. */
export function recordPlayerChoice(s: MerchantState, choice: OptionId): MerchantState {
  if (!s.experiment || s.experiment.status !== "running") return s;
  const players = s.players + 1;
  return {
    ...s,
    players,
    experiment: addVotes(s.experiment, [
      { voterId: `player-${players}`, choice, source: "player" },
    ]),
  };
}

export function finishExperiment(s: MerchantState): MerchantState {
  return s.experiment ? { ...s, experiment: endExperiment(s.experiment) } : s;
}

export function chooseSuggestion(s: MerchantState, id: string): MerchantState {
  return { ...s, chosenSuggestionId: id };
}

export function startOver(): MerchantState {
  return initialState;
}
