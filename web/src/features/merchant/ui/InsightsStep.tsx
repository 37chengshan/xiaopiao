import { Users } from "lucide-react";
import { XButton, SectionHeader } from "@/components/app/kit";
import { EmptyState } from "@/components/app/States";
import { copy } from "../copy";
import type { Insights } from "../logic/aggregator";
import { BarList, pct } from "./Bars";
import { Heatmap } from "./Heatmap";
import { Meter, Ring } from "./Visuals";

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
  const t = copy.insights;
  if (insights.crowdSize === 0) {
    return <EmptyState icon={Users} title={t.emptyTitle} hint={t.emptyHint} />;
  }
  const share = insights.drinkBuyers / insights.crowdSize;
  return (
    <div className="pb-6">
      <div className="mx-4 mt-2 overflow-hidden rounded-[18px] bg-gradient-to-b from-surface to-surface-2/60 shadow-[0_1px_0_var(--hairline)_inset,0_8px_24px_-16px_oklch(0_0_0/20%)] ring-1 ring-hairline">
        <div className="px-5 pb-4 pt-4">
          <p className="text-[13px] leading-[18px] text-ink-2">{t.crowd}</p>
          <p className="num mt-0.5 text-[48px] font-semibold leading-[52px] tracking-[-0.035em] text-ink">
            {insights.crowdSize}
            <span className="ml-1 text-[17px] font-normal tracking-normal text-ink-2">{t.people}</span>
          </p>
          <div className="mt-3">
            <Meter value={share} label={`${t.drinkBuyers} ${insights.drinkBuyers} ${t.people}，${pct(share)}`} />
            <p className="num mt-1.5 flex justify-between text-[13px] leading-[18px] text-ink-2">
              <span>
                {t.drinkBuyers} {insights.drinkBuyers}
              </span>
              <span>{pct(share)}</span>
            </p>
          </div>
        </div>
        <div className="grid grid-cols-2 border-t border-hairline">
          <div className="flex items-center gap-3 border-r border-hairline px-5 py-3.5">
            <Ring value={insights.repeatRate ?? 0} size={44} label={`${t.repeat} ${pct(insights.repeatRate)}`} />
            <div className="min-w-0">
              <p className="num text-[22px] font-semibold leading-[26px] tracking-[-0.02em] text-ink">{pct(insights.repeatRate)}</p>
              <p className="text-[12px] leading-[16px] text-ink-2">{t.repeat}</p>
            </div>
          </div>
          <div className="min-w-0 px-5 py-3.5">
            <p className="truncate text-[22px] font-semibold leading-[26px] tracking-[-0.02em] text-ink">{insights.peak?.label ?? "—"}</p>
            <p className="text-[12px] leading-[16px] text-ink-2">{t.peak}</p>
          </div>
        </div>
      </div>

      <SectionHeader>{t.busy}</SectionHeader>
      <Heatmap grid={insights.heatmap} k={insights.k} peak={insights.peak} />

      <SectionHeader>{t.price}</SectionHeader>
      <BarList cells={insights.priceBands} highlightTop ariaLabel={t.price} />

      <SectionHeader>{t.toppings}</SectionHeader>
      <BarList cells={insights.toppings} ariaLabel={t.toppings} />

      <SectionHeader>{t.alsoBuy}</SectionHeader>
      <BarList cells={insights.categoryShare.slice(0, 5)} ariaLabel={t.alsoBuy} />

      <div className="px-4 pt-5">
        <XButton variant="primary" className="w-full" onClick={onNext}>
          {t.next}
        </XButton>
      </div>
    </div>
  );
}
