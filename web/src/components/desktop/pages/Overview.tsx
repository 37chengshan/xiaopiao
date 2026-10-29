import { useMemo } from "react";
import { Plus, Printer, RefreshCw } from "lucide-react";
import { useLocale } from "@/lib/strings";
import { AnimatePresence, motion } from "framer-motion";
import { CATEGORIES, startOfDay, type Txn } from "@/domain/types";
import { addDays, hhmm } from "@/logic/time";
import { useLedger } from "@/services/LedgerProvider";
import { AreaTrend, Donut, Legend, type Slice } from "../charts";
import { DataTable, type Column } from "../DataTable";
import { dayLabel, delta, money, relativeDay } from "../format";
import { categoryLabel } from "../labels";
import { useFirstPaint } from "../hooks";
import { Loadable, Stagger } from "../motion";
import { go } from "../nav";
import { monthStartOf, peakDayOf, seriesOf, sumOut } from "../statsModel";
import { Button, Card, EmptyState, Hint, Kpi, Skeleton } from "../ui";
import { s } from "../strings";
import { useShell } from "../Shell";

/**
 * The warm band at the top of 总览 (tt 2026-10-28): today's receipt, one plain sentence about it,
 * and the print button. It is the only saturated surface on the page, so it sets the mood without
 * competing with the numbers below.
 */
function TodayStrip({ count, total, top, onPrint, onRecord }: {
  count: number;
  total: number;
  top: { merchant: string; cents: number } | null;
  onPrint: () => void;
  onRecord: () => void;
}) {
  const { now } = useLedger();
  const sentence =
    count === 0
      ? s("hero.empty")
      : (count === 1 ? s("hero.sentenceOne") : s("hero.sentence"))
          .replace("{count}", String(count))
          .replace("{total}", money(total))
          .replace("{merchant}", top?.merchant ?? s("common.none"))
          .replace("{amount}", money(top?.cents ?? 0));

  return (
    <section className="relative flex min-w-0 flex-col gap-5 overflow-hidden rounded-2xl bg-d-hero px-5 py-5 text-d-hero-ink sm:flex-row sm:items-center sm:px-6 sm:py-5">
      <div className="min-w-0 flex-1">
        <p className="flex items-center gap-2 text-[11px] font-semibold uppercase tracking-[0.08em] text-d-hero-ink-2">
          <span aria-hidden className="size-1.5 rounded-full bg-d-hero-ink" />
          {s("hero.kicker")}
          <span className="font-normal normal-case tracking-normal">{dayLabel(now)}</span>
        </p>
        <p className="mt-2 text-[16px] font-semibold leading-[24px] tracking-[-0.01em]">{sentence}</p>
        <div className="mt-3.5 flex flex-wrap items-center gap-2">
          <Button variant="primary" size="sm" icon={Printer} onClick={onPrint} className="bg-d-hero-ink text-d-hero hover:bg-d-hero-ink/90">
            {s("hero.print")}
          </Button>
          <Button size="sm" onClick={onRecord} className="bg-d-hero-ink/10 text-d-hero-ink hover:bg-d-hero-ink/18">
            {s("hero.record")}
          </Button>
          <span className="text-[11.5px] text-d-hero-ink-2">{s("hero.paperHint")}</span>
        </div>
      </div>
      <img
        src="/img/app-today.webp"
        alt=""
        aria-hidden
        loading="lazy"
        className="hidden h-[112px] w-[150px] shrink-0 rounded-xl object-cover sm:block"
      />
    </section>
  );
}

/** 近 30 天 / 本月的事实小结：只陈述，不评判，不给建议。 */
function SummaryFacts({ facts }: { facts: { top: string | null; topCents: number; share: number; vsPrevPct: number | null; vsPrevDir: "up" | "down" | "flat"; prevCents: number; peakDay: number | null; peakCents: number } }) {
  return (
    <motion.dl
      key={`${facts.top}-${facts.vsPrevPct}-${facts.peakDay}`}
      initial={{ opacity: 0, y: 6 }}
      animate={{ opacity: 1, y: 0 }}
      exit={{ opacity: 0, y: -6 }}
      transition={{ duration: 0.18, ease: [0.23, 1, 0.32, 1] }}
      className="space-y-3"
    >
      <div>
        <dt className="text-[11.5px] text-d-ink-3">{s("ov.topCategory")}</dt>
        <dd className="mt-0.5 flex items-baseline gap-2">
          <span className="text-[13px] font-medium text-d-ink">{facts.top ?? s("common.none")}</span>
          <span className="num text-[12px] text-d-ink-2">
            {money(facts.topCents)} · {facts.share}%
          </span>
        </dd>
      </div>
      <div>
        <dt className="text-[11.5px] text-d-ink-3">{s("ov.vsLastMonth")}</dt>
        <dd className="mt-0.5 text-[13px] font-medium text-d-ink">
          {facts.vsPrevPct === null ? s("ov.flat") : `${facts.vsPrevDir === "up" ? "▲" : "▼"} ${Math.abs(facts.vsPrevPct)}%`}
          <span className="ml-1.5 font-normal text-d-ink-3">{money(facts.prevCents)}</span>
        </dd>
      </div>
      <div>
        <dt className="text-[11.5px] text-d-ink-3">{s("ov.peakDay")}</dt>
        <dd className="mt-0.5 text-[13px] font-medium text-d-ink">
          {facts.peakDay ? dayLabel(facts.peakDay) : s("common.none")}
          {facts.peakDay && <span className="num ml-1.5 font-normal text-d-ink-3">{money(facts.peakCents)}</span>}
        </dd>
      </div>
    </motion.dl>
  );
}

export function OverviewPage() {
  useLocale();
  const { txns, now, isLoading, isError, refetch } = useLedger();
  const shell = useShell();
  const skeleton = useFirstPaint(isLoading);

  const data = useMemo(() => {
    const today = startOfDay(now);
    const monthStart = monthStartOf(now);
    const monthEnd = addDays(today, 1);
    const yesterdayStart = addDays(today, -1);
    const d = new Date(now);
    const prevMonthStart = new Date(d.getFullYear(), d.getMonth() - 1, 1).getTime();
    const prevMonthEnd = new Date(d.getFullYear(), d.getMonth() - 1, d.getDate() + 1).getTime();

    const todayOut = sumOut(txns, today, monthEnd);
    const yestOut = sumOut(txns, yesterdayStart, today);
    const monthOut = sumOut(txns, monthStart, monthEnd);
    const prevMonthOut = sumOut(txns, prevMonthStart, prevMonthEnd);
    const monthIn = txns.filter((t) => t.direction === "IN" && t.occurredAt >= monthStart && t.occurredAt < monthEnd).reduce((sum, t) => sum + t.amountCents, 0);
    const prevMonthIn = txns.filter((t) => t.direction === "IN" && t.occurredAt >= prevMonthStart && t.occurredAt < prevMonthEnd).reduce((sum, t) => sum + t.amountCents, 0);

    const todayTxns = txns.filter((t) => t.direction === "OUT" && t.occurredAt >= today && t.occurredAt < monthEnd);
    const topToday = todayTxns.reduce<{ merchant: string; cents: number } | null>(
      (best, t) => (best === null || t.amountCents > best.cents ? { merchant: t.merchant, cents: t.amountCents } : best),
      null,
    );

    const monthDays = new Set(txns.filter((t) => t.occurredAt >= monthStart && t.occurredAt < monthEnd).map((t) => startOfDay(t.occurredAt)));
    const trend30 = seriesOf("custom", { start: addDays(today, -29), end: addDays(today, 1) }, txns, null);

    const byCat = new Map<string, number>();
    for (const t of txns) {
      if (t.direction !== "OUT" || t.occurredAt < monthStart || t.occurredAt >= monthEnd) continue;
      byCat.set(t.category, (byCat.get(t.category) ?? 0) + t.amountCents);
    }
    const sorted = [...byCat.entries()].sort((a, b) => b[1] - a[1]);
    const top: Slice[] = sorted.slice(0, 5).map(([key, cents]) => ({ label: categoryLabel(key as keyof typeof CATEGORIES), value: cents }));
    const rest = sorted.slice(5).reduce((sum, [, cents]) => sum + cents, 0);
    if (rest > 0) top.push({ label: s("common.more"), value: rest, color: "muted" });
    const mixTotal = top.reduce((sum, x) => sum + x.value, 0);

    const recent = [...txns].sort((a, b) => b.occurredAt - a.occurredAt).slice(0, 10);
    const peak = peakDayOf(txns, { start: addDays(today, -29), end: addDays(today, 1) });
    const topCategory = sorted[0];

    return { todayTxns, topToday, todayOut, yestOut, monthOut, prevMonthOut, monthIn, prevMonthIn, monthDays, trend30, mix: top, mixTotal, recent, peak, topCategory };
  }, [txns, now]);

  const columns: Column<Txn>[] = [
    {
      key: "merchant",
      header: s("ledger.colMerchant"),
      cell: (t) => (
        <div className="flex min-w-0 items-center gap-2">
          <span aria-hidden className="grid size-5 shrink-0 place-items-center rounded bg-d-fill text-[11px]">
            {CATEGORIES[t.category].emoji}
          </span>
          <span className="truncate text-d-ink">{t.merchant}</span>
        </div>
      ),
    },
    {
      key: "time",
      header: s("ledger.colTime"),
      width: "150px",
      phoneHidden: true,
      cell: (t) => (
        <span className="text-[12px] text-d-ink-3">
          {relativeDay(startOfDay(t.occurredAt), now)} {hhmm(t.occurredAt)}
        </span>
      ),
    },
    {
      key: "amount",
      header: s("ledger.colAmount"),
      align: "right",
      width: "120px",
      cell: (t) => (
        <span className={t.direction === "IN" ? "num text-[13px] font-medium text-d-positive" : "num text-[13px] font-medium text-d-ink"}>
          {t.direction === "IN" ? `+${money(t.amountCents)}` : money(t.amountCents)}
        </span>
      ),
    },
  ];

  const vsPrev = delta(data.monthOut, data.prevMonthOut);
  const share = data.mixTotal > 0 ? Math.round(((data.mix[0]?.value ?? 0) / data.mixTotal) * 100) : 0;
  const facts = {
    top: data.topCategory ? categoryLabel(data.topCategory[0] as keyof typeof CATEGORIES) : null,
    topCents: data.mix[0]?.value ?? 0,
    share,
    vsPrevPct: vsPrev.pct,
    vsPrevDir: vsPrev.dir,
    prevCents: data.prevMonthOut,
    peakDay: data.peak?.dayStart ?? null,
    peakCents: data.peak?.cents ?? 0,
  };

  const skeletonGrid = (
    <div className="grid grid-cols-12 gap-5">
      <div className="col-span-12 grid grid-cols-2 gap-5 xl:grid-cols-4">
        {[0, 1, 2, 3].map((i) => (
          <div key={i} className="h-[104px] rounded-xl bg-d-surface" />
        ))}
      </div>
      <div className="col-span-12 xl:col-span-8">
        <Skeleton rows={6} />
      </div>
      <div className="col-span-12 xl:col-span-4">
        <Skeleton rows={4} />
      </div>
    </div>
  );

  return (
    <Loadable loading={skeleton} skeleton={skeletonGrid}>
      <div className="grid grid-cols-12 gap-5">
        <div className="col-span-12">
          <TodayStrip
            count={data.todayTxns.length}
            total={data.todayOut}
            top={data.topToday}
            onPrint={() => shell.openPrint()}
            onRecord={() => shell.openEntry()}
          />
        </div>

        <div className="col-span-12 grid grid-cols-2 gap-5 xl:grid-cols-4">
          <Stagger index={0}>
            <Kpi label={s("ov.todayOut")} cents={data.todayOut} delta={delta(data.todayOut, data.yestOut)} vs={s("ov.vsYesterday")} />
          </Stagger>
          <Stagger index={1}>
            <Kpi label={s("ov.monthOut")} cents={data.monthOut} delta={delta(data.monthOut, data.prevMonthOut)} vs={s("ov.vsLastMonth")} />
          </Stagger>
          <Stagger index={2}>
            <Kpi label={s("ov.monthIn")} cents={data.monthIn} delta={delta(data.monthIn, data.prevMonthIn)} tone="income" vs={s("ov.vsLastMonth")} />
          </Stagger>
          <Stagger index={3}>
            <Kpi
              label={s("ov.days")}
              value={`${data.monthDays.size}`}
              suffix={s("ov.dayUnit")}
              hint={`${s("stats.avg")} ${money(data.monthDays.size > 0 ? Math.round(data.monthOut / data.monthDays.size) : 0)}`}
            />
          </Stagger>
        </div>

        <Stagger index={4} className="col-span-12 xl:col-span-8">
          <Card className="h-full" title={s("ov.trend")} action={<Hint>{s("ov.trendHint")}</Hint>}>
            {data.mixTotal === 0 && data.trend30.every((point) => point.value === 0) ? (
              <EmptyState
                art="/img/app-empty-ledger.jpg"
                title={s("ov.emptyTrendTitle")}
                hint={s("ov.emptyTrendHint")}
                action={
                  <Button variant="primary" size="sm" icon={Plus} onClick={() => shell.openEntry()}>
                    {s("hero.record")}
                  </Button>
                }
                className="py-6"
              />
            ) : (
              <AreaTrend points={data.trend30} compareName={s("stats.previous")} valueName={s("ov.trend")} height={236} interval={4} />
            )}
          </Card>
        </Stagger>

        <Stagger index={5} className="col-span-12 xl:col-span-4">
          <Card
            className="h-full"
            title={s("ov.summary")}
            action={
              <Button size="sm" variant="ghost" icon={Plus} onClick={() => shell.openEntry()}>
                {s("cm.record")}
              </Button>
            }
          >
            <div className="min-h-[132px]">
              <AnimatePresence mode="popLayout" initial={false}>
                {data.mixTotal === 0 ? (
                  <p key="empty" className="text-[13px] leading-[20px] text-d-ink-2">
                    {s("ov.summaryEmpty")}
                  </p>
                ) : (
                  <SummaryFacts key="facts" facts={facts} />
                )}
              </AnimatePresence>
            </div>
            <p className="mt-4 text-[11px] text-d-ink-3">
              {s("ov.updated")} {hhmm(now)}
            </p>
          </Card>
        </Stagger>

        <Stagger index={6} className="col-span-12 xl:col-span-7">
          <Card
            className="h-full"
            padded={false}
            title={s("ov.recent")}
            action={
              <Button size="sm" variant="ghost" onClick={() => go("/app/ledger")}>
                {s("ov.viewAll")}
              </Button>
            }
          >
            <DataTable
              rows={data.recent}
              columns={columns}
              rowKey={(t) => t.id}
              label={s("ov.recent")}
              onActivate={(t) => shell.openEntry(t)}
              query=""
              maxHeight="360px"
              empty={
                <EmptyState
                  art="/img/app-empty-ledger.jpg"
                  title={s("empty.ledgerTitle")}
                  hint={s("empty.ledgerHint")}
                  action={
                    <Button variant="primary" icon={Plus} onClick={() => shell.openEntry()}>
                      {s("cm.record")}
                    </Button>
                  }
                />
              }
            />
          </Card>
        </Stagger>

        <Stagger index={7} className="col-span-12 xl:col-span-5">
          <Card className="h-full" title={s("ov.mix")}>
            {data.mixTotal === 0 ? (
              <p className="py-6 text-center text-[13px] text-d-ink-3">{s("ov.mixEmpty")}</p>
            ) : (
              <div className="flex items-center gap-4">
                <Donut slices={data.mix} centerValue={money(data.mixTotal)} centerLabel={s("stats.total")} size={168} />
                <Legend slices={data.mix} total={data.mixTotal} />
              </div>
            )}
          </Card>
        </Stagger>
      </div>
    </Loadable>
  );
}
