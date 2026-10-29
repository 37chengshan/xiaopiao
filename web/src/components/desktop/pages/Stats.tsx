import { categoryLabel } from "../labels";
import { ChevronLeft, ChevronRight, Plus, RefreshCw } from "lucide-react";
import { useLocale } from "@/lib/strings";
import { motion } from "framer-motion";
import { useMemo, useState } from "react";
import { CATEGORIES, startOfDay } from "@/domain/types";
import { dateKey } from "@/logic/time";
import { useLedger } from "@/services/LedgerProvider";
import { AreaTrend, BarTrend, Donut, Legend, type Slice } from "../charts";
import { delta, money } from "../format";
import { useFirstPaint } from "../hooks";
import { Stagger } from "../motion";
import { previousWindow, seriesOf, shiftAnchor, summarize, windowLabel, windowOf, type CustomRange, type Period } from "../statsModel";
import { Button, Card, EmptyState, Kpi, Segmented, Skeleton, Toggle } from "../ui";
import { s } from "../strings";
import { useShell } from "../Shell";

const PERIODS: { value: Period; label: () => string }[] = [
  { value: "week", label: () => s("stats.week") },
  { value: "month", label: () => s("stats.month") },
  { value: "year", label: () => s("stats.year") },
  { value: "custom", label: () => s("stats.custom") },
];

export function StatsPage() {
  useLocale();
  const { txns, now, isLoading, isError, refetch } = useLedger();
  const shell = useShell();
  const skeleton = useFirstPaint(isLoading);

  const [period, setPeriod] = useState<Period>("month");
  const [anchor, setAnchor] = useState(() => startOfDay(Date.now()));
  const [custom, setCustom] = useState<CustomRange>(() => ({ from: startOfDay(Date.now()) - 6 * 86400000, to: startOfDay(Date.now()) }));
  const [compare, setCompare] = useState(false);

  const view = useMemo(() => {
    const win = windowOf(period, anchor, custom);
    const prevWin = compare ? previousWindow(period, anchor, custom) : null;
    const summary = summarize(txns, win);
    const prevSummary = prevWin ? summarize(txns, prevWin) : null;
    const points = seriesOf(period, win, txns, prevWin);
    const mix: Slice[] = summary.byCategory.slice(0, 5).map((c) => ({ label: categoryLabel(c.category), value: c.cents }));
    const rest = summary.byCategory.slice(5).reduce((sum, c) => sum + c.cents, 0);
    if (rest > 0) mix.push({ label: s("common.more"), value: rest, color: "muted" });
    return { win, prevWin, summary, prevSummary, points, mix, label: windowLabel(period, anchor, custom) };
  }, [period, anchor, custom, compare, txns]);

  const today = startOfDay(now);
  // A custom range is fixed by its two dates, so stepping it is meaningless (and `windowOf` ignores the anchor).
  const steppable = period !== "custom";
  const nextAnchor = steppable ? shiftAnchor(period, anchor, 1, custom) : anchor;
  const canNext = steppable && windowOf(period, nextAnchor, custom).start <= today;

  return (
    <div className="grid grid-cols-12 gap-5">
      <div className="col-span-12 flex flex-wrap items-center gap-2">
        <Segmented value={period} onChange={setPeriod} label={s("stats.trend")} options={PERIODS.map((p) => ({ value: p.value, label: p.label() }))} />
        <div className="flex items-center gap-1">
          {steppable && (
            <button
              type="button"
              aria-label={s("stats.prevPeriod")}
              onClick={() => setAnchor(shiftAnchor(period, anchor, -1, custom))}
              className="d-press grid size-7 place-items-center rounded-md text-d-ink-2 hover:bg-d-fill hover:text-d-ink"
            >
              <ChevronLeft className="size-4" strokeWidth={2} aria-hidden />
            </button>
          )}
          <span className="min-w-[164px] text-center text-[12.5px] font-medium text-d-ink">{view.label}</span>
          {steppable && (
            <button
              type="button"
              aria-label={s("stats.nextPeriod")}
              disabled={!canNext}
              onClick={() => canNext && setAnchor(nextAnchor)}
              className="d-press grid size-7 place-items-center rounded-md text-d-ink-2 hover:bg-d-fill hover:text-d-ink disabled:opacity-30"
            >
              <ChevronRight className="size-4" strokeWidth={2} aria-hidden />
            </button>
          )}
        </div>

        {period === "custom" && (
          <div className="flex items-center gap-1.5">
            <input
              type="date"
              aria-label={s("stats.from")}
              value={dateKey(custom.from)}
              max={dateKey(today)}
              onChange={(e) => setCustom((c) => ({ ...c, from: new Date(`${e.target.value}T00:00:00`).getTime() }))}
              className="h-7 rounded-md bg-d-fill px-2 text-[12px] text-d-ink outline-none"
            />
            <span className="text-d-ink-3">–</span>
            <input
              type="date"
              aria-label={s("stats.to")}
              value={dateKey(custom.to)}
              max={dateKey(today)}
              onChange={(e) => setCustom((c) => ({ ...c, to: new Date(`${e.target.value}T00:00:00`).getTime() }))}
              className="h-7 rounded-md bg-d-fill px-2 text-[12px] text-d-ink outline-none"
            />
          </div>
        )}

        <div className="min-w-0 flex-1" />

        <label className="flex items-center gap-2 text-[12px] text-d-ink-2">
          {s("stats.compare")}
          <Toggle checked={compare} onChange={setCompare} label={s("stats.compare")} />
        </label>
      </div>

      {skeleton ? (
        <>
          <div className="col-span-12 xl:col-span-8">
            <Skeleton rows={7} />
          </div>
          <div className="col-span-12 xl:col-span-4">
            <Skeleton rows={4} />
          </div>
        </>
      ) : (
        <>
          <Stagger index={0} className="col-span-12 xl:col-span-8">
            <Card className="h-full" title={s("stats.trend")} action={compare ? <span className="text-[11px] text-d-ink-3">{s("stats.previous")}</span> : null}>
              {view.summary.totalCents === 0 && view.summary.count === 0 ? (
                <EmptyState
                  art="/img/app-empty-ledger.jpg"
                  title={s("stats.empty")}
                  hint={s("stats.emptyHint")}
                  action={
                    <Button variant="primary" icon={Plus} onClick={() => shell.openEntry()}>
                      {s("stats.emptyAction")}
                    </Button>
                  }
                />
              ) : (
                <motion.div key={`${period}-${view.label}-${compare}`} initial={{ opacity: 0 }} animate={{ opacity: 1 }} transition={{ duration: 0.16 }}>
                  {period === "week" ? (
                    <BarTrend points={view.points} valueName={s("stats.period")} compareName={s("stats.previous")} height={248} />
                  ) : (
                    <AreaTrend points={view.points} valueName={s("stats.period")} compareName={s("stats.previous")} height={248} />
                  )}
                </motion.div>
              )}
            </Card>
          </Stagger>

          <div className="col-span-12 grid grid-cols-2 gap-5 xl:col-span-4">
            <Stagger index={1}>
              <Kpi label={s("stats.total")} cents={view.summary.totalCents} delta={view.prevSummary ? delta(view.summary.totalCents, view.prevSummary.totalCents) : undefined} vs={view.prevSummary ? s("stats.previous") : undefined} />
            </Stagger>
            <Stagger index={2}>
              <Kpi label={s("stats.avg")} cents={view.summary.avgPerDayCents} hint={`${view.summary.activeDays} ${s("ov.dayUnit")}`} />
            </Stagger>
            <Stagger index={3}>
              <Kpi label={s("stats.count")} value={`${view.summary.count}`} hint={s("ledger.itemUnit")} />
            </Stagger>
            <Stagger index={4}>
              <Kpi label={s("stats.top")} cents={view.summary.peak?.amountCents ?? 0} hint={view.summary.peak?.merchant} />
            </Stagger>
          </div>

          <Stagger index={5} className="col-span-12 xl:col-span-4">
          <Card className="h-full" title={s("stats.mix")}>
            {view.summary.totalCents === 0 ? (
              <EmptyState art="/img/app-empty-shop.jpg" title={s("stats.empty")} className="py-4" />
            ) : (
              <div className="flex flex-col items-center gap-3">
                <Donut slices={view.mix} size={172} centerValue={money(view.summary.totalCents)} centerLabel={s("stats.total")} />
                <Legend slices={view.mix} total={view.summary.totalCents} className="w-full" />
              </div>
            )}
          </Card>
          </Stagger>

          <Stagger index={6} className="col-span-12 xl:col-span-8">
          <Card className="h-full" title={s("stats.ranking")}>
            {view.summary.byCategory.length === 0 ? (
              <EmptyState art="/img/app-empty-shop.jpg" title={s("stats.empty")} className="py-4" />
            ) : (
              <ul className="space-y-2.5">
                {view.summary.byCategory.map((row) => {
                  const share = view.summary.totalCents > 0 ? row.cents / view.summary.totalCents : 0;
                  return (
                    <li key={row.category} className="flex items-center gap-3">
                      <span className="w-[92px] shrink-0 truncate text-[12.5px] text-d-ink-2">{categoryLabel(row.category)}</span>
                      <span className="h-[3px] min-w-0 flex-1 overflow-hidden rounded-full bg-d-fill">
                        <span className="block h-full rounded-full bg-d-accent" style={{ width: `${Math.max(2, share * 100)}%` }} />
                      </span>
                      <span className="num w-[92px] shrink-0 text-right text-[12.5px] text-d-ink">{money(row.cents)}</span>
                      <span className="num w-10 shrink-0 text-right text-[11.5px] text-d-ink-3">{Math.round(share * 100)}%</span>
                    </li>
                  );
                })}
              </ul>
            )}
          </Card>
          </Stagger>
        </>
      )}
    </div>
  );
}
