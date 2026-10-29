import { startOfDay, type Category, type Cents, type Txn } from "@/domain/types";
import { type Locale } from "@/lib/strings";

/**
 * Local placeholder feed for the circle page. Members, receipts and seed messages live only in this
 * module; nothing is fetched or uploaded, and messages the user sends sit in page state.
 *
 * Seed names and bubbles carry both languages, so switching the reading language switches the feed
 * too instead of leaving Chinese behind. Everything here is mock content and stays identifiable as
 * such in code, never on screen.
 */

/** What one member lets the room see. Mirrors the phone's `CircleShare` enum (ui/circle/CircleModel.kt). */
export type ShareItem = "todayTotal" | "categoryMix" | "weeklyTitles" | "txnDetail" | "receiptFull";

export type Shares = Record<ShareItem, boolean>;

export const SHARE_ORDER: readonly ShareItem[] = ["todayTotal", "categoryMix", "weeklyTitles", "txnDetail", "receiptFull"];

/** Conservative defaults: aggregates yes, anything that names a purchase no. */
export const SHARE_DEFAULTS: Shares = {
  todayTotal: true,
  categoryMix: true,
  weeklyTitles: true,
  txnDetail: false,
  receiptFull: false,
};

export const ME = "me";

interface Localized {
  zh: string;
  en: string;
}

function localize(value: Localized, locale: Locale): string {
  return locale === "en" ? value.en : value.zh;
}

export interface CircleMember {
  id: string;
  name: string;
  emoji: string;
  /** the account viewing the page */
  self?: boolean;
  shares: Shares;
}

export interface Circle {
  id: string;
  name: string;
  emoji: string;
  members: CircleMember[];
}

interface MemberSeed extends Pick<CircleMember, "id" | "emoji" | "self"> {
  name: Localized;
}

interface CircleSeed {
  id: string;
  name: Localized;
  emoji: string;
  members: MemberSeed[];
}

const CIRCLES: readonly CircleSeed[] = [
  {
    id: "home",
    name: { zh: "我们家", en: "Our home" },
    emoji: "🏠",
    members: [
      { id: ME, name: { zh: "我", en: "Me" }, emoji: "🙂", self: true },
      { id: "m2", name: { zh: "阿哲", en: "Zhe" }, emoji: "🧢" },
      { id: "m3", name: { zh: "小雨", en: "Xiaoyu" }, emoji: "🌧️" },
    ],
  },
  {
    id: "roomies",
    name: { zh: "合租三人组", en: "Flat share" },
    emoji: "🛋️",
    members: [
      { id: ME, name: { zh: "我", en: "Me" }, emoji: "🙂", self: true },
      { id: "m4", name: { zh: "老王", en: "Lao Wang" }, emoji: "🎧" },
      { id: "m5", name: { zh: "Mia", en: "Mia" }, emoji: "🌿" },
      { id: "m6", name: { zh: "胖虎", en: "Panghu" }, emoji: "🐯" },
    ],
  },
  {
    id: "college",
    name: { zh: "大学室友", en: "College roommates" },
    emoji: "🎓",
    members: [
      { id: ME, name: { zh: "我", en: "Me" }, emoji: "🙂", self: true },
      { id: "m7", name: { zh: "程子", en: "Chengzi" }, emoji: "📷" },
      { id: "m8", name: { zh: "老六", en: "Lao Liu" }, emoji: "🎮" },
    ],
  },
];

/** Resolved for the reading language; callers pass the locale they render with. */
export function circles(locale: Locale): Circle[] {
  return CIRCLES.map((circle) => ({
    id: circle.id,
    name: localize(circle.name, locale),
    emoji: circle.emoji,
    members: circle.members.map((member) => ({
      id: member.id,
      name: localize(member.name, locale),
      emoji: member.emoji,
      self: member.self,
      shares: { ...SHARE_DEFAULTS },
    })),
  }));
}

export function memberOf(circle: Circle, id: string): CircleMember | undefined {
  return circle.members.find((member) => member.id === id);
}

/* ------------------------------------------------------------------------ posts */

export interface ReceiptItem {
  merchant: string;
  cents: Cents;
  category: Category;
}

export interface WeeklyFacts {
  totalCents: Cents;
  count: number;
  top: Category | null;
}

interface PostBase {
  id: string;
  circleId: string;
  memberId: string;
  at: number;
}

export interface TextPost extends PostBase {
  kind: "text";
  text: string;
}

export interface WeeklyPost extends PostBase {
  kind: "weekly";
  weekly: WeeklyFacts;
}

export interface ReceiptPost extends PostBase {
  kind: "receipt";
  dayStart: number;
  items: ReceiptItem[];
}

export type CirclePost = TextPost | WeeklyPost | ReceiptPost;

const HOUR = 3_600_000;

function seedItem(merchant: Localized, cents: Cents, category: Category, locale: Locale): ReceiptItem {
  return { merchant: localize(merchant, locale), cents, category };
}

/** Fixed mock feed, anchored to "now": three circles, a couple of receipts and the bubbles around them. */
export function seedMessages(now: number, locale: Locale): CirclePost[] {
  const at = (hoursAgo: number) => now - hoursAgo * HOUR;
  const text = (id: string, circleId: string, memberId: string, hoursAgo: number, body: Localized): TextPost => ({
    kind: "text",
    id,
    circleId,
    memberId,
    at: at(hoursAgo),
    text: localize(body, locale),
  });
  const receipt = (id: string, circleId: string, memberId: string, hoursAgo: number, items: ReceiptItem[]): ReceiptPost => {
    const time = at(hoursAgo);
    return { kind: "receipt", id, circleId, memberId, at: time, dayStart: startOfDay(time), items };
  };

  return [
    receipt("seed-r1", "home", "m3", 26, [
      seedItem({ zh: "食堂", en: "Canteen" }, 1200, "MEAL", locale),
      seedItem({ zh: "奶茶", en: "Milk tea" }, 1600, "DRINK", locale),
      seedItem({ zh: "电影票", en: "Cinema ticket" }, 4500, "FUN", locale),
    ]),
    text("seed-t1", "home", "m3", 25.8, {
      zh: "食堂今天的糖醋排骨居然不甜",
      en: "The sweet-and-sour ribs at the canteen weren't sweet today",
    }),
    text("seed-t2", "home", "m2", 24, {
      zh: "我这两天自己做了三顿，省了差不多一百。",
      en: "Cooked three meals in the last two days, saved about a hundred.",
    }),
    receipt("seed-r2", "home", ME, 15, [
      seedItem({ zh: "地铁", en: "Metro" }, 400, "TRANSPORT", locale),
      seedItem({ zh: "公司食堂", en: "Office canteen" }, 1800, "MEAL", locale),
      seedItem({ zh: "瑞幸咖啡", en: "Luckin Coffee" }, 1650, "DRINK", locale),
    ]),
    text("seed-t3", "home", ME, 14.6, {
      zh: "淘宝那单是给家里买的路由器",
      en: "The Taobao order was the router for home",
    }),
    text("seed-t4", "roomies", "m5", 9, {
      zh: "周末要不要一起去买菜，分摊一下？",
      en: "Groceries this weekend? We can split it.",
    }),
    receipt("seed-r3", "roomies", "m4", 30, [
      seedItem({ zh: "盒马鲜生", en: "Hema" }, 12640, "GROCERY", locale),
      seedItem({ zh: "菜市场", en: "Wet market" }, 3200, "GROCERY", locale),
      seedItem({ zh: "鲜牛奶", en: "Fresh milk" }, 1990, "GROCERY", locale),
    ]),
    text("seed-t5", "college", "m8", 3, {
      zh: "谁还记得我们毕业那年一顿火锅多少钱",
      en: "Anyone remember what hotpot cost the year we graduated?",
    }),
    receipt("seed-r4", "college", "m7", 20, [
      seedItem({ zh: "食堂", en: "Canteen" }, 1500, "MEAL", locale),
      seedItem({ zh: "蜜雪冰城", en: "Mixue" }, 700, "DRINK", locale),
    ]),
  ];
}

/* ---------------------------------------------------------------------- masking */

export interface CategorySlice {
  category: Category;
  cents: Cents;
  /** display only; money stays in cents */
  percent: number;
}

/** What a reader may see on one receipt. `null` means "not shared". */
export interface ReceiptView {
  totalCents: Cents | null;
  slices: CategorySlice[] | null;
  detail: ReceiptItem[] | null;
  full: ReceiptItem[] | null;
}

/**
 * Applies an author's sharing profile. Masking is hierarchical, as on the phone: without the daily
 * total neither detail nor the full receipt can be shown, because summing either would leak the
 * total; the full receipt also needs itemised entries.
 */
export function receiptView(items: readonly ReceiptItem[], shares: Shares): ReceiptView {
  const total = items.reduce((sum, item) => sum + item.cents, 0);
  const totalVisible = shares.todayTotal;
  const detailVisible = totalVisible && shares.txnDetail;
  const fullVisible = detailVisible && shares.receiptFull;

  const byCategory = new Map<Category, Cents>();
  for (const item of items) byCategory.set(item.category, (byCategory.get(item.category) ?? 0) + item.cents);
  const slices: CategorySlice[] = [...byCategory.entries()]
    .map(([category, cents]) => ({ category, cents, percent: total > 0 ? Math.round((cents / total) * 100) : 0 }))
    .sort((a, b) => b.cents - a.cents);

  return {
    totalCents: totalVisible ? total : null,
    slices: shares.categoryMix ? slices : null,
    detail: detailVisible ? [...items] : null,
    full: fullVisible ? [...items] : null,
  };
}

/* ------------------------------------------------------------------- feed rows */

export type FeedRow = { kind: "day"; key: string; dayStart: number } | { kind: "post"; key: string; post: CirclePost };

/** Chronological posts with one day separator before the first post of each day. */
export function feedRows(posts: readonly CirclePost[]): FeedRow[] {
  const rows: FeedRow[] = [];
  let day: number | null = null;
  for (const post of [...posts].sort((a, b) => a.at - b.at)) {
    const dayStart = startOfDay(post.at);
    if (dayStart !== day) {
      rows.push({ kind: "day", key: `day-${dayStart}`, dayStart });
      day = dayStart;
    }
    rows.push({ kind: "post", key: post.id, post });
  }
  return rows;
}

/* ---------------------------------------------------------------------- weekly */

/** Facts a weekly report shows: totals only, never individual entries. */
export function weeklyFacts(txns: readonly Txn[], start: number, end: number): WeeklyFacts {
  const out = txns.filter((t) => t.direction === "OUT" && t.occurredAt >= start && t.occurredAt < end);
  const byCat = new Map<Category, number>();
  for (const t of out) byCat.set(t.category, (byCat.get(t.category) ?? 0) + t.amountCents);
  const top = [...byCat.entries()].sort((a, b) => b[1] - a[1])[0];
  return { totalCents: out.reduce((sum, t) => sum + t.amountCents, 0), count: out.length, top: top ? top[0] : null };
}
