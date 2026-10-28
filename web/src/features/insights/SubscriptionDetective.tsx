import { animate } from "framer-motion";
import { useEffect, useMemo, useState } from "react";
import { Drawer, DrawerContent, DrawerDescription, DrawerTitle } from "@/components/ui/drawer";
import { cn } from "@/lib/utils";
import { CATEGORIES, type Subscription, type Txn, yuan } from "../../domain/types";
import { categorize } from "../../logic/categorize";
import { CANCEL_GUIDES, type CancelPlatform, annualCents, cycleLabel, detectSubscriptions } from "../../logic/subscriptions";
import { dayLabel, startOfDayOf } from "./util";

function useCountUp(target: number): number {
  const [v, setV] = useState(0);
  useEffect(() => {
    const c = animate(0, target, { duration: 1, ease: "easeOut", onUpdate: (x) => setV(Math.round(x)) });
    return () => c.stop();
  }, [target]);
  return v;
}

export function SubscriptionDetective({ txns, now }: { txns: readonly Txn[]; now: number }) {
  const subs = useMemo(() => detectSubscriptions(txns, now), [txns, now]);
  const yearly = subs.reduce((s, x) => s + annualCents(x), 0);
  const rolled = useCountUp(yearly);
  const [guideFor, setGuideFor] = useState<Subscription | null>(null);
  const [platform, setPlatform] = useState<CancelPlatform>("alipay");

  if (subs.length === 0) {
    return (
      <div className="rounded-2xl bg-card p-5 text-center">
        <div className="text-4xl" aria-hidden>
          🕵️
        </div>
        <p className="mt-2 font-semibold text-ink">没发现自动续费</p>
        <p className="mt-1 text-sm text-ink-2">同一家、金额差不多、按周/月/季/年规律出现的扣款，我会帮你揪出来～</p>
      </div>
    );
  }

  return (
    <div className="space-y-3">
      <div className="rounded-2xl bg-ink p-5 text-center text-white">
        <p className="text-[13px] opacity-70">🕵️ 订阅侦探发现 {subs.length} 项自动续费</p>
        <p className="mt-1 text-[13px] opacity-70">一年大约会花掉</p>
        <p className="thermal text-[44px] font-extrabold leading-tight text-coral">¥{yuan(rolled)}</p>
      </div>

      <ul className="space-y-2">
        {subs.map((s) => (
          <li key={s.merchant} className="flex items-center gap-3 rounded-2xl bg-card p-3">
            <div className="grid size-11 shrink-0 place-items-center rounded-xl bg-secondary text-2xl" aria-hidden>
              {CATEGORIES[categorize(s.merchant) === "OTHER" ? "SUBSCRIPTION" : categorize(s.merchant)].emoji}
            </div>
            <div className="min-w-0 flex-1">
              <p className="truncate font-semibold text-ink">{s.merchant}</p>
              <p className="text-[12px] text-ink-2">
                {cycleLabel(s.cycleDays)} ¥{yuan(s.amountCents)} · 下次约 {dayLabel(startOfDayOf(s.nextChargeAt))}
              </p>
            </div>
            <div className="text-right">
              <p className="thermal text-sm font-bold text-ink">¥{yuan(annualCents(s))}/年</p>
              <button onClick={() => setGuideFor(s)} className="mt-0.5 text-[12px] font-semibold text-coral">
                怎么取消
              </button>
            </div>
          </li>
        ))}
      </ul>
      <p className="px-1 text-[11px] text-ink-2">识别规则：同一商家、金额浮动在 ±10% 内、间隔接近每周/月/季/年。小票不会替你取消任何订阅。</p>

      <Drawer open={guideFor !== null} onOpenChange={(o) => !o && setGuideFor(null)}>
        <DrawerContent className="mx-auto max-w-[430px] bg-canvas outline-none">
          <DrawerTitle className="px-5 pt-4 text-lg font-extrabold">怎么取消「{guideFor?.merchant}」</DrawerTitle>
          <DrawerDescription className="px-5 text-[13px]">仅为操作指引，请在对应 App 里自己完成（不同版本入口可能略有差异）</DrawerDescription>
          <div className="mx-5 mt-3 flex rounded-full bg-secondary p-0.5" role="tablist">
            {(Object.keys(CANCEL_GUIDES) as CancelPlatform[]).map((p) => (
              <button key={p} role="tab" aria-selected={platform === p} onClick={() => setPlatform(p)} className={cn("flex-1 rounded-full py-1.5 text-sm font-semibold", platform === p ? "bg-card text-ink shadow-sm" : "text-ink-2")}>
                {CANCEL_GUIDES[p].name}
              </button>
            ))}
          </div>
          <ol className="space-y-2 px-5 pb-[max(1.5rem,env(safe-area-inset-bottom))] pt-4">
            {CANCEL_GUIDES[platform].steps.map((step, i) => (
              <li key={step} className="flex gap-3 text-[15px] text-ink">
                <span className="grid size-6 shrink-0 place-items-center rounded-full bg-coral-soft text-xs font-bold text-coral">{i + 1}</span>
                {step}
              </li>
            ))}
          </ol>
        </DrawerContent>
      </Drawer>
    </div>
  );
}
