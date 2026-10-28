import { useEffect, useState } from "react";
import { useTheme } from "@/lib/theme";
import { Area, AreaChart, Bar, BarChart, Cell, Pie, PieChart, ResponsiveContainer, Tooltip as RechartsTooltip, XAxis, YAxis, type TooltipProps } from "recharts";
import { cn } from "@/lib/utils";
import { money, moneyShort } from "./format";

/**
 * One chart style for the whole shell (docs/WEB-SPEC.md §A.2): no vertical grid, horizontal hairline
 * rules only, 11px ink-3 axis labels, accent for the main series, ink-3 at 30% for the compare period.
 * Series entrance animation is off on purpose: recharts keeps a series clipped to zero width until its
 * rAF animation runs, so a throttled/occluded window would show empty axes. A painted chart beats a flourish.
 */

/**
 * Recharts writes colours as SVG presentation attributes, where `var(--x)` is not allowed.
 * Resolve the `d-*` tokens once per theme instead of duplicating their values here.
 */
function readChartColors() {
  const css = getComputedStyle(document.documentElement);
  const read = (name: string, fallback: string) => css.getPropertyValue(name).trim() || fallback;
  return {
    accent: read("--d-accent", "#0066CC"),
    ink3: read("--d-ink-3", "#AEAEB2"),
    hairline: read("--d-hairline", "#E5E5EA"),
    fill: read("--d-fill", "#EDEDF0"),
  };
}

function useChartColors() {
  const { dark } = useTheme();
  const [colors, setColors] = useState(readChartColors);
  useEffect(() => setColors(readChartColors()), [dark]);
  return colors;
}

export interface Point {
  label: string;
  value: number;
  compare?: number;
}

function TipBox({ active, payload, label, total, accent }: TooltipProps<number, string> & { total?: number; accent: string }) {
  if (!active || !payload?.length) return null;
  return (
    <div className="rounded-[10px] bg-d-surface px-3 py-2 shadow-[0_6px_20px_oklch(0_0_0/12%)]">
      <p className="mb-1 text-[11px] text-d-ink-3">{label}</p>
      {payload.map((p) => (
        <p key={String(p.dataKey)} className="flex items-center gap-2 text-[12px] text-d-ink">
          <span aria-hidden className="size-1.5 rounded-full" style={{ background: p.color ?? accent }} />
          <span className="text-d-ink-2">{p.name}</span>
          <span className="num ml-auto font-medium">{money(Number(p.value ?? 0))}</span>
          {total && total > 0 ? <span className="num text-d-ink-3">{Math.round((Number(p.value ?? 0) / total) * 100)}%</span> : null}
        </p>
      ))}
    </div>
  );
}

function chartFrame(height: number) {
  return { height, width: "100%" } as const;
}

export function AreaTrend({ points, height = 220, compareName, valueName, interval }: { points: Point[]; height?: number; compareName: string; valueName: string; interval?: number }) {
  const c = useChartColors();
  const axis = { fontSize: 11, fill: c.ink3 };
  const hasCompare = points.some((p) => p.compare !== undefined);
  return (
    <div style={chartFrame(height)}>
      <ResponsiveContainer>
        <AreaChart data={points} margin={{ top: 8, right: 6, bottom: 0, left: -6 }}>
          <defs>
            <linearGradient id="d-area-fill" x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stopColor={c.accent} stopOpacity={0.18} />
              <stop offset="100%" stopColor={c.accent} stopOpacity={0} />
            </linearGradient>
          </defs>
          <XAxis dataKey="label" tick={axis} tickLine={false} axisLine={false} interval={interval ?? "preserveStartEnd"} minTickGap={24} dy={4} />
          <YAxis tick={axis} tickLine={false} axisLine={false} width={52} tickFormatter={(v: number) => moneyShort(v)} />
          <RechartsTooltip content={<TipBox accent={c.accent} />} cursor={{ stroke: c.hairline }} />
          {hasCompare && <Area type="monotone" dataKey="compare" name={compareName} stroke={c.ink3} strokeOpacity={0.3} strokeWidth={1.25} fill="transparent" strokeDasharray="3 3" dot={false} isAnimationActive={false} />}
          <Area type="monotone" dataKey="value" name={valueName} stroke={c.accent} strokeWidth={1.75} fill="url(#d-area-fill)" dot={false} activeDot={{ r: 3, strokeWidth: 0 }} isAnimationActive={false} />
        </AreaChart>
      </ResponsiveContainer>
    </div>
  );
}

export function BarTrend({ points, height = 220, compareName, valueName }: { points: Point[]; height?: number; compareName: string; valueName: string }) {
  const c = useChartColors();
  const axis = { fontSize: 11, fill: c.ink3 };
  const hasCompare = points.some((p) => p.compare !== undefined);
  return (
    <div style={chartFrame(height)}>
      <ResponsiveContainer>
        <BarChart data={points} margin={{ top: 8, right: 6, bottom: 0, left: -6 }} barCategoryGap="28%">
          <XAxis dataKey="label" tick={axis} tickLine={false} axisLine={false} dy={4} />
          <YAxis tick={axis} tickLine={false} axisLine={false} width={52} tickFormatter={(v: number) => moneyShort(v)} />
          <RechartsTooltip content={<TipBox accent={c.accent} />} cursor={{ fill: c.fill, fillOpacity: 0.5 }} />
          {hasCompare && <Bar dataKey="compare" name={compareName} fill={c.ink3} fillOpacity={0.3} radius={[3, 3, 0, 0]} isAnimationActive={false} />}
          <Bar dataKey="value" name={valueName} fill={c.accent} radius={[3, 3, 0, 0]} isAnimationActive={false} />
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
}

export interface Slice {
  label: string;
  value: number;
  /** `muted` paints the folded "everything else" slice in ink-3 instead of the accent ramp */
  color?: "muted";
}

/** Accent, fading out; anything beyond the fifth slice folds into one ink-3 slice by the caller. */
const SLICE_OPACITY = [1, 0.82, 0.64, 0.46, 0.3];

export function Donut({ slices, size = 180, centerValue, centerLabel }: { slices: Slice[]; size?: number; centerValue?: string; centerLabel?: string }) {
  const c = useChartColors();
  const total = slices.reduce((s, x) => s + x.value, 0);
  return (
    <div className="relative shrink-0" style={{ width: size, height: size }}>
      <PieChart width={size} height={size}>
          <Pie data={slices} dataKey="value" nameKey="label" innerRadius="66%" outerRadius="94%" paddingAngle={1.5} stroke="none" isAnimationActive={false}>
            {slices.map((slice, i) => (
              <Cell key={slice.label} fill={slice.color === "muted" ? c.ink3 : c.accent} fillOpacity={slice.color === "muted" ? 1 : (SLICE_OPACITY[i] ?? 0.24)} />
            ))}
          </Pie>
          <RechartsTooltip content={<TipBox total={total} accent={c.accent} />} />
      </PieChart>
      {centerValue && (
        <div className="pointer-events-none absolute inset-0 grid place-content-center text-center">
          <p className="num text-[15px] font-semibold text-d-ink">{centerValue}</p>
          {centerLabel && <p className="mt-0.5 text-[11px] text-d-ink-3">{centerLabel}</p>}
        </div>
      )}
    </div>
  );
}

export function Legend({ slices, total, className }: { slices: Slice[]; total: number; className?: string }) {
  const c = useChartColors();
  return (
    <ul className={cn("min-w-0 flex-1 space-y-1.5", className)}>
      {slices.map((slice, i) => (
        <li key={slice.label} className="flex items-center gap-2 text-[12.5px]">
          <span aria-hidden className="size-2 shrink-0 rounded-full" style={{ background: slice.color === "muted" ? c.ink3 : c.accent, opacity: slice.color === "muted" ? 1 : (SLICE_OPACITY[i] ?? 0.24) }} />
          <span className="min-w-0 truncate text-d-ink-2">{slice.label}</span>
          <span className="num ml-auto shrink-0 text-d-ink">{money(slice.value)}</span>
          <span className="num w-8 shrink-0 text-right text-d-ink-3">{total > 0 ? Math.round((slice.value / total) * 100) : 0}%</span>
        </li>
      ))}
    </ul>
  );
}

/** Tiny bar strip used inside the AI sources panel. */
export function MiniBars({ points, height = 56 }: { points: Point[]; height?: number }) {
  const c = useChartColors();
  const max = Math.max(1, ...points.map((p) => p.value));
  return (
    <div className="flex items-end gap-1" style={{ height }} aria-hidden>
      {points.map((p) => (
        <span key={p.label} className="min-w-0 flex-1 rounded-[2px]" style={{ height: `${Math.max(4, (p.value / max) * 100)}%`, background: c.accent, opacity: 0.7 }} />
      ))}
    </div>
  );
}
