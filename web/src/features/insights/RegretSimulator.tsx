import * as SliderPrimitive from "@radix-ui/react-slider";
import { Sparkles } from "lucide-react";
import { useMemo, useState } from "react";
import { Chip, Footnote, Group, Money, Row, SectionHeader, Segmented } from "@/components/app/kit";
import { EmptyState } from "@/components/app/States";
import { CATEGORIES, type Txn, yuan } from "../../domain/types";
import { type RegretTarget, type WindowMonths, listTargets, simulateRegret } from "../../logic/regret";

const targetLabel = (t: RegretTarget): string => (t.kind === "category" ? CATEGORIES[t.category].label : t.merchant);
const targetKey = (t: RegretTarget): string => `${t.kind}:${targetLabel(t)}`;

export function RegretSimulator({
  txns,
  now,
  initialTarget,
  initialPct = 50,
  initialMonths = 3,
}: {
  txns: readonly Txn[];
  now: number;
  initialTarget?: RegretTarget | null;
  initialPct?: number;
  initialMonths?: WindowMonths;
}) {
  const targets = useMemo(() => listTargets(txns), [txns]);
  const [picked, setPicked] = useState<RegretTarget | null>(initialTarget ?? null);
  const [pct, setPct] = useState(initialPct);
  const [months, setMonths] = useState<WindowMonths>(initialMonths);
  const target = picked ?? targets[0] ?? null;

  const result = useMemo(
    () => (target ? simulateRegret({ txns, now, target, reductionPct: pct, windowMonths: months }) : null),
    [txns, now, target, pct, months],
  );

  if (!target || !result) {
    return <EmptyState icon={Sparkles} title="先记几笔，再来模拟" hint="有了真实花费，才能算「如果少花一点」会怎样" />;
  }

  return (
    <div>
      <p className="px-8 pt-5 text-[13px] leading-[18px] text-ink-2">选一个想少花的，拖一拖看看能省多少</p>

      <div className="hide-scrollbar mt-3 flex gap-2 overflow-x-auto px-4 pb-1" role="radiogroup" aria-label="模拟对象">
        {targets.map((t) => (
          <Chip key={targetKey(t)} role="radio" aria-checked={targetKey(t) === targetKey(target)} selected={targetKey(t) === targetKey(target)} onClick={() => setPicked(t)} className="max-w-[12rem]">
            <span className="truncate">{targetLabel(t)}</span>
          </Chip>
        ))}
      </div>

      <div className="mx-4 mt-3 rounded-[14px] bg-surface p-4">
        <div className="flex items-baseline justify-between">
          <label htmlFor="regret-pct" className="text-[16px] font-medium text-ink">
            少花 <span className="num">{pct}</span>%
          </label>
          <span className="text-[13px] text-ink-2">{pct === 100 ? "彻底戒掉" : pct === 0 ? "维持现状" : "拖动调整"}</span>
        </div>
        <SliderPrimitive.Root id="regret-pct" aria-label="减少比例" min={0} max={100} step={5} value={[pct]} onValueChange={(v) => setPct(v[0])} className="relative mt-3 flex h-11 w-full touch-none select-none items-center">
          <SliderPrimitive.Track className="relative h-1.5 w-full grow overflow-hidden rounded-full bg-fill">
            <SliderPrimitive.Range className="absolute h-full bg-clay" />
          </SliderPrimitive.Track>
          <SliderPrimitive.Thumb className="block size-7 rounded-full bg-white shadow-[0_1px_4px_oklch(0_0_0/28%)] ring-1 ring-hairline transition-transform duration-100 active:scale-[1.06] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-clay" />
        </SliderPrimitive.Root>
        <Segmented
          className="mt-2"
          value={String(months) as "1" | "3" | "6"}
          onChange={(v) => setMonths(Number(v) as WindowMonths)}
          options={[
            { value: "1", label: "1 个月" },
            { value: "3", label: "3 个月" },
            { value: "6", label: "6 个月" },
          ]}
        />
      </div>

      {result.enoughData ? (
        <div aria-live="polite">
          <div className="px-8 pt-6">
            <p className="text-[13px] leading-[18px] text-ink-2">{months} 个月能省下</p>
            <Money cents={result.savedCents} className="mt-1 block text-[48px] font-semibold leading-[52px] tracking-[-0.03em] text-ink" />
          </div>
          {result.equivalents.length > 0 && (
            <>
              <SectionHeader>大约相当于</SectionHeader>
              <Group>
                {result.equivalents.map((e) => (
                  <Row key={e.label} title={`≈ ${e.count} ${e.label}`} className="min-h-[44px]" />
                ))}
              </Group>
            </>
          )}
          <Footnote>
            按你这 {result.observedDays} 天的真实花费（¥{yuan(result.observedCents)}），每月约 ¥{yuan(result.monthlyCents)}。
          </Footnote>
        </div>
      ) : (
        <p className="mx-4 mt-4 rounded-[14px] bg-surface p-4 text-center text-[14px] text-ink-2">这一项还没有花费记录，换一个看看～</p>
      )}
      <Footnote className="pt-4 text-[12px] leading-[18px]">
        仅为纯算术估算：把过去的花费按同样节奏往后推，不含任何投资收益，也不构成理财建议。怎么花，永远由你决定。
      </Footnote>
    </div>
  );
}
