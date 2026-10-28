import { useMemo } from "react";
import { AlertTriangle, Plus } from "lucide-react";
import { motion } from "framer-motion";
import { CATEGORIES, startOfDay } from "@/domain/types";
import { addDays, hhmm } from "@/logic/time";
import { useLedger } from "@/services/LedgerProvider";
import { AreaTrend, Donut, Legend, type Slice } from "../charts";
import { DataTable, type Column } from "../DataTable";
import { dayLabel, delta, money, relativeDay } from "../format";
import { useFirstPaint } from "../hooks";
import { go } from "../nav";
import { monthStartOf, peakDayOf, seriesOf, sumOut } from "../statsModel";
import { Button, Card, EmptyState, Hint, Kpi, Skeleton } from "../ui";
import { s } from "../strings";
import { useShell } from "../Shell";
import type { Txn } from "@/domain/types";

export function OverviewPage() {
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

    const monthDays = new Set(txns.filter((t) => t.occurredAt >= monthStart && t.occurredAt < monthEnd).map((t) => startOfDay(t.occurredAt)));
    const trend30 = seriesOf("custom", { start: addDays(today, -29), end: addDays(today, 1) }, txns, null);

    // Category mix for the month, top five plus one folded slice.
    const byCat = new Map<string, number>();
    for (const t of txns) {
      if (t.direction !== "OUT" || t.occurredAt < monthStart || t.occurredAt >= monthEnd) continue;
      byCat.set(t.category, (byCat.get(t.category) ?? 0) + t.amountCents);
    }
    const sorted = [...byCat.entries()].sort((a, b) => b[1] - a[1]);
    const top: Slice[] = sorted.slice(0, 5).map(([key, cents]) => ({ label: CATEGORIES[key as keyof typeof CATEGORIES].label, value: cents }));
    const rest = sorted.slice(5).reduce((sum, [, cents]) => sum + cents, 0);
    if (rest > 0) top.push({ label: s("common.more"), value: rest, color: "muted" });
    const mixTotal = top.reduce((sum, x) => sum + x.value, 0);

    const recent = [...txns].sort((a, b) => b.occurredAt - a.occurredAt).slice(0, 10);
    const peak = peakDayOf(txns, { start: addDays(today, -29), end: addDays(today, 1) });
    const topCategory = sorted[0];

    return { today, todayOut, yestOut, monthOut, prevMonthOut, monthIn, prevMonthIn, monthDays, trend30, mix: top, mixTotal, recent, peak, topCategory };
  }, [txns, now]);

  if (isError) {
    return (
      <Card padded={false}>
        <EmptyState icon={AlertTriangle} title={s("common.error")} action={<Button variant="primary" onClick={refetch}>{s("common.retry")}</Button>} />
      </Card>
    );
  }

  const monthShare = data.mixTotal > 0 ? Math.round(((data.mix[0]?.value ?? 0) / data.mixTotal) * 100) : 0;

  const columns: Column<Txn>[] = [
    {
      key: "merchant",
      header: s("ledger.colMerchant"),
      cell: (t) => (
        <div className="flex min-w-0 items-center gap-2">
          <span aria-hidden className="grid size-5 shrink-0 place-items-center rounded bg-d-fill text-[11px]">{CATEGORIES[t.category].emoji}</span>
          <span className="truncate text-d-ink">{t.merchant}</span>
        </div>
      ),
    },
    {
      key: "time",
      header: s("ledger.colTime"),
      width: "150px",
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

  return (
    <div className="grid grid-cols-12 gap-5">
      {skeleton ? (
        <>
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
        </>
      ) : (
        <>
          <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} className="col-span-12 grid grid-cols-2 gap-5 xl:grid-cols-4">
            <Kpi label={s("ov.todayOut")} value={money(data.todayOut)} delta={delta(data.todayOut, data.yestOut)} vs={s("ov.vsYesterday")} />
            <Kpi label={s("ov.monthOut")} value={money(data.monthOut)} delta={delta(data.monthOut, data.prevMonthOut)} vs={s("ov.vsLastMonth")} />
            <Kpi label={s("ov.monthIn")} value={money(data.monthIn)} delta={delta(data.monthIn, data.prevMonthIn)} tone="income" vs={s("ov.vsLastMonth")} />
            <Kpi label={s("ov.days")} value={`${data.monthDays.size}`} hint={`${s("stats.avg")} ${money(data.monthDays.size > 0 ? Math.round(data.monthOut / data.monthDays.size) : 0)}`} />
          </motion.div>

          <Card className="col-span-12 xl:col-span-8" title={s("ov.trend")} action={<Hint>{s("ov.trendHint")}</Hint>}>
            <AreaTrend points={data.trend30} compareName={s("stats.previous")} valueName={s("ov.trend")} height={236} interval={4} />
          </Card>

          <Card
            className="col-span-12 xl:col-span-4"
            title={s("ov.summary")}
            action={
              <Button size="sm" variant="ghost" icon={Plus} onClick={() => shell.openEntry()}>
                {s("cm.record")}
              </Button>
            }
          >
            {data.mixTotal === 0 ? (
              <p className="text-[13px] leading-[20px] text-d-ink-2">{s("ov.summaryEmpty")}</p>
            ) : (
              <dl className="space-y-3">
                <div>
                  <dt className="text-[11.5px] text-d-ink-3">{s("ov.topCategory")}</dt>
                  <dd className="mt-0.5 flex items-baseline gap-2">
                    <span className="text-[13px] font-medium text-d-ink">{data.topCategory ? CATEGORIES[data.topCategory[0] as keyof typeof CATEGORIES].label : s("common.none")}</span>
                    <span className="num text-[12px] text-d-ink-2">{money(data.mix[0]?.value ?? 0)} · {monthShare}%</span>
                  </dd>
                </div>
                <div>
                  <dt className="text-[11.5px] text-d-ink-3">{s("ov.vsLastMonth")}</dt>
                  <dd className="mt-0.5 text-[13px] font-medium text-d-ink">
                    {delta(data.monthOut, data.prevMonthOut).pct === null
                      ? s("ov.flat")
                      : `${delta(data.monthOut, data.prevMonthOut).dir === "up" ? "▲" : "▼"} ${Math.abs(delta(data.monthOut, data.prevMonthOut).pct ?? 0)}%`}
                    <span className="ml-1.5 font-normal text-d-ink-3">{money(data.prevMonthOut)}</span>
                  </dd>
                </div>
                <div>
                  <dt className="text-[11.5px] text-d-ink-3">{s("ov.peakDay")}</dt>
                  <dd className="mt-0.5 text-[13px] font-medium text-d-ink">
                    {data.peak ? dayLabel(data.peak.dayStart) : s("common.none")}
                    {data.peak && <span className="num ml-1.5 font-normal text-d-ink-3">{money(data.peak.cents)}</span>}
                  </dd>
                </div>
              </dl>
            )}
            <p className="mt-4 text-[11px] text-d-ink-3">
              {s("ov.updated")} {hhmm(now)}
            </p>
          </Card>

          <Card className="col-span-12 xl:col-span-7" padded={false} title={s("ov.recent")} action={<Button size="sm" variant="ghost" onClick={() => go("/app/ledger")}>{s("ov.viewAll")}</Button>}>
            <DataTable
              rows={data.recent}
              columns={columns}
              rowKey={(t) => t.id}
              label={s("ov.recent")}
              onActivate={(t) => shell.openEntry(t)}
              query=""
              maxHeight="360px"
              empty={<EmptyState icon={Plus} title={s("ledger.empty")} hint={s("ledger.emptyHint")} action={<Button variant="primary" icon={Plus} onClick={() => shell.openEntry()}>{s("cm.record")}</Button>} />}
            />
          </Card>

          <Card className="col-span-12 xl:col-span-5" title={s("ov.mix")}>
            {data.mixTotal === 0 ? (
              <p className="py-6 text-center text-[13px] text-d-ink-3">{s("ov.mixEmpty")}</p>
            ) : (
              <div className="flex items-center gap-4">
                <Donut slices={data.mix} centerValue={money(data.mixTotal)} centerLabel={s("stats.total")} size={168} />
                <Legend slices={data.mix} total={data.mixTotal} />
              </div>
            )}
          </Card>
        </>
      )}
    </div>
  );
}
