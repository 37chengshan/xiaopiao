/**
 * View payloads are model output, so they are treated as untrusted: parse, clamp and drop anything unknown.
 * The phone uses the same catalogue (ChatModels.kt); keep the two lists identical.
 */
export const VIEW_KINDS = [
  "spend_summary",
  "category_split",
  "trend",
  "entries",
  "peak",
  "weekly_report",
  "subscriptions",
  "draft_list",
] as const;

export type ViewKind = (typeof VIEW_KINDS)[number];
export type ViewPeriod = "today" | "week" | "month";

export interface ViewRow {
  label?: string;
  merchant?: string;
  category?: string;
  cents?: number;
  occurred_at?: number;
  cadence?: string;
}

export interface ViewSpec {
  kind: ViewKind;
  period?: ViewPeriod;
  title?: string;
  total_cents?: number;
  count?: number;
  rows?: ViewRow[];
  points?: ViewRow[];
  label?: string;
  dimension?: "day" | "category" | "merchant";
  week_label?: string;
  top_category?: string;
}

const MAX_CENTS = 1_000_000_000;

function parseRows(value: unknown, max = 40): ViewRow[] | undefined {
  if (!Array.isArray(value)) return undefined;
  const out: ViewRow[] = [];
  for (const row of value.slice(0, max)) {
    if (typeof row !== "object" || row === null) continue;
    const source = row as Record<string, unknown>;
    const label = typeof source.label === "string" ? source.label.trim().slice(0, 60) : "";
    const merchant = typeof source.merchant === "string" ? source.merchant.trim().slice(0, 80) : "";
    const category = typeof source.category === "string" ? source.category.trim().slice(0, 40) : "";
    const cents = typeof source.cents === "number" && Number.isSafeInteger(source.cents) && Math.abs(source.cents) <= MAX_CENTS ? source.cents : undefined;
    if (!label && !merchant && cents === undefined) continue;
    out.push({
      label: label || undefined,
      merchant: merchant || undefined,
      category: category || undefined,
      cents,
      occurred_at: typeof source.occurred_at === "number" && Number.isSafeInteger(source.occurred_at) ? source.occurred_at : undefined,
      cadence: typeof source.cadence === "string" ? source.cadence.slice(0, 20) : undefined,
    });
  }
  return out.length ? out : undefined;
}

/** Returns null when the payload cannot be rendered safely; the caller must not guess. */
export function parseView(args: unknown): ViewSpec | null {
  if (typeof args !== "object" || args === null || Array.isArray(args)) return null;
  const source = args as Record<string, unknown>;
  const kind = source.kind;
  if (typeof kind !== "string" || !(VIEW_KINDS as readonly string[]).includes(kind)) return null;
  const total = typeof source.total_cents === "number" && Number.isSafeInteger(source.total_cents) && Math.abs(source.total_cents) <= MAX_CENTS ? source.total_cents : undefined;
  const spec: ViewSpec = {
    kind: kind as ViewKind,
    period: source.period === "today" || source.period === "week" || source.period === "month" ? source.period : undefined,
    title: typeof source.title === "string" ? source.title.trim().slice(0, 80) || undefined : undefined,
    total_cents: total,
    count: typeof source.count === "number" && Number.isSafeInteger(source.count) && source.count >= 0 ? source.count : undefined,
    rows: parseRows(source.rows),
    points: parseRows(source.points),
    label: typeof source.label === "string" ? source.label.trim().slice(0, 80) || undefined : undefined,
    dimension: source.dimension === "day" || source.dimension === "category" || source.dimension === "merchant" ? source.dimension : undefined,
    week_label: typeof source.week_label === "string" ? source.week_label.slice(0, 40) : undefined,
    top_category: typeof source.top_category === "string" ? source.top_category.slice(0, 40) : undefined,
  };
  // Each kind needs its minimum payload; an empty shell would render a broken card.
  const ready: Record<ViewKind, boolean> = {
    spend_summary: spec.total_cents !== undefined,
    category_split: Boolean(spec.rows?.length),
    trend: Boolean(spec.points?.length),
    entries: Boolean(spec.rows?.length),
    peak: Boolean(spec.label) && spec.total_cents !== undefined,
    weekly_report: spec.total_cents !== undefined,
    subscriptions: Boolean(spec.rows?.length),
    draft_list: Boolean(spec.rows?.length),
  };
  return ready[spec.kind] ? spec : null;
}
