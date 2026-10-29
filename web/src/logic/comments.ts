import { type Txn, yuan } from "../domain/types";
import { hashString } from "./time";
import { getLocale } from "@/lib/strings";

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

/* English voice: same length as the Chinese pools so the same date always picks the same line. */
const ZERO_EN = [
  "Your wallet slept well today — no receipt to print, and the paper is spared.",
  "Zero-spend day. Your wallet is on holiday 🏝️",
  "Nothing spent today. Worth a small gold star 🌸",
  "Wallet: I did nothing today. You: yes, and it was great.",
];
const LOW_EN = [
  "A restrained day. Your wallet exhaled.",
  "One small entry, exactly enough. Keep this rhythm.",
  "A light day — the wallet sends you a heart 💛",
  "Not much spent, and every line still meant something.",
];
const MID_EN = [
  "Nothing strange, nothing extreme — a normal day.",
  "Good food, good coffee; the day fits on one receipt.",
  "A receipt of just the right length for the fridge door.",
  "Steady today. No surprises, in either direction.",
];
const HIGH_EN = [
  "A long receipt today. The wallet would like a hug 🫂",
  "A big one! Be a little gentler tomorrow — no rush.",
  "Quite a day; the paper nearly reached the floor.",
  "Looks like a special day. Glad you enjoyed it — no judgement.",
];
const DRINKY_EN = [
  "Lots of cups today. Survival achieved ☕",
  "More cups than meals — the joy is real, and so is the sugar.",
];

export function dailyComment(items: readonly Txn[], dayKey: string): string {
  const out = items.filter((t) => t.direction === "OUT");
  const total = out.reduce((s, t) => s + t.amountCents, 0);
  const seed = hashString(`day:${dayKey}`);
  const en = getLocale() === "en";
  if (total === 0) return (en ? ZERO_EN : ZERO)[seed % ZERO.length];
  if (out.filter((t) => t.category === "DRINK").length >= 2) return (en ? DRINKY_EN : DRINKY)[seed % DRINKY.length];
  const pool = en
    ? total < 3000
      ? LOW_EN
      : total < 12000
        ? MID_EN
        : HIGH_EN
    : total < 3000
      ? LOW
      : total < 12000
        ? MID
        : HIGH;
  return pool[seed % pool.length];
}

const WEEK_UP = ["这周比上周多花了一点点，没关系，下周再轻装上阵。", "支出小幅上扬，偶尔放飞也是生活。"];
const WEEK_DOWN = ["这周比上周省了不少，钱包偷偷笑了 😄", "支出下降，你悄悄变成了会过日子的人。"];
const WEEK_FLAT = ["这周和上周差不多，稳稳的幸福。", "节奏稳定，像一首循环的好歌。"];
const WEEK_FIRST = ["这是你的第一份周报，欢迎来到小票星球～"];

const WEEK_UP_EN = ["A little more than last week — that's fine. Start light again next week.", "Spending ticked up. An occasional splurge is part of living."];
const WEEK_DOWN_EN = ["You saved a fair bit versus last week. The wallet is quietly smiling 😄", "Spending is down; you've become someone who stretches a yuan."];
const WEEK_FLAT_EN = ["About the same as last week. Steady, quiet happiness.", "Same rhythm as last week — like a good song on repeat."];
const WEEK_FIRST_EN = ["This is your first weekly report. Welcome to Receipt Planet."];

export function weeklyComment(totalCents: number, prevCents: number, weekKey: string): string {
  const seed = hashString(`week:${weekKey}`);
  const en = getLocale() === "en";
  if (prevCents <= 0) return (en ? WEEK_FIRST_EN : WEEK_FIRST)[seed % WEEK_FIRST.length];
  const ratio = totalCents / prevCents;
  const pool = en ? (ratio > 1.1 ? WEEK_UP_EN : ratio < 0.9 ? WEEK_DOWN_EN : WEEK_FLAT_EN) : ratio > 1.1 ? WEEK_UP : ratio < 0.9 ? WEEK_DOWN : WEEK_FLAT;
  return pool[seed % pool.length];
}

export function greeting(now: number): string {
  const h = new Date(now).getHours();
  const en = getLocale() === "en";
  if (h < 5) return en ? "Late night. Still up?" : "夜深了，还没睡呀？";
  if (h < 11) return en ? "Morning!" : "早上好呀！";
  if (h < 14) return en ? "Midday — had lunch?" : "中午好～ 吃饭了吗？";
  if (h < 18) return en ? "Good afternoon!" : "下午好呀！";
  if (h < 23) return en ? "Good evening." : "晚上好～";
  return en ? "Late again — get some rest." : "夜深了，早点休息哦。";
}

export function todaySpendLine(totalCents: number, count: number): string {
  if (getLocale() === "en") {
    if (count === 0) return "Nothing spent so far today. Good wallet 🎉";
    return `${count === 1 ? "1 entry" : `${count} entries`} today, ¥${yuan(totalCents)} so far.`;
  }
  if (count === 0) return "今天到现在还没有花钱，钱包乖乖的 🎉";
  return `今天到现在花了 ¥${yuan(totalCents)}，一共 ${count} 笔。`;
}
