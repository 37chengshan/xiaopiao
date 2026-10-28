import { XButton, SectionHeader } from "@/components/app/kit";
import type { Insights } from "../logic/aggregator";
import { BarList, pct } from "./Bars";
import { Heatmap } from "./Heatmap";

function Kpi({ label, value, unit, sub, className }: { label: string; value: string; unit?: string; sub: string; className?: string }) {
  return (
    <div className={`px-4 py-4 ${className ?? ""}`}>
      <p className="text-[13px] leading-[18px] text-ink-2">{label}</p>
      <p className="num mt-1 text-[32px] font-semibold leading-[38px] tracking-[-0.03em] text-ink">
        {value}
        {unit && <span className="ml-0.5 text-[15px] font-normal tracking-normal text-ink-2">{unit}</span>}
      </p>
      <p className="mt-0.5 text-[11px] leading-[14px] text-ink-3">{sub}</p>
    </div>
  );
}

export function InsightsSkeleton() {
  return (
    <div role="status" aria-live="polite" aria-label="正在整理匿名汇总" className="space-y-4 px-4 pt-3">
      <div className="grid grid-cols-2 gap-px overflow-hidden rounded-[14px] bg-surface">
        {[0, 1, 2, 3].map((i) => (
          <div key={i} className="space-y-2 p-4">
            <div className="h-3 w-1/2 animate-pulse rounded bg-surface-2" />
            <div className="h-8 w-3/4 animate-pulse rounded bg-surface-2" />
          </div>
        ))}
      </div>
      <div className="h-64 animate-pulse rounded-[14px] bg-surface" />
      <p className="text-center text-[13px] text-ink-2">正在整理匿名汇总…</p>
    </div>
  );
}

export function InsightsStep({ insights, onNext }: { insights: Insights; onNext: () => void }) {
  const share = insights.drinkBuyers / insights.crowdSize;
  return (
    <div className="pb-6">
      <p className="px-4 pt-1 text-[13px] leading-[18px] text-ink-2">愿意匿名分享的顾客汇总，帮你先看清楚谁在什么时候来。</p>

      <div className="mx-4 mt-4 grid grid-cols-2 overflow-hidden rounded-[14px] bg-surface">
        <Kpi label="愿意分享的顾客" value={`${insights.crowdSize}`} unit="人" sub="自愿 · 匿名" className="border-b border-r border-hairline" />
        <Kpi label="买过饮品" value={`${insights.drinkBuyers}`} unit="人" sub={`占 ${pct(share)}`} className="border-b border-hairline" />
        <Kpi label="复购率" value={pct(insights.repeatRate)} sub="买过饮品、近期 ≥3 杯" className="border-r border-hairline" />
        <Kpi label="最热时段" value={insights.peak?.label ?? "—"} sub="饮品购买最集中" />
      </div>

      <SectionHeader>什么时候最忙</SectionHeader>
      <Heatmap grid={insights.heatmap} k={insights.k} peak={insights.peak} />

      <SectionHeader>价格带 · 饮品客单价</SectionHeader>
      <BarList cells={insights.priceBands} highlightTop ariaLabel="价格带分布" />

      <SectionHeader>小料偏好</SectionHeader>
      <BarList cells={insights.toppings} ariaLabel="小料偏好" />

      <SectionHeader>顾客还买什么</SectionHeader>
      <BarList cells={insights.categoryShare.slice(0, 5)} ariaLabel="顾客还买什么" />

      <p className="px-4 pt-3 text-[11px] leading-[16px] text-ink-3">
        {insights.suppressedCells > 0 ? `已隐藏 ${insights.suppressedCells} 个样本不足的格子 · ` : ""}
        所有数字均为模拟的演示数据。
      </p>

      <div className="px-4 pt-5">
        <XButton variant="primary" className="w-full" onClick={onNext}>
          下一步：发起一个小实验
        </XButton>
      </div>
    </div>
  );
}
