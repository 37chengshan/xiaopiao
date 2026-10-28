import { useMemo, useState } from "react";
import { Slider } from "@/components/ui/slider";
import { cn } from "@/lib/utils";
import { CATEGORIES, type Txn, yuan } from "../../domain/types";
import { type RegretTarget, type WindowMonths, listTargets, simulateRegret } from "../../logic/regret";

function targetLabel(t: RegretTarget): string {
  return t.kind === "category" ? `${CATEGORIES[t.category].emoji} ${CATEGORIES[t.category].label}` : t.merchant;
}
const same = (a: RegretTarget, b: RegretTarget) => targetLabel(a) === targetLabel(b);

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
    return (
      <div className="rounded-2xl bg-card p-5 text-center">
        <div className="text-4xl" aria-hidden>
          🔮
        </div>
        <p className="mt-2 font-semibold text-ink">先记几笔，再来模拟</p>
        <p className="mt-1 text-sm text-ink-2">有了真实花费，才能算「如果少花一点」会怎样～</p>
      </div>
    );
  }

  return (
    <div className="rounded-2xl bg-card p-4">
      <h3 className="text-base font-extrabold text-ink">后悔模拟器 🔮</h3>
      <p className="text-[13px] text-ink-2">选一个想少花的，拖一拖看看能省多少</p>

      <div className="hide-scrollbar -mx-1 mt-3 flex gap-1.5 overflow-x-auto px-1 pb-1" role="radiogroup" aria-label="模拟对象">
        {targets.map((t) => (
          <button
            key={targetLabel(t)}
            role="radio"
            aria-checked={same(t, target)}
            onClick={() => setPicked(t)}
            className={cn("shrink-0 rounded-full px-3 py-1.5 text-[13px] font-semibold transition", same(t, target) ? "bg-coral text-white" : "bg-secondary text-ink-2")}
          >
            {targetLabel(t)}
          </button>
        ))}
      </div>

      <div className="mt-4">
        <div className="flex items-baseline justify-between text-sm">
          <label htmlFor="regret-pct" className="font-semibold text-ink">
            少花 {pct}%
          </label>
          <span className="text-[12px] text-ink-2">{pct === 100 ? "彻底戒掉" : pct === 0 ? "维持现状" : "拖动调整"}</span>
        </div>
        <Slider id="regret-pct" aria-label="减少比例" min={0} max={100} step={5} value={[pct]} onValueChange={(v) => setPct(v[0])} className="mt-3" />
      </div>

      <div className="mt-4 flex rounded-full bg-secondary p-0.5" role="radiogroup" aria-label="时间窗口">
        {([1, 3, 6] as const).map((m) => (
          <button key={m} role="radio" aria-checked={months === m} onClick={() => setMonths(m)} className={cn("flex-1 rounded-full py-1.5 text-sm font-semibold transition", months === m ? "bg-card text-ink shadow-sm" : "text-ink-2")}>
            {m} 个月
          </button>
        ))}
      </div>

      {result.enoughData ? (
        <div className="mt-4 rounded-2xl bg-coral-soft p-4 text-center" aria-live="polite">
          <p className="text-[13px] text-ink-2">{months} 个月能省下</p>
          <p className="thermal text-[40px] font-extrabold leading-tight text-coral">¥{yuan(result.savedCents)}</p>
          {result.equivalents.length > 0 && (
            <div className="mt-2 flex flex-wrap justify-center gap-2">
              {result.equivalents.map((e) => (
                <span key={e.label} className="rounded-full bg-card px-3 py-1 text-[13px] font-semibold text-ink">
                  {e.emoji} ≈ {e.count} {e.label}
                </span>
              ))}
            </div>
          )}
          <p className="mt-3 text-[12px] text-ink-2">
            按你这 {result.observedDays} 天的真实花费（¥{yuan(result.observedCents)}），每月约 ¥{yuan(result.monthlyCents)}
          </p>
        </div>
      ) : (
        <p className="mt-4 rounded-2xl bg-secondary p-4 text-center text-sm text-ink-2">这一项还没有花费记录，换一个看看～</p>
      )}
      <p className="mt-3 text-[11px] leading-relaxed text-ink-2">
        仅为纯算术估算：把过去的花费按同样节奏往后推，不含任何投资收益，也不构成理财建议。怎么花，永远由你决定。
      </p>
    </div>
  );
}
