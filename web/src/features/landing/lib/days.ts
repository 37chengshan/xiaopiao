/**
 * The shelf's fourteen days and the bento's spend stills — the page's only data.
 *
 * Every day is a real spend list: the title, the remark and the total are derived from it with the same
 * rules the app uses (`dayTitle.ts`), so a receipt on the landing page can never contradict its own lines.
 * Money stays in integer cents (`Cents`) and is only formatted for display.
 */
import type { Cents } from "@/domain/types";
import { yuan } from "@/domain/types";
import type { Locale } from "@/lib/strings";
import { dayRemark, dayTitle, type SpendKind, type TitleKey } from "../dayTitle";
import { IMG } from "./assets";

export interface DayLine {
  kind: SpendKind;
  /** `spend.*` key for the line's label. */
  label: string;
  time: string;
  cents: Cents;
}

/** A line's label key, e.g. `metro` or `breakfast`. */
const line = (kind: SpendKind, label: string, time: string, cents: Cents): DayLine => ({ kind, label, time, cents });

export interface ShelfDay {
  id: string;
  /** Fixed calendar day, month is 1-based. */
  month: number;
  day: number;
  lines: DayLine[];
  photo: string;
}

export const SHELF_DAYS: ShelfDay[] = [
  {
    id: "d10-29",
    month: 10,
    day: 29,
    photo: IMG.handReceipt,
    lines: [
      line("transport", "spend.metro", "08:24", 4_00),
      line("meal", "spend.lunch", "12:30", 28_00),
      line("drink", "spend.tea", "15:40", 18_00),
      line("meal", "spend.dinner", "19:20", 86_00),
    ],
  },
  {
    id: "d10-28",
    month: 10,
    day: 28,
    photo: IMG.receiptStack,
    lines: [
      line("drink", "spend.tea", "10:05", 16_00),
      line("drink", "spend.coffee", "16:10", 12_00),
    ],
  },
  {
    id: "d10-27",
    month: 10,
    day: 27,
    photo: IMG.lockscreenNight,
    lines: [],
  },
  {
    id: "d10-26",
    month: 10,
    day: 26,
    photo: IMG.shopCounter,
    lines: [
      line("grocery", "spend.grocery", "09:40", 128_00),
      line("meal", "spend.lunch", "12:15", 20_00),
    ],
  },
  {
    id: "d10-25",
    month: 10,
    day: 25,
    photo: IMG.friendsTable,
    lines: [
      line("fun", "spend.movie", "14:00", 45_00),
      line("fun", "spend.movie", "19:30", 30_00),
    ],
  },
  {
    id: "d10-24",
    month: 10,
    day: 24,
    photo: IMG.receiptWall,
    lines: [line("shopping", "spend.grocery", "15:20", 528_00)],
  },
  {
    id: "d10-23",
    month: 10,
    day: 23,
    photo: IMG.thermalMacro,
    lines: [
      line("snack", "spend.tea", "09:10", 6_00),
      line("snack", "spend.tea", "10:40", 6_00),
      line("snack", "spend.tea", "12:00", 6_00),
      line("snack", "spend.tea", "14:20", 6_00),
      line("snack", "spend.tea", "15:50", 6_00),
      line("snack", "spend.tea", "17:10", 6_00),
      line("snack", "spend.tea", "19:00", 6_00),
      line("snack", "spend.tea", "21:30", 6_00),
    ],
  },
  {
    id: "d10-22",
    month: 10,
    day: 22,
    photo: IMG.metroPhone,
    lines: [
      line("transport", "spend.metro", "08:10", 4_00),
      line("transport", "spend.metro", "13:30", 6_00),
      line("transport", "spend.taxi", "22:40", 12_00),
    ],
  },
  {
    id: "d10-21",
    month: 10,
    day: 21,
    photo: IMG.breakfastQr,
    lines: [
      line("drink", "spend.tea", "08:05", 8_00),
      line("transport", "spend.metro", "08:40", 4_00),
    ],
  },
  {
    id: "d10-20",
    month: 10,
    day: 20,
    photo: IMG.takeoutNight,
    lines: [
      line("meal", "spend.lunch", "11:50", 32_00),
      line("meal", "spend.dinner", "19:40", 45_00),
      line("drink", "spend.coffee", "21:10", 14_00),
    ],
  },
  {
    id: "d10-19",
    month: 10,
    day: 19,
    photo: IMG.printerSlot,
    lines: [
      line("subscription", "spend.member", "07:30", 25_00),
      line("snack", "spend.tea", "16:20", 9_00),
    ],
  },
  {
    id: "d10-18",
    month: 10,
    day: 18,
    photo: IMG.tearReceipt,
    lines: [
      line("drink", "spend.coffee", "09:20", 24_00),
      line("drink", "spend.tea", "15:00", 18_00),
    ],
  },
  {
    id: "d10-17",
    month: 10,
    day: 17,
    photo: IMG.nightDesk,
    lines: [
      line("grocery", "spend.grocery", "10:30", 66_00),
      line("grocery", "spend.grocery", "18:10", 24_00),
    ],
  },
  {
    id: "d10-16",
    month: 10,
    day: 16,
    photo: IMG.handReceipt,
    lines: [
      line("snack", "spend.tea", "11:15", 12_00),
      line("snack", "spend.tea", "17:45", 9_00),
    ],
  },
];

export const total = (lines: readonly DayLine[]): Cents => lines.reduce((sum, l) => sum + l.cents, 0);

export interface DayFacts {
  total: Cents;
  title: TitleKey;
  remark: `remark.${"zero" | "small" | "normal" | "big"}`;
  count: number;
}

export function dayFacts(day: ShelfDay): DayFacts {
  const sum = total(day.lines);
  return {
    total: sum,
    title: dayTitle(day.lines),
    remark: dayRemark(sum),
    count: day.lines.length,
  };
}

/** "136.00" — thermal paper always shows cents; `yuan()` drops trailing zeros. */
export function receiptAmount(cents: Cents): string {
  const text = yuan(cents);
  return text.includes(".") ? text : `${text}.00`;
}

const WEEKDAY_ZH = ["日", "一", "二", "三", "四", "五", "六"];
const WEEKDAY_EN = ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"];
const MONTH_EN = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];

export function dayDate(day: ShelfDay, locale: Locale): { short: string; weekday: string; full: string } {
  const weekdayIndex = new Date(2026, day.month - 1, day.day).getDay();
  if (locale === "en") {
    const weekday = WEEKDAY_EN[weekdayIndex];
    return { short: `${MONTH_EN[day.month - 1]} ${day.day}`, weekday, full: `${weekday}, ${MONTH_EN[day.month - 1]} ${day.day}` };
  }
  return {
    short: `${day.month}月${day.day}日`,
    weekday: `周${WEEKDAY_ZH[weekdayIndex]}`,
    full: `${day.month}月${day.day}日 周${WEEKDAY_ZH[weekdayIndex]}`,
  };
}

/** The bento's "stamp your day" options: the stills Luna generated, one per spending habit. */
export interface SpendItem {
  kind: SpendKind;
  label: string;
  cents: Cents;
  img: string;
}

export const SPEND_ITEMS: SpendItem[] = [
  { kind: "meal", label: "spend.breakfast", cents: 8_00, img: "/img/spend-breakfast.webp" },
  { kind: "transport", label: "spend.commute", cents: 4_00, img: "/img/spend-metro.webp" },
  { kind: "meal", label: "spend.lunch", cents: 28_00, img: "/img/spend-lunch.webp" },
  { kind: "drink", label: "spend.tea", cents: 16_00, img: "/img/spend-tea.webp" },
  { kind: "drink", label: "spend.coffee", cents: 18_00, img: "/img/spend-coffee.webp" },
  { kind: "transport", label: "spend.taxi", cents: 26_00, img: "/img/spend-taxi.webp" },
  { kind: "grocery", label: "spend.grocery", cents: 68_00, img: "/img/spend-grocery.webp" },
  { kind: "fun", label: "spend.movie", cents: 45_00, img: "/img/spend-movie.webp" },
  { kind: "subscription", label: "spend.member", cents: 25_00, img: "/img/spend-member.webp" },
];
