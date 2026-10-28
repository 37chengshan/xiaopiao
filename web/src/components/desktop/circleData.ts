import { type Category, type Cents, type Txn } from "@/domain/types";

/**
 * Local placeholder feed for the circle page. The circle service (sync, real invites) is W4 work;
 * until then these members and messages exist only in this module and nothing is fetched or uploaded.
 * Messages the user sends stay in page state and disappear on reload.
 */
export interface CircleMember {
  id: string;
  name: string;
  emoji: string;
  /** the account viewing the page */
  self?: boolean;
}

export interface Circle {
  id: string;
  name: string;
  emoji: string;
  members: CircleMember[];
}

export interface CircleMessage {
  id: string;
  circleId: string;
  memberId: string;
  text: string;
  at: number;
  weekly?: { totalCents: Cents; count: number; top: Category | null };
}

export const ME = "me";

export const CIRCLES: readonly Circle[] = [
  {
    id: "home",
    name: "我们家",
    emoji: "🏠",
    members: [
      { id: ME, name: "我", emoji: "🙂", self: true },
      { id: "m2", name: "阿哲", emoji: "🧢" },
      { id: "m3", name: "小雨", emoji: "🌧️" },
    ],
  },
  {
    id: "roomies",
    name: "合租三人组",
    emoji: "🛋️",
    members: [
      { id: ME, name: "我", emoji: "🙂", self: true },
      { id: "m4", name: "老王", emoji: "🎧" },
      { id: "m5", name: "Mia", emoji: "🌿" },
      { id: "m6", name: "胖虎", emoji: "🐯" },
    ],
  },
  {
    id: "college",
    name: "大学室友",
    emoji: "🎓",
    members: [
      { id: ME, name: "我", emoji: "🙂", self: true },
      { id: "m7", name: "程子", emoji: "📷" },
      { id: "m8", name: "老六", emoji: "🎮" },
    ],
  },
];

const HOUR = 3_600_000;

export function seedMessages(now: number): CircleMessage[] {
  return [
    { id: "c1", circleId: "home", memberId: "m3", text: "这周外卖是不是有点多 😅", at: now - 26 * HOUR },
    { id: "c2", circleId: "home", memberId: "m2", text: "我这两天自己做了三顿，省了差不多一百。", at: now - 24 * HOUR },
    { id: "c3", circleId: "roomies", memberId: "m5", text: "周末要不要一起去买菜，分摊一下？", at: now - 9 * HOUR },
    { id: "c4", circleId: "college", memberId: "m8", text: "谁还记得我们毕业那年一顿火锅多少钱", at: now - 3 * HOUR },
  ];
}

export function memberOf(circle: Circle, id: string): CircleMember | undefined {
  return circle.members.find((m) => m.id === id);
}

/** Facts a weekly report message shows: totals only, never individual entries. */
export function weeklyFacts(txns: readonly Txn[], start: number, end: number): { totalCents: Cents; count: number; top: Category | null } {
  const out = txns.filter((t) => t.direction === "OUT" && t.occurredAt >= start && t.occurredAt < end);
  const byCat = new Map<Category, number>();
  for (const t of out) byCat.set(t.category, (byCat.get(t.category) ?? 0) + t.amountCents);
  const top = [...byCat.entries()].sort((a, b) => b[1] - a[1])[0];
  return { totalCents: out.reduce((sum, t) => sum + t.amountCents, 0), count: out.length, top: top ? top[0] : null };
}
