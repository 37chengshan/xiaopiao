import { money, shortDate } from "@/logic/biz/format";
import type { OverviewFact, ReportFact } from "@/logic/biz/summary";
import { WEEKDAY_KEYS, type s } from "./strings";
import { fill } from "./text";

/**
 * Facts → sentences. The numbers always come from `logic/biz/summary.ts`, so a wording change
 * (or a later model-backed phrasing) can never move a figure.
 */
export type Tone = "up" | "down" | "flat" | "warn";

export interface SummaryLine {
  tone: Tone;
  text: string;
}

type Strings = typeof s;

function weekdayName(t: Strings, index: number): string {
  return t(WEEKDAY_KEYS[index] ?? WEEKDAY_KEYS[0]);
}

export function factsToLines(facts: readonly OverviewFact[], t: Strings): SummaryLine[] {
  return facts.map((fact) => {
    switch (fact.kind) {
      case "decline":
        return {
          tone: "warn" as const,
          text: fill(t("summary.decline"), `${fact.streak}`, money(fact.fromCents), money(fact.toCents)),
        };
      case "week": {
        const total = money(fact.change.nowCents);
        const rel = !fact.change.hasBase
          ? t("summary.weekNoBase")
          : fact.change.diffCents === 0
            ? t("summary.weekFlat")
            : fact.change.diffCents > 0
              ? fill(t("summary.weekUp"), money(fact.change.diffCents), `${Math.round((fact.change.pct ?? 0) * 100)}%`)
              : fill(t("summary.weekDown"), money(-fact.change.diffCents), `${Math.abs(Math.round((fact.change.pct ?? 0) * 100))}%`);
        return {
          tone: !fact.change.hasBase || fact.change.diffCents === 0 ? ("flat" as const) : fact.change.diffCents > 0 ? ("up" as const) : ("down" as const),
          text: fill(t("summary.week"), `${fact.days}`, total, rel),
        };
      }
      case "weekday":
        return {
          tone: "flat" as const,
          text: fill(
            t("summary.weekday"),
            weekdayName(t, fact.best.index),
            money(fact.best.averageCents),
            weekdayName(t, fact.worst.index),
            money(fact.worst.averageCents),
            fact.ratio === null ? "" : fill(t("summary.weekdayRatio"), (Math.round(fact.ratio * 10) / 10).toString()),
          ),
        };
      case "ticket": {
        const c = fact.change;
        const rel = !c.hasBase
          ? t("summary.ticketNoBase")
          : c.diffCents === 0
            ? t("summary.ticketFlat")
            : c.diffCents > 0
              ? fill(t("summary.ticketUp"), money(c.diffCents))
              : fill(t("summary.ticketDown"), money(-c.diffCents));
        return {
          tone: !c.hasBase || c.diffCents === 0 ? ("flat" as const) : c.diffCents > 0 ? ("up" as const) : ("down" as const),
          text: fill(t("summary.ticket"), money(c.nowCents), rel),
        };
      }
      case "bestDay":
        return {
          tone: "up" as const,
          text:
            fill(t("summary.bestDay"), dayPart(t, fact.best.day), money(fact.best.revenueCents)) +
            (fact.worst
              ? ` ${fill(t("summary.worstDay"), dayPart(t, fact.worst.day), money(fact.worst.revenueCents))}`
              : ""),
        };
    }
  });
}

/** One line for the bottom of the daily receipt: this day, and the same weekday a week earlier. */
export function reportLine(fact: ReportFact, t: Strings): string {
  if (!fact.hasEntry) return t("report.noEntry");
  const total = money(fact.revenueCents);
  if (!fact.change.hasBase) return fill(t("report.lineNoBase"), total);
  if (fact.change.diffCents === 0) return fill(t("report.lineFlat"), total);
  const pct = `${Math.abs(Math.round((fact.change.pct ?? 0) * 100))}%`;
  return fact.change.diffCents > 0
    ? fill(t("report.lineUp"), total, money(fact.change.diffCents), pct)
    : fill(t("report.lineDown"), total, money(-fact.change.diffCents), pct);
}

/** "10/24 (Saturday)" / "10/24（周六）" — the weekday in brackets, with the locale's own punctuation. */
function dayPart(t: Strings, day: number): string {
  return fill(t("summary.dayPart"), shortDate(day), weekdayName(t, weekdayOf(day)));
}

function weekdayOf(day: number): number {
  return (new Date(day).getDay() + 6) % 7;
}
