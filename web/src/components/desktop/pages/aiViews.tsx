import { ArrowUpRight, CalendarClock, Repeat, Sparkles, Tag } from "lucide-react";
import type { ViewRow, ViewSpec } from "@/services/views";
import { money } from "../format";

/**
 * Modular view renderers, one per kind in services/views.ts. The model only picks a kind and fills numbers;
 * every pixel here is designed by the app, so an answer never depends on the model's formatting skill.
 */
const cents = (value?: number) => money(value ?? 0);

const Card = ({ title, children }: { title?: string; children: React.ReactNode }) => (
  <section className="mt-3 rounded-2xl border border-d-hairline bg-d-surface p-3.5">
    {title && <h3 className="mb-2.5 text-[12px] font-medium tracking-[0.02em] text-d-ink-3">{title}</h3>}
    {children}
  </section>
);

function Summary({ spec }: { spec: ViewSpec }) {
  return (
    <Card title={spec.title}>
      <p className="num text-[30px] font-semibold leading-none tracking-[-0.02em]">{cents(spec.total_cents)}</p>
      <p className="mt-2 flex items-center gap-1.5 text-[12px] text-d-ink-3">
        {spec.count !== undefined && <span>{spec.count} 笔</span>}
        {spec.period && <span>· {spec.period === "today" ? "今天" : spec.period === "week" ? "本周" : "本月"}</span>}
      </p>
    </Card>
  );
}

function Bars({ rows, tone = "accent" }: { rows: ViewRow[]; tone?: "accent" | "ink" }) {
  const max = Math.max(...rows.map((row) => Math.abs(row.cents ?? 0)), 1);
  return (
    <ul className="space-y-2">
      {rows.map((row, index) => (
        <li key={`${row.label ?? row.merchant ?? index}`} className="grid grid-cols-[minmax(0,1fr)_auto] items-center gap-2">
          <div className="min-w-0">
            <div className="flex items-baseline justify-between gap-2">
              <span className="truncate text-[12.5px] text-d-ink-2">{row.label ?? row.merchant}</span>
              <span className="num shrink-0 text-[12.5px] text-d-ink">{cents(row.cents)}</span>
            </div>
            <div className="mt-1 h-1.5 overflow-hidden rounded-full bg-d-fill">
              <div
                className={tone === "accent" ? "h-full rounded-full bg-d-accent" : "h-full rounded-full bg-d-ink-3"}
                style={{ width: `${Math.max(4, (Math.abs(row.cents ?? 0) / max) * 100)}%` }}
              />
            </div>
          </div>
        </li>
      ))}
    </ul>
  );
}

function Trend({ spec }: { spec: ViewSpec }) {
  const points = spec.points ?? [];
  const max = Math.max(...points.map((point) => Math.abs(point.cents ?? 0)), 1);
  return (
    <Card title={spec.title}>
      <div className="flex h-24 items-end gap-1.5">
        {points.map((point, index) => (
          <div key={`${point.label ?? index}`} className="flex min-w-0 flex-1 flex-col items-center gap-1" title={`${point.label ?? ""} ${cents(point.cents)}`}>
            <div className="flex w-full flex-1 items-end">
              <div className="w-full rounded-t bg-d-accent/80" style={{ height: `${Math.max(3, (Math.abs(point.cents ?? 0) / max) * 100)}%` }} />
            </div>
            <span className="num w-full truncate text-center text-[9.5px] text-d-ink-3">{point.label ?? ""}</span>
          </div>
        ))}
      </div>
    </Card>
  );
}

function Entries({ spec }: { spec: ViewSpec }) {
  return (
    <Card title={spec.title ?? "最近明细"}>
      <ul className="divide-y divide-d-hairline">
        {(spec.rows ?? []).map((row, index) => (
          <li key={`${row.merchant ?? index}`} className="flex items-center gap-2 py-2 first:pt-0 last:pb-0">
            <Tag className="size-3.5 shrink-0 text-d-ink-3" strokeWidth={2} aria-hidden />
            <span className="min-w-0 flex-1 truncate text-[13px] text-d-ink">{row.merchant ?? row.label}</span>
            {row.category && <span className="shrink-0 text-[11.5px] text-d-ink-3">{row.category}</span>}
            <span className="num shrink-0 text-[13px] text-d-ink">{cents(row.cents)}</span>
          </li>
        ))}
      </ul>
    </Card>
  );
}

function Peak({ spec }: { spec: ViewSpec }) {
  return (
    <Card title={spec.title ?? "最大的一笔"}>
      <div className="flex items-center gap-3">
        <ArrowUpRight className="size-5 shrink-0 text-d-accent" strokeWidth={2} aria-hidden />
        <div className="min-w-0">
          <p className="truncate text-[13px] text-d-ink-2">{spec.label}</p>
          <p className="num text-[22px] font-semibold">{cents(spec.total_cents)}</p>
        </div>
      </div>
    </Card>
  );
}

function Weekly({ spec }: { spec: ViewSpec }) {
  return (
    <Card title={spec.title ?? "本周小票"}>
      <div className="flex items-end justify-between gap-3">
        <div>
          <p className="num text-[26px] font-semibold leading-none">{cents(spec.total_cents)}</p>
          <p className="mt-1.5 text-[12px] text-d-ink-3">{[spec.count !== undefined ? `${spec.count} 笔` : "", spec.week_label ?? ""].filter(Boolean).join(" · ")}</p>
        </div>
        {spec.top_category && (
          <span className="flex items-center gap-1 rounded-full bg-d-fill px-2.5 py-1 text-[11.5px] text-d-ink-2">
            <Sparkles className="size-3" strokeWidth={2} aria-hidden />
            {spec.top_category}
          </span>
        )}
      </div>
    </Card>
  );
}

function Recurring({ spec }: { spec: ViewSpec }) {
  return (
    <Card title={spec.title ?? "规律扣款"}>
      <ul className="space-y-2">
        {(spec.rows ?? []).map((row, index) => (
          <li key={`${row.merchant ?? index}`} className="flex items-center gap-2">
            <Repeat className="size-3.5 shrink-0 text-d-ink-3" strokeWidth={2} aria-hidden />
            <span className="min-w-0 flex-1 truncate text-[13px]">{row.merchant ?? row.label}</span>
            {row.cadence && (
              <span className="flex items-center gap-1 text-[11.5px] text-d-ink-3">
                <CalendarClock className="size-3" strokeWidth={2} aria-hidden />
                {row.cadence}
              </span>
            )}
            <span className="num text-[13px]">{cents(row.cents)}</span>
          </li>
        ))}
      </ul>
    </Card>
  );
}

export function ViewCard({ spec }: { spec: ViewSpec }) {
  switch (spec.kind) {
    case "spend_summary":
      return <Summary spec={spec} />;
    case "category_split":
      return (
        <Card title={spec.title ?? "分类占比"}>
          <Bars rows={spec.rows ?? []} />
        </Card>
      );
    case "trend":
      return <Trend spec={spec} />;
    case "entries":
      return <Entries spec={spec} />;
    case "peak":
      return <Peak spec={spec} />;
    case "weekly_report":
      return <Weekly spec={spec} />;
    case "subscriptions":
      return <Recurring spec={spec} />;
    case "draft_list":
      return (
        <Card title={spec.title ?? "待确认草稿"}>
          <Bars rows={spec.rows ?? []} tone="ink" />
        </Card>
      );
    default:
      return null;
  }
}
