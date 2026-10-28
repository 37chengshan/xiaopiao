import { useEffect, useState } from "react";
import {
  Area,
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  ComposedChart,
  Line,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import { money, moneyCompact } from "@/logic/biz/format";

/**
 * Recharts wrappers with one house style (docs/WEB-SPEC.md §A.2): hairline horizontal grid only,
 * 11px ink-3 axes, accent for the current period and a quieter ink-3 for the comparison period.
 * Each chart takes data it can label itself, so pages own the wording.
 */

/** Recharts writes paint into SVG presentation attributes, where `var()` never resolves — so tokens
 * are read off the document as real colours and re-read whenever the theme class changes. */
interface ChartColors {
  accent: string;
  ink2: string;
  ink3: string;
  hairline: string;
}

function readColors(): ChartColors {
  const style = getComputedStyle(document.documentElement);
  const pick = (name: string, fallback: string) => style.getPropertyValue(name).trim() || fallback;
  return {
    accent: pick("--d-accent", "#0066CC"),
    ink2: pick("--d-ink-2", "#6E6E73"),
    ink3: pick("--d-ink-3", "#AEAEB2"),
    hairline: pick("--d-hairline", "#E5E5EA"),
  };
}

function useChartColors(): ChartColors {
  const [colors, setColors] = useState<ChartColors>(readColors);
  useEffect(() => {
    const observer = new MutationObserver(() => setColors(readColors()));
    observer.observe(document.documentElement, { attributes: true, attributeFilter: ["class", "data-theme"] });
    return () => observer.disconnect();
  }, []);
  return colors;
}

interface TipPayload {
  name?: string | number;
  value?: number | string;
  dataKey?: string | number;
  color?: string;
}

function TipBox({
  active,
  payload,
  label,
  format,
}: {
  active?: boolean;
  payload?: TipPayload[];
  label?: string | number;
  format?: (value: number, key: string) => string;
}) {
  const colors = useChartColors();
  if (!active || !payload?.length) return null;
  return (
    <div className="rounded-[10px] bg-d-surface px-3 py-2 text-[12px] shadow-[0_6px_20px_oklch(0_0_0/12%)] ring-1 ring-d-hairline">
      <p className="mb-1 font-medium text-d-ink">{label}</p>
      {payload.map((row) => {
        const key = String(row.dataKey ?? row.name ?? "");
        const value = typeof row.value === "number" ? row.value : Number(row.value ?? 0);
        return (
          <p key={key} className="flex items-center gap-1.5 text-d-ink-2">
            <span
              className="size-1.5 rounded-full"
              style={{ backgroundColor: key === "orders" || key === "compareCents" ? colors.ink3 : colors.accent }}
              aria-hidden
            />
            <span>{row.name}</span>
            <span className="ml-auto pl-3 tabular-nums text-d-ink">{format ? format(value, key) : value}</span>
          </p>
        );
      })}
    </div>
  );
}

/** Revenue bars with the order count on a second axis. */
export function RevenueOrdersChart({
  data,
  revenueLabel,
  ordersLabel,
  height = 240,
}: {
  data: Array<{ label: string; revenueCents: number; orders: number | null; hasEntry: boolean }>;
  revenueLabel: string;
  ordersLabel: string;
  height?: number;
}) {
  const colors = useChartColors();
  const axis = { fontSize: 11, fill: colors.ink3 };
  return (
    <div style={{ height }} className="w-full">
      <ResponsiveContainer width="100%" height="100%">
        <ComposedChart data={data} margin={{ top: 8, right: 4, bottom: 0, left: 0 }} barCategoryGap="26%">
          <CartesianGrid vertical={false} stroke={colors.hairline} />
          <XAxis dataKey="label" tick={axis} axisLine={false} tickLine={false} interval="preserveStartEnd" minTickGap={8} />
          <YAxis
            yAxisId="revenue"
            tick={axis}
            axisLine={false}
            tickLine={false}
            width={46}
            tickFormatter={(value: number) => moneyCompact(value)}
          />
          <YAxis yAxisId="orders" orientation="right" tick={axis} axisLine={false} tickLine={false} width={34} />
          <Tooltip
            cursor={{ fill: colors.hairline, fillOpacity: 0.4 }}
            content={<TipBox format={(value, key) => (key === "revenueCents" ? money(value) : `${value}`)} />}
          />
          <Bar
            yAxisId="revenue"
            dataKey="revenueCents"
            name={revenueLabel}
            fill={colors.accent}
            radius={[3, 3, 0, 0]}
            maxBarSize={28}
            isAnimationActive={false}
          />
          <Line
            yAxisId="orders"
            type="monotone"
            dataKey="orders"
            name={ordersLabel}
            stroke={colors.ink2}
            strokeWidth={1.5}
            dot={false}
            activeDot={{ r: 3, fill: colors.ink2 }}
            isAnimationActive={false}
          />
        </ComposedChart>
      </ResponsiveContainer>
    </div>
  );
}

/** Area trend; `compareCents` draws the prior period as a quieter dashed line behind it. */
export function TrendArea({
  data,
  label,
  compareLabel,
  height = 240,
}: {
  data: Array<{ label: string; revenueCents: number | null; compareCents?: number | null }>;
  label: string;
  compareLabel?: string;
  height?: number;
}) {
  const colors = useChartColors();
  const axis = { fontSize: 11, fill: colors.ink3 };
  const hasCompare = data.some((d) => typeof d.compareCents === "number");
  return (
    <div style={{ height }} className="w-full">
      <ResponsiveContainer width="100%" height="100%">
        {/* ComposedChart, not AreaChart: recharts only draws `<Line>` children inside a composed chart. */}
        <ComposedChart data={data} margin={{ top: 8, right: 8, bottom: 0, left: 0 }}>
          <CartesianGrid vertical={false} stroke={colors.hairline} />
          <XAxis dataKey="label" tick={axis} axisLine={false} tickLine={false} interval="preserveStartEnd" minTickGap={8} />
          <YAxis
            tick={axis}
            axisLine={false}
            tickLine={false}
            width={46}
            tickFormatter={(value: number) => moneyCompact(value)}
          />
          <Tooltip content={<TipBox format={(value) => money(value)} />} cursor={{ stroke: colors.hairline }} />
          <Area
            type="monotone"
            dataKey="revenueCents"
            name={label}
            stroke={colors.accent}
            strokeWidth={2}
            fill={colors.accent}
            fillOpacity={0.1}
            isAnimationActive={false}
          />
          {hasCompare && (
            <Line
              type="monotone"
              dataKey="compareCents"
              name={compareLabel}
              stroke={colors.ink3}
              strokeWidth={1.5}
              strokeDasharray="4 3"
              dot={false}
              isAnimationActive={false}
            />
          )}
        </ComposedChart>
      </ResponsiveContainer>
    </div>
  );
}

/** Weekday averages: the busiest bar keeps the accent, the rest step back. */
export function WeekdayBars({
  data,
  valueLabel,
  height = 220,
}: {
  data: Array<{ label: string; averageCents: number; days: number }>;
  valueLabel: string;
  height?: number;
}) {
  const colors = useChartColors();
  const axis = { fontSize: 11, fill: colors.ink3 };
  const max = Math.max(1, ...data.map((d) => d.averageCents));
  return (
    <div style={{ height }} className="w-full">
      <ResponsiveContainer width="100%" height="100%">
        <BarChart data={data} margin={{ top: 8, right: 8, bottom: 0, left: 0 }} barCategoryGap="30%">
          <CartesianGrid vertical={false} stroke={colors.hairline} />
          <XAxis dataKey="label" tick={axis} axisLine={false} tickLine={false} />
          <YAxis
            tick={axis}
            axisLine={false}
            tickLine={false}
            width={46}
            tickFormatter={(value: number) => moneyCompact(value)}
          />
          <Tooltip cursor={{ fill: colors.hairline, fillOpacity: 0.4 }} content={<TipBox format={(value) => money(value)} />} />
          <Bar dataKey="averageCents" name={valueLabel} radius={[3, 3, 0, 0]} maxBarSize={36} isAnimationActive={false}>
            {data.map((row) => (
              <Cell key={row.label} fill={row.averageCents === max ? colors.accent : colors.ink3} />
            ))}
          </Bar>
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
}

/** How many days landed in each average-ticket band. */
export function Histogram({
  data,
  valueLabel,
  height = 200,
}: {
  data: Array<{ label: string; days: number }>;
  valueLabel: string;
  height?: number;
}) {
  const colors = useChartColors();
  const axis = { fontSize: 11, fill: colors.ink3 };
  return (
    <div style={{ height }} className="w-full">
      <ResponsiveContainer width="100%" height="100%">
        <BarChart data={data} margin={{ top: 8, right: 8, bottom: 0, left: 0 }} barCategoryGap="24%">
          <CartesianGrid vertical={false} stroke={colors.hairline} />
          <XAxis dataKey="label" tick={axis} axisLine={false} tickLine={false} />
          <YAxis tick={axis} axisLine={false} tickLine={false} width={28} allowDecimals={false} />
          <Tooltip cursor={{ fill: colors.hairline, fillOpacity: 0.4 }} content={<TipBox format={(value) => `${value}`} />} />
          <Bar dataKey="days" name={valueLabel} fill={colors.accent} radius={[3, 3, 0, 0]} maxBarSize={44} isAnimationActive={false} />
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
}
