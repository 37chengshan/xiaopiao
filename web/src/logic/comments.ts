import { type Txn, yuan } from "../domain/types";
import { hashString } from "./time";

/** Template-based AI voice. Deterministic per date key so the same day always reads the same. */

const ZERO = [
  "今天钱包睡了个好觉，小票都没得打，纸都省了～",
  "零消费达成！你的钱包今天在度假 🏝️",
  "今日无消费，值得一个小红花 🌸",
  "钱包：我今天什么都没做。你：对，就是这样，真棒。",
];
const LOW = [
  "今天花得很克制，钱包轻轻松了口气。",
  "小小一笔，刚刚好～ 继续保持这个节奏。",
  "轻盈的一天，钱包给你比个心 💛",
  "花得不多，但每一笔都有它的意义。",
];
const MID = [
  "不多不少，是很正常的一天。",
  "吃好喝好，生活的味道都在这张小票里了。",
  "今天的小票长度刚刚好，适合挂冰箱。",
  "平稳的一天，没有惊喜也没有惊吓～",
];
const HIGH = [
  "今天的小票有点长，钱包说它需要一个抱抱 🫂",
  "花得很尽兴！明天稍微温柔一点就好，不着急。",
  "大场面的一天，小票都快拖到地上了。",
  "今天是个大日子吧？开心就好，不评判。",
];
const DRINKY = [
  "今天奶茶/咖啡出镜率很高，续命成功 ☕",
  "杯子比饭还多，快乐是真的，糖分也是真的。",
];

export function dailyComment(items: readonly Txn[], dayKey: string): string {
  const out = items.filter((t) => t.direction === "OUT");
  const total = out.reduce((s, t) => s + t.amountCents, 0);
  const seed = hashString(`day:${dayKey}`);
  if (total === 0) return ZERO[seed % ZERO.length];
  if (out.filter((t) => t.category === "DRINK").length >= 2) return DRINKY[seed % DRINKY.length];
  const pool = total < 3000 ? LOW : total < 12000 ? MID : HIGH;
  return pool[seed % pool.length];
}

const WEEK_UP = ["这周比上周多花了一点点，没关系，下周再轻装上阵。", "支出小幅上扬，偶尔放飞也是生活。"];
const WEEK_DOWN = ["这周比上周省了不少，钱包偷偷笑了 😄", "支出下降，你悄悄变成了会过日子的人。"];
const WEEK_FLAT = ["这周和上周差不多，稳稳的幸福。", "节奏稳定，像一首循环的好歌。"];
const WEEK_FIRST = ["这是你的第一份周报，欢迎来到小票星球～"];

export function weeklyComment(totalCents: number, prevCents: number, weekKey: string): string {
  const seed = hashString(`week:${weekKey}`);
  if (prevCents <= 0) return WEEK_FIRST[seed % WEEK_FIRST.length];
  const ratio = totalCents / prevCents;
  const pool = ratio > 1.1 ? WEEK_UP : ratio < 0.9 ? WEEK_DOWN : WEEK_FLAT;
  return pool[seed % pool.length];
}

export function greeting(now: number): string {
  const h = new Date(now).getHours();
  if (h < 5) return "夜深了，还没睡呀？";
  if (h < 11) return "早上好呀！";
  if (h < 14) return "中午好～ 吃饭了吗？";
  if (h < 18) return "下午好呀！";
  if (h < 23) return "晚上好～";
  return "夜深了，早点休息哦。";
}

export function todaySpendLine(totalCents: number, count: number): string {
  if (count === 0) return "今天到现在还没有花钱，钱包乖乖的 🎉";
  return `今天到现在花了 ¥${yuan(totalCents)}，一共 ${count} 笔。`;
}
