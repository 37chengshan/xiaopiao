import type { DemoMember } from "./crowd";
import { mulberry32, shuffled } from "./rng";

export type ExperimentStatus = "draft" | "running" | "ended";
export const STATUS_LABELS: Record<ExperimentStatus, string> = {
  draft: "草稿",
  running: "进行中",
  ended: "已结束",
};

export type OptionId = "A" | "B";
export type PersonaTag = "topping" | "deal" | "novel" | "classic";

export interface ExperimentOption {
  id: OptionId;
  emoji: string;
  label: string;
  blurb: string;
  tag: PersonaTag;
}

export interface ExperimentTemplate {
  id: string;
  title: string;
  question: string;
  options: readonly [ExperimentOption, ExperimentOption];
  /** demo respondents that "already answered" once the experiment goes live */
  demoSampleSize: number;
  /** demo probability of choosing A */
  demoShareA: number;
}

export const TEMPLATES: readonly ExperimentTemplate[] = [
  {
    id: "half-vs-topping",
    title: "第二杯半价 vs 免费加小料",
    question: "同样一杯，你更想要哪个小福利？",
    options: [
      { id: "A", emoji: "🥤", label: "第二杯半价", blurb: "两杯一起买，第二杯半价", tag: "deal" },
      { id: "B", emoji: "🍡", label: "免费加小料", blurb: "这杯免费多加一份小料", tag: "topping" },
    ],
    demoSampleSize: 63,
    demoShareA: 0.36,
  },
  {
    id: "member-vs-weekly",
    title: "会员积分 vs 每周一杯折扣",
    question: "常来的话，你更喜欢哪种回馈？",
    options: [
      { id: "A", emoji: "⭐", label: "会员积分", blurb: "每杯攒积分，攒够换一杯", tag: "classic" },
      { id: "B", emoji: "🎫", label: "每周一杯折扣", blurb: "每周固定一杯享折扣", tag: "deal" },
    ],
    demoSampleSize: 44,
    demoShareA: 0.47,
  },
  {
    id: "large-vs-regular",
    title: "大杯加 2 元 vs 中杯原价",
    question: "同一款饮品，你会怎么选？",
    options: [
      { id: "A", emoji: "🥛", label: "大杯 +2 元", blurb: "多喝一些，加 2 元", tag: "novel" },
      { id: "B", emoji: "🧋", label: "中杯原价", blurb: "刚刚好，不加价", tag: "classic" },
    ],
    demoSampleSize: 24,
    demoShareA: 0.58,
  },
];

export function findTemplate(id: string): ExperimentTemplate | undefined {
  return TEMPLATES.find((t) => t.id === id);
}

export type VoteSource = "demo" | "player";

/** A single 游戏表态 (game stance). Kept separate from real transactions. */
export interface Vote {
  voterId: string;
  choice: OptionId;
  source: VoteSource;
}

export interface Experiment {
  id: string;
  templateId: string;
  title: string;
  question: string;
  options: readonly [ExperimentOption, ExperimentOption];
  status: ExperimentStatus;
  votes: readonly Vote[];
}

export function createDraft(template: ExperimentTemplate, id: string): Experiment {
  return {
    id,
    templateId: template.id,
    title: template.title,
    question: template.question,
    options: template.options,
    status: "draft",
    votes: [],
  };
}

function hash(text: string): number {
  let h = 2166136261;
  for (let i = 0; i < text.length; i++) h = Math.imul(h ^ text.charCodeAt(i), 16777619);
  return h >>> 0;
}

/** Deterministic demo respondents for a template, drawn from the demo crowd. */
export function demoVotes(template: ExperimentTemplate, crowd: readonly DemoMember[]): Vote[] {
  const rng = mulberry32(hash(template.id));
  const n = Math.min(template.demoSampleSize, crowd.length);
  return shuffled(rng, crowd)
    .slice(0, n)
    .map((m) => ({
      voterId: `demo-${m.id}`,
      choice: rng() < template.demoShareA ? "A" : "B",
      source: "demo" as const,
    }));
}

/** draft → running. Seeds the demo respondents. No-op unless draft. */
export function publish(exp: Experiment, template: ExperimentTemplate, crowd: readonly DemoMember[]): Experiment {
  if (exp.status !== "draft") return exp;
  return { ...exp, status: "running", votes: demoVotes(template, crowd) };
}

/** running → ended. */
export function endExperiment(exp: Experiment): Experiment {
  return exp.status === "running" ? { ...exp, status: "ended" } : exp;
}

/** Only a running experiment accepts votes; one vote per voter id. */
export function addVotes(exp: Experiment, votes: readonly Vote[]): Experiment {
  if (exp.status !== "running") return exp;
  const seen = new Set(exp.votes.map((v) => v.voterId));
  const fresh = votes.filter((v) => !seen.has(v.voterId));
  return fresh.length === 0 ? exp : { ...exp, votes: [...exp.votes, ...fresh] };
}

const Z95 = 1.96;

/** Wilson score interval (95%) for a binomial proportion. */
export function wilson(successes: number, n: number, z: number = Z95): [number, number] {
  if (n <= 0) return [0, 1];
  const p = successes / n;
  const denom = 1 + (z * z) / n;
  const center = (p + (z * z) / (2 * n)) / denom;
  const half = (z * Math.sqrt((p * (1 - p)) / n + (z * z) / (4 * n * n))) / denom;
  return [Math.max(0, center - half), Math.min(1, center + half)];
}

export const MIN_STABLE_SAMPLE = 30;

export interface OptionResult {
  optionId: OptionId;
  count: number;
  share: number;
  ci: [number, number];
}

export interface ExperimentResults {
  sampleSize: number;
  demoCount: number;
  playerCount: number;
  options: [OptionResult, OptionResult];
  leader: OptionId | null;
  /** true when sample is big enough and the leader's CI stays above 50% */
  isClear: boolean;
  note: string;
}

export function computeResults(exp: Experiment): ExperimentResults {
  const n = exp.votes.length;
  const build = (id: OptionId): OptionResult => {
    const count = exp.votes.filter((v) => v.choice === id).length;
    return { optionId: id, count, share: n === 0 ? 0 : count / n, ci: wilson(count, n) };
  };
  const options: [OptionResult, OptionResult] = [build("A"), build("B")];
  const leaderRes = options[0].count === options[1].count ? null : options[0].count > options[1].count ? options[0] : options[1];
  const isClear = n >= MIN_STABLE_SAMPLE && leaderRes !== null && leaderRes.ci[0] > 0.5;
  let note: string;
  if (n < MIN_STABLE_SAMPLE) note = `样本 ${n} 人，样本偏少，仅供参考`;
  else if (isClear) note = `样本 ${n} 人，结论较稳定`;
  else note = `样本 ${n} 人，两边差距不大，难分高下`;
  return {
    sampleSize: n,
    demoCount: exp.votes.filter((v) => v.source === "demo").length,
    playerCount: exp.votes.filter((v) => v.source === "player").length,
    options,
    leader: leaderRes?.optionId ?? null,
    isClear,
    note,
  };
}
