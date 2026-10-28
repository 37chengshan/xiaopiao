import type { Insights } from "./aggregator";
import { computeResults, type Experiment, type ExperimentOption } from "./experiment";

export interface Suggestion {
  id: "follow" | "pilot" | "retest";
  emoji: string;
  title: string;
  summary: string;
  /** honest trade-off, never a revenue promise */
  pros: string;
  cons: string;
  firstStep: string;
  reviewNote: string;
}

export const REVIEW_TEXT = "两周后复盘";

/** Exactly three options. The owner chooses; the app never decides. Deterministic. */
export function generateSuggestions(
  exp: Experiment,
  insights: Insights,
): [Suggestion, Suggestion, Suggestion] {
  const res = computeResults(exp);
  const lead: ExperimentOption | null = res.leader
    ? (exp.options.find((o) => o.id === res.leader) ?? null)
    : null;
  const leadName = lead ? `「${lead.label}」` : "两个都留着";
  const peak = insights.peak?.label ?? "顾客最多的时段";
  const sampleHint = res.isClear
    ? "这次样本够用，倾向比较清楚。"
    : "这次样本或差距有限，当作线索而不是定论。";

  return [
    {
      id: "follow",
      emoji: "🧭",
      title: lead ? `把 ${leadName} 推出来试两周` : "两个玩法都先摆出来",
      summary: lead
        ? `按这次游戏表态，顾客更偏向 ${leadName}。${sampleHint}`
        : `这次两边打平，先把两个都摆上柜台，让顾客自己挑。`,
      pros: "最直接，顾客反馈能很快看到，改回去也容易。",
      cons: "游戏表态不等于真实下单，可能和实际选择有出入；活动成本由你自己承担。",
      firstStep: lead ? `在柜台贴出 ${leadName} 的小告示，先试两周。` : "两个活动各占半个柜台，先试两周。",
      reviewNote: `${REVIEW_TEXT}：看看顾客提到它的次数、复购有没有变化，再决定留不留。`,
    },
    {
      id: "pilot",
      emoji: "⏱️",
      title: `只在「${peak}」小范围试`,
      summary: `演示数据里 ${peak} 来的人最多。只在这个时段试，成本小，也更容易看出差别。`,
      pros: "投入小、可随时叫停；刚好能服务最常来的那批顾客。",
      cons: "覆盖的顾客少，结论要慢一些才能看清；其他时段的顾客可能觉得没赶上。",
      firstStep: `这周先在 ${peak} 试一次，记下大概来了多少人。`,
      reviewNote: `${REVIEW_TEXT}：对比试点时段和平时，看看有没有明显不同。`,
    },
    {
      id: "retest",
      emoji: "🔁",
      title: "先不动，再测一轮",
      summary: res.isClear
        ? "结论已经比较清楚，但如果你想更踏实，换个说法再测一轮也不吃亏。"
        : "样本还不够结实，先邀请顾客再玩一轮，攒够人数再做决定。",
      pros: "零成本、零风险，顺便让更多顾客参与进来。",
      cons: "决定会晚一些；顾客可能玩腻了，参与度会有些下降。",
      firstStep: "把小游戏再发一轮，或换一个新模板测别的问题。",
      reviewNote: `${REVIEW_TEXT}：看看新一轮样本多了多少，倾向有没有变。`,
    },
  ];
}
