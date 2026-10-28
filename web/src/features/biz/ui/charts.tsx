import { Suspense, lazy, type ComponentProps, type ReactNode } from "react";

/**
 * Charts live in a lazily loaded module: recharts is the largest dependency in the console
 * (~400 kB) and no chart is worth blocking a page's first paint for. Each wrapper reserves the
 * chart's own height while it loads, so the layout never jumps.
 */
const RevenueOrdersChartLazy = lazy(() =>
  import("./chartsImpl").then((module) => ({ default: module.RevenueOrdersChart })),
);
const TrendAreaLazy = lazy(() => import("./chartsImpl").then((module) => ({ default: module.TrendArea })));
const WeekdayBarsLazy = lazy(() => import("./chartsImpl").then((module) => ({ default: module.WeekdayBars })));
const HistogramLazy = lazy(() => import("./chartsImpl").then((module) => ({ default: module.Histogram })));

function Deferred({ height, children }: { height: number; children: ReactNode }) {
  return <Suspense fallback={<div style={{ height }} aria-hidden />}>{children}</Suspense>;
}

export function RevenueOrdersChart(props: ComponentProps<typeof RevenueOrdersChartLazy>) {
  return (
    <Deferred height={props.height ?? 240}>
      <RevenueOrdersChartLazy {...props} />
    </Deferred>
  );
}

export function TrendArea(props: ComponentProps<typeof TrendAreaLazy>) {
  return (
    <Deferred height={props.height ?? 240}>
      <TrendAreaLazy {...props} />
    </Deferred>
  );
}

export function WeekdayBars(props: ComponentProps<typeof WeekdayBarsLazy>) {
  return (
    <Deferred height={props.height ?? 220}>
      <WeekdayBarsLazy {...props} />
    </Deferred>
  );
}

export function Histogram(props: ComponentProps<typeof HistogramLazy>) {
  return (
    <Deferred height={props.height ?? 200}>
      <HistogramLazy {...props} />
    </Deferred>
  );
}
