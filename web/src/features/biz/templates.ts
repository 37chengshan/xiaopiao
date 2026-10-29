import type { Experiment, ExperimentOption, ExperimentTemplate, OptionId } from "@/features/merchant/logic/experiment";
import type { BizStringKey } from "./strings";

/**
 * Template questions and option labels live in `features/merchant/logic/experiment.ts` as the
 * android-side source text; the desktop console reads the dictionary instead, so an English
 * session never shows a Chinese template. An unknown template id falls back to its own text.
 */
interface TemplateKeys {
  title: BizStringKey;
  question: BizStringKey;
  label: Record<OptionId, BizStringKey>;
  blurb: Record<OptionId, BizStringKey>;
}

const KEYS: Record<string, TemplateKeys> = {
  "half-vs-topping": {
    title: "template.half-vs-topping.title",
    question: "template.half-vs-topping.question",
    label: {
      A: "template.half-vs-topping.A.label",
      B: "template.half-vs-topping.B.label",
    },
    blurb: {
      A: "template.half-vs-topping.A.blurb",
      B: "template.half-vs-topping.B.blurb",
    },
  },
  "member-vs-weekly": {
    title: "template.member-vs-weekly.title",
    question: "template.member-vs-weekly.question",
    label: {
      A: "template.member-vs-weekly.A.label",
      B: "template.member-vs-weekly.B.label",
    },
    blurb: {
      A: "template.member-vs-weekly.A.blurb",
      B: "template.member-vs-weekly.B.blurb",
    },
  },
  "large-vs-regular": {
    title: "template.large-vs-regular.title",
    question: "template.large-vs-regular.question",
    label: {
      A: "template.large-vs-regular.A.label",
      B: "template.large-vs-regular.B.label",
    },
    blurb: {
      A: "template.large-vs-regular.A.blurb",
      B: "template.large-vs-regular.B.blurb",
    },
  },
};

type Translate = (key: BizStringKey) => string;

function localizeOptions(t: Translate, keys: TemplateKeys, options: readonly ExperimentOption[]): ExperimentOption[] {
  return options.map((option) => ({
    ...option,
    label: t(keys.label[option.id]),
    blurb: t(keys.blurb[option.id]),
  }));
}

/** Localised title, question and options for the template list and the preview card. */
export function templateCopy(t: Translate, template: ExperimentTemplate) {
  const keys = KEYS[template.id];
  if (!keys) return { title: template.title, question: template.question, options: [...template.options] };
  return {
    title: t(keys.title),
    question: t(keys.question),
    options: localizeOptions(t, keys, template.options),
  };
}

/** Localised title and option labels for an experiment that is already running. */
export function experimentCopy(t: Translate, experiment: Experiment) {
  const keys = KEYS[experiment.templateId];
  if (!keys) return { title: experiment.title, options: [...experiment.options] };
  return {
    title: t(keys.title),
    options: localizeOptions(t, keys, experiment.options),
  };
}
