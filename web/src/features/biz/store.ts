import { useSyncExternalStore } from "react";
import { normalize, remove, upsert, type RevenueEntry } from "@/logic/biz/book";
import { startOfDay } from "@/logic/biz/format";
import { applyPreview, type ImportPreview } from "@/logic/biz/import";
import { seededBook } from "@/logic/biz/seed";
import { aggregate, type Insights } from "@/features/merchant/logic/aggregator";
import { generateDemoCrowd, type DemoMember } from "@/features/merchant/logic/crowd";
import {
  createDraft,
  endExperiment,
  findTemplate,
  publish,
  type Experiment,
} from "@/features/merchant/logic/experiment";

/**
 * In-memory shop store shared by every `/app/biz/*` page (nothing is written to localStorage:
 * the cloud account arrives later, so a reload starts from the seeded book again).
 * Pure transitions live in `logic/biz/*`; this file only holds the current value and notifies.
 */

export interface BizBook {
  /** day-start of "today", fixed at first load so a page never disagrees with itself */
  today: number;
  book: RevenueEntry[];
  /** the day the daily report is looking at */
  reportDay: number;
  /** when the book last changed, for the "更新于 HH:mm" line */
  updatedAt: number;
}

const TODAY = startOfDay(Date.now());

let bookState: BizBook = {
  today: TODAY,
  book: seededBook(TODAY),
  reportDay: TODAY,
  updatedAt: Date.now(),
};
const bookListeners = new Set<() => void>();

function setBook(next: BizBook) {
  if (next === bookState) return;
  bookState = { ...next, updatedAt: Date.now() };
  bookListeners.forEach((l) => l());
}

export function useBizBook(): BizBook {
  return useSyncExternalStore(
    (cb) => {
      bookListeners.add(cb);
      return () => bookListeners.delete(cb);
    },
    () => bookState,
    () => bookState,
  );
}

export function getBook(): BizBook {
  return bookState;
}

export const bookActions = {
  save: (entry: RevenueEntry) =>
    setBook({ ...bookState, book: upsert(bookState.book, { ...entry, day: startOfDay(entry.day) }) }),
  deleteDays: (days: readonly number[]) => {
    const drop = new Set(days);
    setBook({ ...bookState, book: normalize(bookState.book.filter((e) => !drop.has(e.day))) });
  },
  restore: (entries: readonly RevenueEntry[]) =>
    setBook({ ...bookState, book: normalize([...bookState.book, ...entries]) }),
  applyImport: (preview: ImportPreview) => {
    const next = applyPreview(bookState.book, preview);
    setBook({ ...bookState, book: next });
    return next;
  },
  setReportDay: (day: number) => setBook({ ...bookState, reportDay: startOfDay(day) }),
};

// ---- customer insights wizard ---------------------------------------------------------------

export type StepIndex = 0 | 1 | 2 | 3;

export interface InsightState {
  step: StepIndex;
  templateId: string | null;
  experiment: Experiment | null;
  chosenId: string | null;
}

let insightState: InsightState = { step: 0, templateId: null, experiment: null, chosenId: null };
const insightListeners = new Set<() => void>();

function setInsights(next: InsightState) {
  if (next === insightState) return;
  insightState = next;
  insightListeners.forEach((l) => l());
}

export function useInsightState(): InsightState {
  return useSyncExternalStore(
    (cb) => {
      insightListeners.add(cb);
      return () => insightListeners.delete(cb);
    },
    () => insightState,
    () => insightState,
  );
}

let crowd: DemoMember[] | null = null;
let crowdInsights: Insights | null = null;

export function getCrowd(): DemoMember[] {
  return (crowd ??= generateDemoCrowd());
}

export function getCrowdInsights(): Insights {
  return (crowdInsights ??= aggregate(getCrowd()));
}

export const insightActions = {
  goToStep: (step: StepIndex) => setInsights({ ...insightState, step }),
  selectTemplate: (id: string) => {
    const template = findTemplate(id);
    if (!template || insightState.experiment?.status === "running") return;
    setInsights({ ...insightState, templateId: id, experiment: createDraft(template, `exp-${id}`), chosenId: null });
  },
  publish: () => {
    const template = insightState.templateId ? findTemplate(insightState.templateId) : undefined;
    if (!insightState.experiment || !template) return;
    setInsights({ ...insightState, experiment: publish(insightState.experiment, template, getCrowd()) });
  },
  endExperiment: () => {
    if (!insightState.experiment) return;
    setInsights({ ...insightState, experiment: endExperiment(insightState.experiment) });
  },
  choose: (id: string) => setInsights({ ...insightState, chosenId: id }),
  restart: () => setInsights({ step: 0, templateId: null, experiment: null, chosenId: null }),
};
