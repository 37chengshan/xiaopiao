import { animate, useReducedMotion } from "framer-motion";
import { Search } from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import { CATEGORY_ICONS } from "@/components/app/categoryIcons";
import { Footnote, Group, IconTile, Money, Row, SectionHeader, Segmented } from "@/components/app/kit";
import { EmptyState } from "@/components/app/States";
import { cn } from "@/lib/utils";
import { DAY_MS, type Subscription, type Txn, startOfDay, yuan } from "../../domain/types";
import { categorize } from "../../logic/categorize";
import { CANCEL_GUIDES, type CancelPlatform, annualCents, cycleLabel, detectSubscriptions } from "../../logic/subscriptions";
import { dayLabel } from "./util";

function useCountUp(target: number): number {
  const reduce = useReducedMotion();
  const [v, setV] = useState(reduce ? target : 0);
  useEffect(() => {
    if (reduce) {
      setV(target);
      return;
    }
    const c = animate(0, target, { duration: 0.6, ease: [0.23, 1, 0.32, 1], onUpdate: (x) => setV(Math.round(x)) });
    return () => c.stop();
  }, [target, reduce]);
  return v;
}

function countdown(nextChargeAt: number, now: number): string {
  const days = Math.round((startOfDay(nextChargeAt) - startOfDay(now)) / DAY_MS);
  if (days <= 0) return days === 0 ? "今天扣款" : "近期扣款";
  return days === 1 ? "明天扣款" : `${days} 天后扣款`;
}

function CancelGuide({ merchant }: { merchant: string }) {
  const [platform, setPlatform] = useState<CancelPlatform>("alipay");
  const platforms = Object.keys(CANCEL_GUIDES) as CancelPlatform[];
  return (
    <div className="px-4 pb-4 pt-1">
      <p className="mb-2 text-[13px] leading-[18px] text-ink-2">怎么取消「{merchant}」：仅为操作指引，请在对应 App 里自己完成。</p>
      <Segmented value={platform} onChange={setPlatform} options={platforms.map((p) => ({ value: p, label: CANCEL_GUIDES[p].name }))} />
      <ol className="mt-3 space-y-2.5">
        {CANCEL_GUIDES[platform].steps.map((step, i) => (
          <li key={step} className="flex gap-3 text-[15px] leading-[22px] text-ink">
            <span className="num grid size-[22px] shrink-0 place-items-center rounded-full bg-fill text-[12px] font-medium text-ink-2">{i + 1}</span>
            {step}
          </li>
        ))}
      </ol>
    </div>
  );
}

export function SubscriptionDetective({ txns, now }: { txns: readonly Txn[]; now: number }) {
  const subs = useMemo(() => detectSubscriptions(txns, now), [txns, now]);
  const yearly = subs.reduce((s, x) => s + annualCents(x), 0);
  const rolled = useCountUp(yearly);
  const [open, setOpen] = useState<string | null>(null);

  if (subs.length === 0) {
    return <EmptyState icon={Search} title="没发现自动续费" hint="同一家、金额差不多、按周/月/季/年规律出现的扣款，我会帮你揪出来" />;
  }

  return (
    <div>
      <div className="px-8 pt-5">
        <p className="text-[13px] leading-[18px] text-ink-2">发现 {subs.length} 项自动续费，一年大约会花掉</p>
        <Money cents={rolled} className="mt-1 block text-[48px] font-semibold leading-[52px] tracking-[-0.03em] text-ink" />
      </div>

      <SectionHeader>订阅</SectionHeader>
      <Group inset="icon">
        {subs.map((s: Subscription) => {
          const cat = categorize(s.merchant);
          const expanded = open === s.merchant;
          return (
            <div key={s.merchant}>
              <Row
                leading={<IconTile icon={CATEGORY_ICONS[cat === "OTHER" ? "SUBSCRIPTION" : cat]} />}
                title={s.merchant}
                subtitle={`${cycleLabel(s.cycleDays)} ¥${yuan(s.amountCents)} · ${countdown(s.nextChargeAt, now)}（${dayLabel(startOfDay(s.nextChargeAt))}）`}
                trailing={
                  <span className="flex flex-col items-end leading-tight">
                    <Money cents={annualCents(s)} />
                    <span className="text-[11px] text-ink-2">每年</span>
                  </span>
                }
                onClick={() => setOpen(expanded ? null : s.merchant)}
              />
              <div className={cn("grid transition-[grid-template-rows,opacity] duration-200 ease-[var(--ease-out)]", expanded ? "grid-rows-[1fr] opacity-100" : "grid-rows-[0fr] opacity-0")} aria-hidden={!expanded}>
                <div className="overflow-hidden">{expanded && <CancelGuide merchant={s.merchant} />}</div>
              </div>
            </div>
          );
        })}
      </Group>
      <Footnote>点一行看「怎么取消」。识别规则：同一商家、金额浮动在 ±10% 内、间隔接近每周/月/季/年。小票不会替你取消任何订阅。</Footnote>
    </div>
  );
}
