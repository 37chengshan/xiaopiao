import type { Experiment, ExperimentOption, OptionId, PersonaTag } from "../../merchant/logic/experiment";
import { mulberry32, shuffled } from "../../merchant/logic/rng";

export interface GameRound {
  id: string;
  kind: "experiment" | "filler";
  question: string;
  /** left card = options[0], right card = options[1] */
  options: readonly [ExperimentOption, ExperimentOption];
}

export interface GameAnswer {
  roundId: string;
  kind: GameRound["kind"];
  choice: OptionId;
  tag: PersonaTag;
}

const fill = (
  id: string,
  question: string,
  a: [string, string, PersonaTag],
  b: [string, string, PersonaTag],
): GameRound => ({
  id,
  kind: "filler",
  question,
  options: [
    { id: "A", emoji: a[0], label: a[1], blurb: "", tag: a[2] },
    { id: "B", emoji: b[0], label: b[1], blurb: "", tag: b[2] },
  ],
});

/** Fun filler rounds: they shape the badge only and never reach the merchant's experiment. */
export const FILLERS: readonly GameRound[] = [
  fill("f-sweet", "今天的心情是？", ["🍰", "甜度拉满", "novel"], ["🍵", "清清爽爽", "classic"]),
  fill("f-ice", "冰块怎么放？", ["🧊", "满杯冰", "novel"], ["☕", "少冰温柔", "classic"]),
  fill("f-topping", "加料你站哪边？", ["🍡", "多加一份料", "topping"], ["🥤", "纯饮不加料", "classic"]),
  fill("f-coupon", "路过看到优惠券？", ["🎟️", "先领了再说", "deal"], ["🚶", "不凑热闹", "classic"]),
  fill("f-new", "菜单上新品？", ["🆕", "先尝为快", "novel"], ["❤️", "老味道最稳", "classic"]),
  fill("f-combo", "套餐 vs 单点？", ["🍱", "凑套餐更划算", "deal"], ["🍜", "想吃啥点啥", "topping"]),
  fill("f-queue", "排队有点长？", ["⏳", "等，值得", "novel"], ["🏃", "换一家", "deal"]),
];

export const MAX_ROUNDS = 8;
export const MIN_ROUNDS = 5;

/**
 * 5–8 rounds: the running experiment's question (if any) goes in at a fixed slot among shuffled fillers.
 * Deterministic for a given seed so tests and demos are stable.
 */
export function buildRounds(experiment: Experiment | null, seed = 7, total = 6): GameRound[] {
  const n = Math.min(MAX_ROUNDS, Math.max(MIN_ROUNDS, total));
  const rng = mulberry32(seed);
  const live = experiment?.status === "running" ? experiment : null;
  const fillers = shuffled(rng, FILLERS).slice(0, live ? n - 1 : n);
  if (!live) return fillers;
  const round: GameRound = {
    id: `exp-${live.id}`,
    kind: "experiment",
    question: live.question,
    options: live.options,
  };
  const slot = Math.min(2, fillers.length);
  return [...fillers.slice(0, slot), round, ...fillers.slice(slot)];
}

export interface Badge {
  tag: PersonaTag;
  title: string;
  emoji: string;
  blurb: string;
}

export const BADGES: Record<PersonaTag, Badge> = {
  topping: { tag: "topping", title: "小料党", emoji: "🍡", blurb: "杯子里不满一点就不踏实，料越多越开心。" },
  deal: { tag: "deal", title: "划算雷达", emoji: "🎟️", blurb: "一眼看出哪个更划算，精打细算的快乐你懂。" },
  novel: { tag: "novel", title: "尝鲜先锋", emoji: "🚀", blurb: "新品、大杯、满杯冰，总想多试一点。" },
  classic: { tag: "classic", title: "老味道守护者", emoji: "🧋", blurb: "知道自己爱什么，稳稳当当最舒服。" },
};

const TAG_ORDER: readonly PersonaTag[] = ["topping", "deal", "novel", "classic"];

export function tagOf(round: GameRound, choice: OptionId): PersonaTag {
  return round.options.find((o) => o.id === choice)?.tag ?? "classic";
}

/** Most frequent tag wins; ties break by TAG_ORDER. No answers → classic. */
export function pickBadge(answers: readonly GameAnswer[]): Badge {
  if (answers.length === 0) return BADGES.classic;
  const counts = new Map<PersonaTag, number>();
  for (const a of answers) counts.set(a.tag, (counts.get(a.tag) ?? 0) + 1);
  let best: PersonaTag = "classic";
  let bestN = -1;
  for (const t of TAG_ORDER) {
    const c = counts.get(t) ?? 0;
    if (c > bestN) {
      best = t;
      bestN = c;
    }
  }
  return BADGES[best];
}
