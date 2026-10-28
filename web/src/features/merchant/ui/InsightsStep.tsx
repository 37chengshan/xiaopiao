import { motion, useReducedMotion } from "framer-motion";
import { Skeleton } from "@/components/ui/skeleton";
import type { HeatCell, Insights, ShareCell } from "../logic/aggregator";
import { SLOT_LABELS, WEEKDAY_LABELS } from "../logic/crowd";
import { softSpring, tokens } from "../tokens";
import { Card, DemoPill, SectionTitle } from "./common";

const pct = (v: number | null) => (v === null ? "—" : `${Math.round(v * 100)}%`);

function Kpi({ label, value, sub }: { label: string; value: string; sub?: string }) {
  return (
    <div className="rounded-2xl border border-(--mb-line) bg-(--mb-card) p-3">
      <p className="text-[11px] text-(--mb-ink-soft)">{label}</p>
      <p className="mt-1 text-[22px] font-extrabold leading-none tracking-tight">{value}</p>
      {sub ? <p className="mt-1 text-[11px] text-(--mb-ink-soft)">{sub}</p> : null}
    </div>
  );
}

function Heatmap({ grid, k }: { grid: HeatCell[][]; k: number }) {
  return (
    <div role="table" aria-label="饮品购买时段热力图" className="text-[10px]">
      <div role="row" className="grid grid-cols-[2rem_repeat(6,1fr)] gap-1 pb-1 text-center text-(--mb-ink-soft)">
        <span role="columnheader" className="sr-only">
          星期
        </span>
        {SLOT_LABELS.map((s) => (
          <span key={s} role="columnheader">
            {s}
          </span>
        ))}
      </div>
      {grid.map((row, wd) => (
        <div key={wd} role="row" className="mb-1 grid grid-cols-[2rem_repeat(6,1fr)] gap-1">
          <span role="rowheader" className="self-center text-(--mb-ink-soft)">
            {WEEKDAY_LABELS[wd]}
          </span>
          {row.map((c) => (
            <motion.div
              key={c.slot}
              role="cell"
              initial={{ opacity: 0, scale: 0.8 }}
              animate={{ opacity: 1, scale: 1 }}
              transition={{ ...softSpring, delay: (wd + c.slot) * 0.015 }}
              aria-label={
                c.suppressed
                  ? `${WEEKDAY_LABELS[wd]}${SLOT_LABELS[c.slot]}：样本不足`
                  : `${WEEKDAY_LABELS[wd]}${SLOT_LABELS[c.slot]}：${c.people} 人`
              }
              className="grid h-8 place-items-center rounded-md text-[9px] font-semibold"
              style={
                c.suppressed
                  ? {
                      background:
                        "repeating-linear-gradient(135deg, oklch(0.93 0.004 90) 0 4px, oklch(0.96 0.004 90) 4px 8px)",
                      color: tokens.inkSoft,
                    }
                  : {
                      background: `color-mix(in oklch, ${tokens.coral} ${Math.round(18 + (c.level ?? 0) * 82)}%, ${tokens.coralSoft})`,
                      color: (c.level ?? 0) > 0.55 ? "white" : tokens.ink,
                    }
              }
            >
              {c.suppressed ? "样本不足" : c.people}
            </motion.div>
          ))}
        </div>
      ))}
      <p className="mt-2 text-[11px] text-(--mb-ink-soft)">
        格内数字 = 当时买过饮品的人数；少于 {k} 人的格子不显示。
      </p>
    </div>
  );
}

function ShareBars({ cells }: { cells: ShareCell[] }) {
  return (
    <ul className="space-y-2.5">
      {cells.map((c, i) => (
        <li key={c.key}>
          <div className="mb-1 flex items-center justify-between text-[12px]">
            <span className="font-semibold">
              {c.emoji ? `${c.emoji} ` : ""}
              {c.label}
            </span>
            <span className="text-(--mb-ink-soft)">{c.suppressed ? "样本不足" : pct(c.share)}</span>
          </div>
          <div className="h-2.5 overflow-hidden rounded-full bg-(--mb-canvas)">
            {c.suppressed ? null : (
              <motion.div
                className="h-full rounded-full bg-(--mb-coral)"
                initial={{ width: 0 }}
                animate={{ width: `${(c.share ?? 0) * 100}%` }}
                transition={{ ...softSpring, delay: i * 0.06 }}
              />
            )}
          </div>
        </li>
      ))}
    </ul>
  );
}

const DONUT_COLORS = [tokens.grape, tokens.coral, tokens.mint, tokens.butter, tokens.sky];

function Donut({ cells }: { cells: ShareCell[] }) {
  const reduce = useReducedMotion();
  const visible = cells.filter((c) => !c.suppressed && c.share);
  const r = 40;
  const circ = 2 * Math.PI * r;
  let acc = 0;
  return (
    <div className="flex items-center gap-4">
      <svg viewBox="0 0 100 100" className="size-28 shrink-0 -rotate-90" role="img" aria-label="小料偏好占比环形图">
        <circle cx="50" cy="50" r={r} fill="none" stroke={tokens.hairline} strokeWidth="14" />
        {visible.map((c, i) => {
          const len = (c.share ?? 0) * circ;
          const offset = -acc;
          acc += len;
          return (
            <motion.circle
              key={c.key}
              cx="50"
              cy="50"
              r={r}
              fill="none"
              stroke={DONUT_COLORS[i % DONUT_COLORS.length]}
              strokeWidth="14"
              strokeDasharray={`${Math.max(0, len - 1.5)} ${circ}`}
              strokeDashoffset={offset}
              initial={reduce ? false : { opacity: 0, strokeWidth: 4 }}
              animate={{ opacity: 1, strokeWidth: 14 }}
              transition={{ ...softSpring, delay: i * 0.08 }}
            />
          );
        })}
      </svg>
      <ul className="min-w-0 flex-1 space-y-1.5 text-[12px]">
        {cells.map((c, i) => (
          <li key={c.key} className="flex items-center justify-between gap-2">
            <span className="flex items-center gap-1.5 truncate">
              <span
                aria-hidden
                className="size-2.5 shrink-0 rounded-full"
                style={{ background: c.suppressed ? tokens.hairline : DONUT_COLORS[visible.indexOf(c) % DONUT_COLORS.length] }}
              />
              {c.label}
            </span>
            <span className="text-(--mb-ink-soft)">{c.suppressed ? "样本不足" : pct(c.share)}</span>
          </li>
        ))}
      </ul>
    </div>
  );
}

export function InsightsSkeleton() {
  return (
    <div className="space-y-3" role="status" aria-live="polite" aria-label="正在整理匿名汇总">
      <div className="grid grid-cols-2 gap-3">
        {[0, 1, 2, 3].map((i) => (
          <Skeleton key={i} className="h-[84px] rounded-2xl" />
        ))}
      </div>
      <Skeleton className="h-64 rounded-2xl" />
      <Skeleton className="h-40 rounded-2xl" />
      <p className="text-center text-[12px] text-(--mb-ink-soft)">正在整理匿名汇总…</p>
    </div>
  );
}

export function InsightsStep({ insights }: { insights: Insights }) {
  return (
    <div className="space-y-3">
      <Card className="bg-(--mb-coral-soft) border-transparent">
        <div className="flex items-center justify-between gap-2">
          <p className="text-[13px] font-bold">楼下的芋泥奶茶铺 · 人群洞察</p>
          <DemoPill />
        </div>
        <p className="mt-1 text-[12px] leading-relaxed text-(--mb-ink-soft)">
          下面是愿意匿名分享的顾客汇总，帮你先看清楚谁在什么时候来。
        </p>
      </Card>

      <div className="grid grid-cols-2 gap-3">
        <Kpi label="愿意分享的顾客" value={`${insights.crowdSize} 人`} sub="自愿 · 匿名" />
        <Kpi label="买过饮品" value={`${insights.drinkBuyers} 人`} sub={`占 ${pct(insights.drinkBuyers / insights.crowdSize)}`} />
        <Kpi label="复购率" value={pct(insights.repeatRate)} sub="近期买 ≥3 杯的比例" />
        <Kpi label="最热时段" value={insights.peak?.label ?? "—"} sub="饮品购买最集中" />
      </div>

      <Card>
        <SectionTitle hint={`k ≥ ${insights.k}`}>什么时候最忙</SectionTitle>
        <Heatmap grid={insights.heatmap} k={insights.k} />
      </Card>

      <Card>
        <SectionTitle hint="饮品客单价">价格带分布</SectionTitle>
        <ShareBars cells={insights.priceBands} />
      </Card>

      <Card>
        <SectionTitle hint="加料偏好">小料人气榜</SectionTitle>
        <Donut cells={insights.toppings} />
      </Card>

      <Card>
        <SectionTitle hint="全品类">顾客还买什么</SectionTitle>
        <ShareBars cells={insights.categoryShare.slice(0, 5)} />
      </Card>
      <p className="px-1 text-center text-[11px] text-(--mb-ink-soft)">
        {insights.suppressedCells > 0 ? `已隐藏 ${insights.suppressedCells} 个样本不足的格子 · ` : ""}
        所有数字均为演示数据
      </p>
    </div>
  );
}
