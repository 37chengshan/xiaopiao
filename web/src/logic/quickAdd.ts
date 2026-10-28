import { type Cents, type Category, type Direction, type Source, startOfDay } from "../domain/types";
import { categorize } from "./categorize";
import { addDays, at } from "./time";

/** A parsed-but-unconfirmed entry. Humans confirm or edit before it becomes a Txn. */
export interface DraftTxn {
  id: string;
  amountCents: Cents;
  direction: Direction;
  merchant: string;
  category: Category;
  occurredAt: number;
  source: Extract<Source, "QUICK_ADD" | "VOICE">;
}

const DATE_WORDS: ReadonlyArray<readonly [RegExp, number]> = [
  [/大前天/, 3],
  [/前天/, 2],
  [/昨天|昨日|昨晚|昨夜|昨早/, 1],
  [/今天|今日|今早|今晚|今儿/, 0],
];
const DATE_STRIP =
  /大前天|前天|昨天|昨日|昨晚|昨夜|昨早|今天|今日|今早|今晚|今儿|上周[一二三四五六日天]|周[一二三四五六日天]|星期[一二三四五六日天]/g;
const FILLERS = /^(和|与|及|还有|以及|然后|又|再|另外|花了|花|买了|买|吃了|喝了|付了|付|共|一共|总共|用了|的|了|是|在|去)+/;
const TRAILING = /(的|了|和|与|及)+$/;
const WEEKDAY_CHARS = "日一二三四五六";

const TIME_HINTS: ReadonlyArray<readonly [RegExp, number, number]> = [
  [/早饭|早餐|早上|今早|昨早|早点/, 8, 0],
  [/午饭|午餐|中午|午休/, 12, 30],
  [/下午茶|下午/, 15, 30],
  [/晚饭|晚餐|晚上|今晚|昨晚/, 19, 0],
  [/夜宵|宵夜|昨夜|深夜/, 22, 30],
  [/奶茶|咖啡/, 15, 30],
];

const INCOME = /工资|收入|报销|退款|奖金|收到/;
const NUMBER_TOKEN = /(\d+(?:\.\d{1,2})?)\s*(?:元|块钱|块|¥|￥)?|([^\d]+)/g;
const MAX_YUAN = 1_000_000;

let counter = 0;
function nextId(now: number): string {
  counter += 1;
  return `draft-${now}-${counter}`;
}

/** Offset in days back from today for a weekday word like 周三 / 上周五; null if none. */
function weekdayOffset(word: string, today: number): number | null {
  const m = word.match(/(上周)?(?:周|星期)([一二三四五六日天])/);
  if (!m) return null;
  const idx = m[2] === "天" ? 0 : WEEKDAY_CHARS.indexOf(m[2]);
  const todayDow = new Date(today).getDay();
  if (m[1]) return ((todayDow + 6) % 7) + 7 - ((idx + 6) % 7);
  return (todayDow - idx + 7) % 7;
}

function detectDateOffset(word: string, today: number): number | null {
  for (const [re, off] of DATE_WORDS) if (re.test(word)) return off;
  const w = word.match(/(上周)?(周|星期)[一二三四五六日天]/);
  if (w) return weekdayOffset(w[0], today);
  return null;
}

function timeHint(text: string): [number, number] | null {
  for (const [re, h, m] of TIME_HINTS) if (re.test(text)) return [h, m];
  return null;
}

function cleanName(raw: string): string {
  let s = raw.replace(DATE_STRIP, "").replace(/[¥￥]/g, "");
  for (let i = 0; i < 3; i++) s = s.replace(FILLERS, "").replace(TRAILING, "");
  return s.trim();
}

function buildOccurredAt(now: number, offset: number, hintText: string): number {
  const today = startOfDay(now);
  const day = addDays(today, -offset);
  const hint = timeHint(hintText);
  if (offset === 0) {
    if (!hint) return now;
    return Math.min(at(day, hint[0], hint[1]), now);
  }
  return hint ? at(day, hint[0], hint[1]) : at(day, 12, 0);
}

interface Pending {
  name: string;
  hintText: string;
  offset: number;
}

/**
 * Rule-based QuickAdd parser. "午饭 32 奶茶 15 昨天打车 18" → three drafts.
 * Names may come before or after the amount; date words ("昨天/前天/周三") apply to what follows.
 */
export function parseQuickAdd(
  text: string,
  now: number = Date.now(),
  source: DraftTxn["source"] = "QUICK_ADD",
): DraftTxn[] {
  const today = startOfDay(now);
  const drafts: DraftTxn[] = [];
  let offset = 0;
  let pendingName: Pending | null = null;
  let awaiting: { amountYuan: number; offset: number } | null = null;

  const push = (amountYuan: number, name: string, off: number, hintText: string) => {
    if (!(amountYuan > 0) || amountYuan > MAX_YUAN) return;
    const merchant = name || "随手记";
    drafts.push({
      id: nextId(now),
      amountCents: Math.round(amountYuan * 100),
      direction: INCOME.test(merchant) ? "IN" : "OUT",
      merchant,
      category: name ? categorize(name) : "OTHER",
      occurredAt: buildOccurredAt(now, off, `${hintText}${name}`),
      source,
    });
  };

  for (const match of text.matchAll(NUMBER_TOKEN)) {
    if (match[1] !== undefined) {
      const amount = Number(match[1]);
      if (pendingName) {
        push(amount, pendingName.name, pendingName.offset, pendingName.hintText);
        pendingName = null;
      } else {
        awaiting = { amountYuan: amount, offset };
      }
      continue;
    }
    for (const word of (match[2] ?? "").split(/[\s,，、;；。.!！?？]+/)) {
      if (!word) continue;
      const found = detectDateOffset(word, today);
      if (found !== null) offset = found;
      const name = cleanName(word);
      if (!name) continue;
      if (awaiting) {
        push(awaiting.amountYuan, name, awaiting.offset, word);
        awaiting = null;
      } else {
        pendingName = { name, hintText: word, offset };
      }
    }
  }
  if (awaiting) push(awaiting.amountYuan, "", awaiting.offset, "");
  return drafts;
}
