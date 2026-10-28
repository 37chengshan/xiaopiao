import { useNavigate } from "@tanstack/react-router";
import { Check, SlidersHorizontal, X } from "lucide-react";
import { useMemo, useState } from "react";
import { CATEGORY_ICONS } from "@/components/app/categoryIcons";
import { Group, IconTile, Money, Row, XButton } from "@/components/app/kit";
import { cn } from "@/lib/utils";
import { CATEGORIES, type Subscription, type Txn, startOfDay, yuan } from "../../domain/types";
import { type DraftTxn } from "../../logic/quickAdd";
import { annualCents, cycleLabel, detectSubscriptions } from "../../logic/subscriptions";
import { hhmm, relativeDayLabel, weekStartOf } from "../../logic/time";
import { buildWeeklyReport } from "../../logic/weeklyReport";

const CARD = "mx-0 ring-1 ring-hairline";

function DraftRow({
  d,
  now,
  saved,
  onEdit,
  onChange,
  onRemove,
}: {
  d: DraftTxn;
  now: number;
  saved: boolean;
  onEdit: (d: DraftTxn) => void;
  onChange: (d: DraftTxn) => void;
  onRemove: (id: string) => void;
}) {
  const [editing, setEditing] = useState(false);
  const [name, setName] = useState(d.merchant);
  const [amount, setAmount] = useState(String(d.amountCents / 100));
  const Icon = CATEGORY_ICONS[d.category];

  const commit = () => {
    const cents = Math.round(Number(amount) * 100);
    if (Number.isFinite(cents) && cents > 0) onChange({ ...d, merchant: name.trim() || d.merchant, amountCents: cents });
    setEditing(false);
  };

  if (editing) {
    return (
      <form
        className="flex items-center gap-2 px-3 py-2.5"
        onSubmit={(e) => {
          e.preventDefault();
          commit();
        }}
      >
        <input
          value={name}
          onChange={(e) => setName(e.target.value)}
          aria-label="商家"
          autoFocus
          className="h-9 min-w-0 flex-1 rounded-[10px] bg-surface-2 px-3 text-[16px] text-ink outline-none"
        />
        <input
          value={amount}
          onChange={(e) => setAmount(e.target.value)}
          aria-label="金额"
          inputMode="decimal"
          className="num h-9 w-[5.5rem] rounded-[10px] bg-surface-2 px-3 text-right text-[16px] text-ink outline-none"
        />
        <button type="button" onClick={() => onEdit(d)} aria-label="更多：分类与日期" className="grid size-8 shrink-0 place-items-center rounded-full text-ink-2 active:bg-surface-2">
          <SlidersHorizontal className="size-[17px]" strokeWidth={1.75} />
        </button>
        <button type="submit" aria-label="完成" className="grid size-8 shrink-0 place-items-center rounded-full bg-ink text-bg active:scale-[0.94]">
          <Check className="size-[17px]" strokeWidth={2.25} />
        </button>
      </form>
    );
  }

  return (
    <div className="flex items-center pr-1.5">
      <button
        type="button"
        disabled={saved}
        onClick={() => setEditing(true)}
        aria-label={saved ? undefined : `编辑 ${d.merchant}`}
        className="flex min-h-[52px] min-w-0 flex-1 items-center gap-3 py-2 pl-4 pr-2 text-left transition-colors duration-100 enabled:active:bg-surface-2"
      >
        <IconTile icon={Icon} />
        <span className="min-w-0 flex-1">
          <span className="block truncate text-[16px] leading-[22px] text-ink">{d.merchant}</span>
          <span className="block truncate text-[13px] leading-[18px] text-ink-2">
            {relativeDayLabel(startOfDay(d.occurredAt), now)} {hhmm(d.occurredAt)} · {CATEGORIES[d.category].label}
          </span>
        </span>
        <Money cents={d.direction === "IN" ? d.amountCents : -d.amountCents} sign={d.direction === "IN"} className={cn("text-[16px]", d.direction === "IN" ? "text-positive" : "text-ink")} />
      </button>
      {!saved && (
        <button type="button" onClick={() => onRemove(d.id)} aria-label={`去掉 ${d.merchant}`} className="grid size-8 shrink-0 place-items-center rounded-full text-ink-3 active:bg-surface-2">
          <X className="size-4" strokeWidth={2} />
        </button>
      )}
    </div>
  );
}

/** Draft rows the human confirms (BRIEF: humans make every choice). Tap a row to edit it inline. */
export function DraftsCard({
  drafts,
  now,
  saved,
  onEdit,
  onChange,
  onRemove,
  onConfirm,
}: {
  drafts: readonly DraftTxn[];
  now: number;
  saved: boolean;
  onEdit: (d: DraftTxn) => void;
  onChange: (d: DraftTxn) => void;
  onRemove: (id: string) => void;
  onConfirm: () => void;
}) {
  const total = drafts.filter((d) => d.direction === "OUT").reduce((s, d) => s + d.amountCents, 0);
  return (
    <div role="group" aria-label="待确认的账目草稿">
      {drafts.length > 0 && (
        <Group inset="icon" className={CARD}>
          {drafts.map((d) => (
            <DraftRow key={d.id} d={d} now={now} saved={saved} onEdit={onEdit} onChange={onChange} onRemove={onRemove} />
          ))}
        </Group>
      )}
      {saved ? (
        <p className="flex items-center gap-1.5 pt-3 text-[14px] font-medium text-positive">
          <Check className="size-4" strokeWidth={2.25} aria-hidden />
          已记入账本
        </p>
      ) : drafts.length === 0 ? (
        <p className="text-[14px] text-ink-2">草稿都去掉啦，没有记任何东西</p>
      ) : (
        <XButton variant="primary" className="mt-3 w-full" onClick={onConfirm}>
          确认记下 {drafts.length} 笔{total > 0 && ` · ¥${yuan(total)}`}
        </XButton>
      )}
    </div>
  );
}

function CardHead({ children }: { children: string }) {
  return <p className="px-4 pt-3.5 text-[13px] leading-[18px] text-ink-2">{children}</p>;
}

function GoRow({ label, to }: { label: string; to: "/insights" }) {
  const navigate = useNavigate();
  return <Row title={<span className="text-[15px] text-ink-2">{label}</span>} chevron onClick={() => void navigate({ to })} className="min-h-[44px]" />;
}

export function WeeklySummaryCard({ txns, now }: { txns: readonly Txn[]; now: number }) {
  const r = useMemo(() => buildWeeklyReport(txns, weekStartOf(now), now), [txns, now]);
  return (
    <Group className={CARD}>
      <div className="pb-4">
        <CardHead>本周颁奖典礼</CardHead>
        <Money cents={r.totalCents} className="mt-0.5 block px-4 text-[34px] font-semibold leading-[40px] text-ink" />
        <p className="mt-1 px-4 text-[13px] leading-[18px] text-ink-2">
          {r.deltaPct === null ? (
            r.comment
          ) : (
            <>
              较上周 <span className={r.deltaPct <= 0 ? "text-positive" : "text-negative"}>{r.deltaPct > 0 ? "+" : ""}{r.deltaPct}%</span> · {r.comment}
            </>
          )}
        </p>
        {r.badges.length > 0 && (
          <div className="mt-3 flex flex-wrap gap-1.5 px-4">
            {r.badges.slice(0, 4).map((b) => (
              <span key={b.code} className="inline-flex h-7 items-center rounded-full bg-fill px-2.5 text-[13px] text-ink" title={b.reason}>
                {b.emoji}&nbsp;{b.title}
              </span>
            ))}
          </div>
        )}
      </div>
      <GoRow label="完整周报" to="/insights" />
    </Group>
  );
}

export function SubscriptionSummaryCard({ txns, now }: { txns: readonly Txn[]; now: number }) {
  const subs: Subscription[] = useMemo(() => detectSubscriptions(txns, now), [txns, now]);
  const yearly = subs.reduce((s, x) => s + annualCents(x), 0);
  return (
    <Group className={CARD}>
      <div className="pb-3">
        <CardHead>订阅侦探 · 一年大约</CardHead>
        <Money cents={yearly} className="mt-0.5 block px-4 text-[34px] font-semibold leading-[40px] text-ink" />
        <ul className="mt-2.5 space-y-1 px-4 text-[14px] leading-[20px]">
          {subs.slice(0, 4).map((s) => (
            <li key={s.merchant} className="flex items-baseline justify-between gap-3">
              <span className="truncate text-ink">{s.merchant}</span>
              <span className="num shrink-0 text-ink-2">
                {cycleLabel(s.cycleDays)} ¥{yuan(s.amountCents)}
              </span>
            </li>
          ))}
        </ul>
      </div>
      <GoRow label="怎么取消" to="/insights" />
    </Group>
  );
}
