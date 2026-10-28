import { Link } from "@tanstack/react-router";
import { Pencil, X } from "lucide-react";
import { useMemo } from "react";
import { CATEGORIES, type Subscription, type Txn, yuan } from "../../domain/types";
import { type DraftTxn } from "../../logic/quickAdd";
import { annualCents, cycleLabel, detectSubscriptions } from "../../logic/subscriptions";
import { relativeDayLabel, hhmm } from "../../logic/time";
import { startOfDay } from "../../domain/types";
import { buildWeeklyReport } from "../../logic/weeklyReport";
import { weekStartOf } from "../../logic/time";

export function DraftsCard({
  drafts,
  now,
  saved,
  onEdit,
  onRemove,
  onConfirm,
}: {
  drafts: readonly DraftTxn[];
  now: number;
  saved: boolean;
  onEdit: (d: DraftTxn) => void;
  onRemove: (id: string) => void;
  onConfirm: () => void;
}) {
  const total = drafts.filter((d) => d.direction === "OUT").reduce((s, d) => s + d.amountCents, 0);
  return (
    <div className="mt-2 rounded-2xl bg-card p-3 shadow-sm" role="group" aria-label="待确认的账目草稿">
      <ul className="divide-y divide-hairline">
        {drafts.map((d) => (
          <li key={d.id} className="flex items-center gap-2.5 py-2">
            <span className="grid size-9 shrink-0 place-items-center rounded-xl bg-secondary text-lg" aria-hidden>
              {CATEGORIES[d.category].emoji}
            </span>
            <div className="min-w-0 flex-1">
              <p className="truncate text-[14px] font-semibold text-ink">{d.merchant}</p>
              <p className="text-[11px] text-ink-2">
                {relativeDayLabel(startOfDay(d.occurredAt), now)} {hhmm(d.occurredAt)} · {CATEGORIES[d.category].label}
              </p>
            </div>
            <span className="thermal text-[15px] font-bold">
              {d.direction === "IN" ? "+" : ""}
              {yuan(d.amountCents)}
            </span>
            {!saved && (
              <>
                <button onClick={() => onEdit(d)} aria-label={`编辑 ${d.merchant}`} className="grid size-8 place-items-center rounded-full text-ink-2 hover:bg-secondary">
                  <Pencil className="size-4" />
                </button>
                <button onClick={() => onRemove(d.id)} aria-label={`去掉 ${d.merchant}`} className="grid size-8 place-items-center rounded-full text-ink-2 hover:bg-secondary">
                  <X className="size-4" />
                </button>
              </>
            )}
          </li>
        ))}
      </ul>
      {saved ? (
        <p className="pt-2 text-center text-[13px] font-semibold text-good">✅ 已记入账本</p>
      ) : drafts.length === 0 ? (
        <p className="pt-2 text-center text-[13px] text-ink-2">草稿都去掉啦，没有记任何东西</p>
      ) : (
        <button onClick={onConfirm} className="mt-2 h-11 w-full rounded-xl bg-coral text-[15px] font-bold text-white active:scale-[0.98]">
          确认记下 {drafts.length} 笔{total > 0 && ` · ¥${yuan(total)}`}
        </button>
      )}
    </div>
  );
}

export function WeeklySummaryCard({ txns, now }: { txns: readonly Txn[]; now: number }) {
  const r = useMemo(() => buildWeeklyReport(txns, weekStartOf(now), now), [txns, now]);
  return (
    <div className="mt-2 rounded-2xl bg-card p-3 shadow-sm">
      <p className="text-[12px] font-semibold text-ink-2">🏆 本周颁奖典礼</p>
      <p className="thermal text-[28px] font-extrabold">¥{yuan(r.totalCents)}</p>
      <p className="text-[12px] text-ink-2">{r.deltaPct === null ? r.comment : `较上周 ${r.deltaPct > 0 ? "+" : ""}${r.deltaPct}% · ${r.comment}`}</p>
      {r.badges.length > 0 && (
        <div className="mt-2 flex flex-wrap gap-1.5">
          {r.badges.slice(0, 4).map((b) => (
            <span key={b.code} className="rounded-full bg-coral-soft px-2.5 py-1 text-[12px] font-semibold text-ink" title={b.reason}>
              {b.emoji} {b.title}
            </span>
          ))}
        </div>
      )}
      <Link to="/insights" className="mt-2 inline-block text-[13px] font-bold text-coral">
        去看完整周报 →
      </Link>
    </div>
  );
}

export function SubscriptionSummaryCard({ txns, now }: { txns: readonly Txn[]; now: number }) {
  const subs: Subscription[] = useMemo(() => detectSubscriptions(txns, now), [txns, now]);
  const yearly = subs.reduce((s, x) => s + annualCents(x), 0);
  return (
    <div className="mt-2 rounded-2xl bg-card p-3 shadow-sm">
      <p className="text-[12px] font-semibold text-ink-2">🕵️ 订阅侦探</p>
      <p className="thermal text-[26px] font-extrabold">
        ¥{yuan(yearly)}
        <span className="ml-1 text-[12px] font-medium text-ink-2">/ 年</span>
      </p>
      <ul className="mt-1 space-y-0.5 text-[13px]">
        {subs.slice(0, 4).map((s) => (
          <li key={s.merchant} className="flex justify-between">
            <span>{s.merchant}</span>
            <span className="text-ink-2">
              {cycleLabel(s.cycleDays)} ¥{yuan(s.amountCents)}
            </span>
          </li>
        ))}
      </ul>
      <Link to="/insights" className="mt-2 inline-block text-[13px] font-bold text-coral">
        怎么取消 →
      </Link>
    </div>
  );
}
