import { useMemo } from "react";
import { addDays, money, shortDate, weekdayIndex } from "@/logic/biz/format";
import {
  averageTicketOf,
  bestDay,
  change,
  entryFor,
  series,
  todayVsYesterday,
  weekToDateChange,
  entriesBetween,
  worstDay,
} from "@/logic/biz/book";
import { overviewFacts } from "@/logic/biz/summary";
import { WEEKDAY_KEYS, useBizStrings } from "../strings";
import { useBizBook } from "../store";
import { factsToLines } from "../summaryText";
import { RevenueOrdersChart } from "./charts";
import { BizPage, Card, Kpi } from "./primitives";

/** `/app/biz` — today's numbers, two weeks at a glance, and what the book says about them. */
export function OverviewPage() {
  const t = useBizStrings();
  const { book, today } = useBizBook();

  const view = useMemo(() => {
    const todayEntry = entryFor(book, today);
    const yesterdayEntry = entryFor(book, addDays(today, -1));
    const lastWeekSameDay = entryFor(book, addDays(today, -7));
    const ticket = averageTicketOf(entriesBetween(book, addDays(today, -6), today));
    return {
      revenue: todayVsYesterday(book, today),
      sameWeekday: change(todayEntry?.revenueCents ?? 0, lastWeekSameDay?.revenueCents ?? 0),
      orders: change(todayEntry?.orders ?? 0, yesterdayEntry?.orders ?? 0),
      ticket,
      ticketChange: change(ticket, averageTicketOf(entriesBetween(book, addDays(today, -13), addDays(today, -7)))),
      week: weekToDateChange(book, today),
      points: series(book, today, 14).map((p) => ({
        label: shortDate(p.day),
        revenueCents: p.revenueCents,
        // A day without an entry is unknown, not zero: the order line breaks instead of dipping.
        orders: p.hasEntry ? p.orders : null,
        hasEntry: p.hasEntry,
      })),
      best: bestDay(book, today),
      worst: worstDay(book, today),
      lines: factsToLines(overviewFacts(book, today), t),
    };
  }, [book, today, t]);

  return (
    <BizPage>
      <div className="grid grid-cols-12 gap-5">
        <Kpi
          className="col-span-12 sm:col-span-6 xl:col-span-3"
          label={t("kpi.today")}
          value={money(view.revenue.nowCents)}
          change={view.revenue}
          compareLabel={t("vs.yesterday")}
        />
        <Kpi
          className="col-span-12 sm:col-span-6 xl:col-span-3"
          label={t("kpi.orders")}
          value={`${view.orders.nowCents}`}
          unit={t("unit.orders")}
          change={view.orders}
          compareLabel={t("vs.yesterday")}
        />
        <Kpi
          className="col-span-12 sm:col-span-6 xl:col-span-3"
          label={t("kpi.ticket")}
          value={money(view.ticket)}
          change={view.ticketChange}
          compareLabel={t("vs.prior7")}
        />
        <Kpi
          className="col-span-12 sm:col-span-6 xl:col-span-3"
          label={t("kpi.week")}
          value={money(view.week.nowCents)}
          change={view.week}
          compareLabel={t("vs.lastWeek")}
        />

        <Card
          className="col-span-12 xl:col-span-8"
          title={`${t("chart.last14")} · ${t("chart.revenue")} / ${t("chart.orders")}`}
          action={
            <span className="flex items-center gap-3 text-[11px] text-d-ink-3">
              <span className="flex items-center gap-1">
                <span className="size-2 rounded-sm bg-d-accent" aria-hidden />
                {t("chart.revenue")}
              </span>
              <span className="flex items-center gap-1">
                <span className="h-0.5 w-3 rounded-full bg-d-ink-2" aria-hidden />
                {t("chart.orders")}
              </span>
            </span>
          }
        >
          <RevenueOrdersChart
            data={view.points}
            revenueLabel={t("chart.revenue")}
            ordersLabel={t("chart.orders")}
            height={248}
          />
        </Card>

        <Card
          className="col-span-12 xl:col-span-4"
          title={t("summary.title")}
          action={<span className="text-[11px] text-d-ink-3">{t("summary.honest")}</span>}
        >
          {view.lines.length === 0 ? (
            <p className="py-8 text-center text-[12px] leading-5 text-d-ink-2">{t("summary.empty")}</p>
          ) : (
            <ul className="space-y-3">
              {view.lines.map((line) => (
                <li key={line.text} className="flex gap-2.5 text-[12px] leading-5">
                  <span
                    className="mt-[7px] size-1.5 shrink-0 rounded-full"
                    style={{
                      backgroundColor:
                        line.tone === "up"
                          ? "var(--d-positive)"
                          : line.tone === "down"
                            ? "var(--d-negative)"
                            : line.tone === "warn"
                              ? "var(--d-warning)"
                              : "var(--d-ink-3)",
                    }}
                    aria-hidden
                  />
                  <span className="text-d-ink-2">{line.text}</span>
                </li>
              ))}
            </ul>
          )}
        </Card>

        <DayCard
          className="col-span-12 md:col-span-6"
          title={t("best.title")}
          point={view.best}
          empty={t("best.none")}
          unit={t("unit.orders")}
          weekday={(day) => t(WEEKDAY_KEYS[weekdayIndex(day)])}
        />
        <DayCard
          className="col-span-12 md:col-span-6"
          title={t("worst.title")}
          point={view.worst}
          empty={t("best.none")}
          unit={t("unit.orders")}
          weekday={(day) => t(WEEKDAY_KEYS[weekdayIndex(day)])}
        />
      </div>
    </BizPage>
  );
}

function DayCard({
  title,
  point,
  empty,
  unit,
  weekday,
  className,
}: {
  title: string;
  point: { day: number; revenueCents: number; orders: number } | null;
  empty: string;
  unit: string;
  weekday: (day: number) => string;
  className?: string;
}) {
  return (
    <Card className={className} title={title}>
      {point ? (
        <div className="flex items-end justify-between gap-3">
          <div>
            <p className="text-[12px] leading-4 text-d-ink-2">
              {shortDate(point.day)} · {weekday(point.day)}
            </p>
            <p className="mt-1 text-[22px] font-semibold leading-7 tracking-[-0.02em] tabular-nums">
              {money(point.revenueCents)}
            </p>
          </div>
          <p className="text-[12px] leading-4 tabular-nums text-d-ink-2">
            {point.orders > 0 ? `${point.orders} ${unit}` : "—"}
          </p>
        </div>
      ) : (
        <p className="py-4 text-[12px] text-d-ink-2">{empty}</p>
      )}
    </Card>
  );
}
