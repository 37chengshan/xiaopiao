import { useMemo, useState } from "react";
import { addDays, money, moneyCompact } from "@/logic/biz/format";
import {
  monthlyTrend,
  ticketBandLabel,
  ticketHistogram,
  weekdayAverages,
  weeklyTrend,
  entriesBetween,
} from "@/logic/biz/book";
import { overviewFacts } from "@/logic/biz/summary";
import { WEEKDAY_KEYS, useBizStrings } from "../strings";
import { useBizBook } from "../store";
import { factsToLines } from "../summaryText";
import { Histogram, TrendArea, WeekdayBars } from "./charts";
import { BizPage, Card, Segmented } from "./primitives";
import { SummaryCard } from "./SummaryCard";

type Grain = "week" | "month";

/** `/app/biz/analysis` — the same book, looked at by weekday, by period and by ticket size. */
export function AnalysisPage() {
  const t = useBizStrings();
  const { book, today, updatedAt } = useBizBook();
  const [grain, setGrain] = useState<Grain>("week");
  const [compare, setCompare] = useState(true);

  const view = useMemo(() => {
    const weekdays = weekdayAverages(book, today, 8);
    const month = entriesBetween(book, addDays(today, -29), today);
    const trend = grain === "week" ? weeklyTrend(book, today, 8, compare) : monthlyTrend(book, today, 6, compare);
    return {
      trend,
      weekdays: weekdays.map((w) => ({ label: t(WEEKDAY_KEYS[w.index]), averageCents: w.averageCents, days: w.days })),
      hasWeekday: weekdays.some((w) => w.days > 0),
      histogram: ticketHistogram(month).map((band) => ({ label: ticketBandLabel(band), days: band.days })),
      totals: {
        revenue: trend.reduce((n, p) => n + p.revenueCents, 0),
        buckets: trend.filter((p) => p.days > 0).length,
        days: trend.reduce((n, p) => n + p.days, 0),
      },
      lines: factsToLines(overviewFacts(book, today), t),
    };
  }, [book, today, grain, compare, t]);

  const busy = view.weekdays.reduce((a, b) => (b.averageCents > a.averageCents ? b : a));
  const quiet = view.weekdays.reduce((a, b) => (b.days > 0 && (a.days === 0 || b.averageCents < a.averageCents) ? b : a));

  return (
    <BizPage>
      <div className="grid grid-cols-12 gap-5">
        <div className="col-span-12 flex flex-wrap items-center justify-between gap-3">
          <Segmented<Grain>
            label={t("analysis.trend")}
            value={grain}
            onChange={setGrain}
            options={[
              { value: "week", label: t("analysis.byWeek") },
              { value: "month", label: t("analysis.byMonth") },
            ]}
          />
          <label className="flex cursor-pointer items-center gap-2 text-[12px] font-medium text-d-ink-2">
            <input
              type="checkbox"
              checked={compare}
              onChange={(e) => setCompare(e.target.checked)}
              className="size-3.5 accent-[var(--d-accent)]"
            />
            {t("analysis.compare")}
          </label>
        </div>

        <Card className="col-span-12 xl:col-span-7" title={t("analysis.trend")} index={0}>
          <TrendArea
            data={view.trend.map((p) => ({
              label: p.label,
              // A bucket without an entry is unknown, not zero: the line starts where the book starts.
              revenueCents: p.days === 0 ? null : p.revenueCents,
              compareCents: p.compareCents !== null && p.compareCents === 0 ? null : p.compareCents,
            }))}
            label={t("chart.revenue")}
            compareLabel={t("chart.compare")}
            height={236}
          />
          <div className="mt-3 flex flex-wrap items-center gap-x-5 gap-y-1 border-t border-d-hairline pt-3 text-[11px] text-d-ink-2">
            <span>
              {t("revenue.total")} {money(view.totals.revenue)}
            </span>
            <span>
              {t("analysis.avg")} {moneyCompact(Math.round(view.totals.revenue / Math.max(1, view.totals.buckets)))}
            </span>
            <span>{view.totals.days} {t("unit.days")}</span>
          </div>
        </Card>

        <Card className="col-span-12 xl:col-span-5" title={t("analysis.ticket")} index={1}>
          <Histogram data={view.histogram} valueLabel={t("unit.days")} height={212} />
          <p className="mt-3 border-t border-d-hairline pt-3 text-[11px] leading-4 text-d-ink-3">{t("analysis.ticketHint")}</p>
        </Card>

        <Card className="col-span-12 xl:col-span-7" title={t("analysis.weekday")} index={2}>
          {view.hasWeekday ? (
            <>
              <WeekdayBars data={view.weekdays} valueLabel={t("analysis.avg")} height={228} />
              <div className="mt-3 flex flex-wrap items-center gap-x-5 gap-y-1 border-t border-d-hairline pt-3 text-[11px] text-d-ink-2">
                <span>
                  {busy.label} {money(busy.averageCents)}
                </span>
                {quiet.days > 0 && (
                  <span>
                    {t("analysis.weakest")} {quiet.label} {money(quiet.averageCents)}
                  </span>
                )}
                <span className="text-d-ink-3">{t("analysis.weekdayHint")}</span>
              </div>
            </>
          ) : (
            <p className="py-10 text-center text-[12px] text-d-ink-2">{t("analysis.noWeekday")}</p>
          )}
        </Card>

        <SummaryCard className="col-span-12 xl:col-span-5" index={3} lines={view.lines} updatedAt={updatedAt} />
      </div>
    </BizPage>
  );
}
