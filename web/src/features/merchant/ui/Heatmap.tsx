import { useState } from "react";
import { cn } from "@/lib/utils";
import type { HeatCell } from "../logic/aggregator";
import { SLOT_LABELS, WEEKDAY_LABELS } from "../logic/crowd";

/** Five single-hue steps of clay alpha (docs/DESIGN.md §2). */
const RAMP = [8, 26, 46, 66, 85] as const;
const tint = (step: number) => `color-mix(in oklch, var(--clay) ${RAMP[step]}%, transparent)`;

function stepOf(people: number, lo: number, hi: number): number {
  if (hi === lo) return RAMP.length - 1;
  return Math.min(RAMP.length - 1, Math.floor(((people - lo) / (hi - lo)) * RAMP.length));
}

/** Weekday × time-slot grid. Suppressed (k<20) cells are empty hatched cells with no text. */
export function Heatmap({ grid, k, peak }: { grid: HeatCell[][]; k: number; peak: { weekday: number; slot: number } | null }) {
  const [sel, setSel] = useState<{ weekday: number; slot: number } | null>(peak);
  const visible = grid.flat().filter((c) => !c.suppressed).map((c) => c.people ?? 0);
  const lo = Math.min(...visible);
  const hi = Math.max(...visible);
  const picked = sel ? grid[sel.weekday][sel.slot] : null;

  return (
    <div className="mx-4 rounded-[14px] bg-surface px-4 pb-4 pt-3">
      <div role="group" aria-label="饮品购买时段热力图">
        <div className="grid grid-cols-[2.25rem_repeat(6,1fr)] gap-1 pb-1.5 text-center text-[11px] font-medium text-ink-3">
          <span aria-hidden />
          {SLOT_LABELS.map((s) => (
            <span key={s}>
              {s}
            </span>
          ))}
        </div>
        {grid.map((row, wd) => (
          <div key={wd} className="mb-1 grid grid-cols-[2.25rem_repeat(6,1fr)] gap-1">
            <span className="self-center text-[11px] font-medium text-ink-3">
              {WEEKDAY_LABELS[wd]}
            </span>
            {row.map((c) => {
              const on = sel?.weekday === wd && sel.slot === c.slot;
              return (
                <button
                  key={c.slot}
                  type="button"
                  onClick={() => setSel({ weekday: wd, slot: c.slot })}
                  aria-label={
                    c.suppressed
                      ? `${WEEKDAY_LABELS[wd]}${SLOT_LABELS[c.slot]}：样本不足，已隐藏`
                      : `${WEEKDAY_LABELS[wd]}${SLOT_LABELS[c.slot]}：${c.people} 人`
                  }
                  className={cn(
                    "h-8 rounded-[6px] outline-offset-1 transition-shadow duration-150",
                    c.suppressed ? "hatch ring-1 ring-inset ring-hairline" : "",
                    on && "shadow-[0_0_0_1.5px_var(--ink)]",
                  )}
                  style={c.suppressed ? undefined : { backgroundColor: tint(stepOf(c.people ?? 0, lo, hi)) }}
                />
              );
            })}
          </div>
        ))}
      </div>

      <p className="mt-2 min-h-[18px] text-[13px] leading-[18px] text-ink" aria-live="polite">
        {picked ? (
          <>
            <span className="font-medium">
              {WEEKDAY_LABELS[picked.weekday]}
              {SLOT_LABELS[picked.slot]}
            </span>
            <span className="text-ink-2">{picked.suppressed ? "　样本不足，已隐藏" : `　${picked.people} 位顾客买过饮品`}</span>
          </>
        ) : (
          <span className="text-ink-2">点一格看看人数</span>
        )}
      </p>

      <div className="mt-3 flex flex-wrap items-center gap-x-4 gap-y-2 border-t border-hairline pt-3 text-[11px] text-ink-2">
        <span className="flex items-center gap-1.5">
          少
          <span className="flex gap-0.5" aria-hidden>
            {RAMP.map((_, i) => (
              <span key={i} className="size-3 rounded-[3px]" style={{ backgroundColor: tint(i) }} />
            ))}
          </span>
          多
        </span>
        <span className="flex items-center gap-1.5">
          <span className="hatch size-3 rounded-[3px] ring-1 ring-inset ring-hairline" aria-hidden />
          斜线 = 不足 {k} 人
        </span>
      </div>
    </div>
  );
}
