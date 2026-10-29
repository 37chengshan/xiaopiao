import { useMemo } from "react";
import { Plus } from "lucide-react";
import { go } from "@/components/desktop/nav";
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
  weekdayAverages,
  worstDay,
} from "@/logic/biz/book";
import { overviewFacts } from "@/logic/biz/summary";
import { WEEKDAY_KEYS, useBizStrings } from "../strings";
import { useBizBook } from "../store";
import { factsToLines } from "../summaryText";
import { RevenueOrdersChart, WeekdayBars } from "./charts";
import { BizPage, Button, Card, Delta, Kpi } from "./primitives";
import { Reveal } from "./reveal";
import { SummaryCard } from "./SummaryCard";

/** `/app/biz` — today's numbers, two weeks at a glance, and what the book says about them. */
export function OverviewPage() {
  const t = useBizStrings();
  const { book, today, updatedAt } = useBizBook();

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
      weekdays: weekdayAverages(book, today, 8).map((w) => ({
        label: t(WEEKDAY_KEYS[w.index]),
        averageCents: w.averageCents,
        days: w.days,
      })),
      lines: factsToLines(overviewFacts(book, today), t),
    };
  }, [book, today, t]);

  return (
    <BizPage>
      <div className="grid grid-cols-12 gap-5">
        <div className="col-span-12 flex flex-wrap items-center justify-between gap-3">
          <p className="text-[12px] text-d-ink-3">
            {t("overview.updated").replace("{}", new Date(updatedAt).toLocaleTimeString("zh-CN", { hour: "2-digit", minute: "2-digit", hour12: false }))}
          </p>
          <Button tone="fill" onClick={() => go("/app/biz/revenue")}>
            <Plus className="size-3.5" strokeWidth={2} aria-hidden />
            {t("overview.manage")}
          </Button>
        </div>

        <Reveal index={0} className="col-span-12 sm:col-span-6 xl:col-span-3">
          <Kpi
            className="h-full w-full"
            label={t("kpi.today")}
            count={{ to: view.revenue.nowCents, format: money }}
            change={view.revenue}
            compareLabel={t("vs.yesterday")}
            extra={<Delta change={view.sameWeekday} compareLabel={t("vs.sameWeekday")} />}
          />
        </Reveal>
        <Reveal index={1} className="col-span-12 sm:col-span-6 xl:col-span-3">
          <Kpi
            className="h-full w-full"
            label={t("kpi.orders")}
            count={{ to: view.orders.nowCents, format: (value) => `${value}` }}
            unit={t("unit.orders")}
            change={view.orders}
            compareLabel={t("vs.yesterday")}
          />
        </Reveal>
        <Reveal index={2} className="col-span-12 sm:col-span-6 xl:col-span-3">
          <Kpi
            className="h-full w-full"
            label={t("kpi.ticket")}
            count={{ to: view.ticket, format: money }}
            change={view.ticketChange}
            compareLabel={t("vs.prior7")}
          />
        </Reveal>
        <Reveal index={3} className="col-span-12 sm:col-span-6 xl:col-span-3">
          <Kpi
            className="h-full w-full"
            label={t("kpi.week")}
            count={{ to: view.week.nowCents, format: money }}
            change={view.week}
            compareLabel={t("vs.lastWeek")}
          />
        </Reveal>

        <Card
          className="col-span-12 xl:col-span-8"
          index={4}
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

        <SummaryCard className="col-span-12 xl:col-span-4" index={5} lines={view.lines} updatedAt={updatedAt} />

        <Card
          className="col-span-12 xl:col-span-8"
          index={6}
          title={t("chart.weekday")}
          action={<span className="text-[11px] text-d-ink-3">{t("chart.weekdayHint")}</span>}
        >
          <WeekdayBars data={view.weekdays} valueLabel={t("analysis.avg")} height={196} />
        </Card>

        <div className="col-span-12 flex min-w-0 flex-col gap-5 xl:col-span-4">
          <DayCard
            index={7}
            title={t("best.title")}
            point={view.best}
            empty={t("best.none")}
            unit={t("unit.orders")}
            weekday={(day) => t(WEEKDAY_KEYS[weekdayIndex(day)])}
          />
          <DayCard
            index={8}
            title={t("worst.title")}
            point={view.worst}
            empty={t("best.none")}
            unit={t("unit.orders")}
            weekday={(day) => t(WEEKDAY_KEYS[weekdayIndex(day)])}
          />
        </div>
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
  index,
}: {
  title: string;
  point: { day: number; revenueCents: number; orders: number } | null;
  empty: string;
  unit: string;
  weekday: (day: number) => string;
  className?: string;
  index?: number;
}) {
  return (
    <Card className={className} index={index} title={title}>
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
