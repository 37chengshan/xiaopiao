import { motion, useMotionValue, useTransform } from "framer-motion";
import { FileUp, Plus, Trash2 } from "lucide-react";
import { useMemo, useRef } from "react";
import { toast } from "sonner";
import { EmptyState, ErrorState, LoadingState } from "@/components/app/States";
import { PageHeader } from "@/components/app/PageHeader";
import { useLedger } from "@/services/LedgerProvider";
import { CATEGORIES, type Txn, yuan } from "../../domain/types";
import { dateKey, hhmm, parseDateKey, relativeDayLabel } from "../../logic/time";
import { useEntry } from "../entry/EntryProvider";

interface DayGroup {
  key: string;
  dayStart: number;
  totalOut: number;
  items: Txn[];
}

function groupByDay(txns: readonly Txn[]): DayGroup[] {
  const map = new Map<string, DayGroup>();
  for (const t of [...txns].sort((a, b) => b.occurredAt - a.occurredAt)) {
    const key = dateKey(t.occurredAt);
    const g = map.get(key) ?? { key, dayStart: parseDateKey(key), totalOut: 0, items: [] };
    g.items.push(t);
    if (t.direction === "OUT") g.totalOut += t.amountCents;
    map.set(key, g);
  }
  return [...map.values()];
}

function Row({ txn, onEdit, onDelete }: { txn: Txn; onEdit: () => void; onDelete: () => void }) {
  const dragged = useRef(false);
  const x = useMotionValue(0);
  const reveal = useTransform(x, [-24, 0], [1, 0]);
  const cat = CATEGORIES[txn.category];
  return (
    <li className="group relative overflow-hidden rounded-2xl">
      <motion.div style={{ opacity: reveal }} className="absolute inset-y-0 right-0 flex w-24 items-center justify-end bg-stamp pr-6 text-white" aria-hidden>
        <Trash2 className="size-5" />
      </motion.div>
      <motion.div
        drag="x"
        style={{ x }}
        dragConstraints={{ left: -96, right: 0 }}
        dragElastic={0.1}
        onDragStart={() => (dragged.current = true)}
        onDragEnd={(_, info) => {
          if (info.offset.x < -70) onDelete();
          setTimeout(() => (dragged.current = false), 50);
        }}
        className="relative flex items-center bg-card"
      >
        <button
          onClick={() => !dragged.current && onEdit()}
          className="flex min-w-0 flex-1 items-center gap-3 px-3 py-2.5 text-left"
          aria-label={`编辑 ${txn.merchant} ${yuan(txn.amountCents)} 元`}
        >
          <span className="grid size-10 shrink-0 place-items-center rounded-xl bg-secondary text-xl" aria-hidden>
            {cat.emoji}
          </span>
          <span className="min-w-0 flex-1">
            <span className="block truncate text-[15px] font-semibold text-ink">{txn.merchant}</span>
            <span className="block text-[12px] text-ink-2">
              {hhmm(txn.occurredAt)} · {cat.label}
              {txn.source === "CSV" && " · 导入"}
            </span>
          </span>
          <span className={`thermal shrink-0 text-[15px] font-bold ${txn.direction === "IN" ? "text-good" : "text-ink"}`}>
            {txn.direction === "IN" ? "+" : "-"}
            {yuan(txn.amountCents)}
          </span>
        </button>
        <button
          onClick={onDelete}
          aria-label={`删除 ${txn.merchant}`}
          className="mr-2 grid size-8 shrink-0 place-items-center rounded-full text-ink-2 pointer-events-none opacity-0 transition hover:bg-secondary hover:text-stamp focus-visible:pointer-events-auto focus-visible:opacity-100 [@media(hover:hover)]:group-hover:pointer-events-auto [@media(hover:hover)]:group-hover:opacity-100"
        >
          <Trash2 className="size-4" />
        </button>
      </motion.div>
    </li>
  );
}

export function LedgerPage() {
  const { txns, now, isLoading, isError, refetch, remove, restore } = useLedger();
  const entry = useEntry();
  const groups = useMemo(() => groupByDay(txns), [txns]);

  const del = async (t: Txn) => {
    await remove(t.id);
    toast(`已删除「${t.merchant}」`, {
      action: { label: "撤销", onClick: () => void restore(t).then(() => toast.success("已恢复")) },
      duration: 5000,
    });
  };

  return (
    <>
      <PageHeader
        title="账本"
        subtitle={txns.length ? `共 ${txns.length} 笔 · 左滑删除，点一下编辑` : undefined}
        right={
          <div className="flex gap-2">
            <button onClick={() => entry.open({ tab: "import" })} aria-label="导入账单" className="grid size-9 place-items-center rounded-full bg-card text-ink-2 shadow-sm active:scale-95">
              <FileUp className="size-4" />
            </button>
            <button onClick={() => entry.open()} aria-label="记一笔" className="grid size-9 place-items-center rounded-full bg-coral text-white shadow-sm active:scale-95">
              <Plus className="size-5" />
            </button>
          </div>
        }
      />
      {isLoading ? (
        <LoadingState />
      ) : isError ? (
        <ErrorState onRetry={refetch} />
      ) : groups.length === 0 ? (
        <EmptyState
          emoji="📒"
          title="账本还是空的"
          hint="记第一笔，或者导入支付宝/微信账单"
          action={
            <div className="flex gap-2">
              <button onClick={() => entry.open()} className="rounded-full bg-coral px-5 py-2 text-sm font-bold text-white">
                记一笔
              </button>
              <button onClick={() => entry.open({ tab: "import" })} className="rounded-full bg-card px-5 py-2 text-sm font-bold text-ink shadow-sm">
                导入账单
              </button>
            </div>
          }
        />
      ) : (
        <div className="pb-6">
          {groups.map((g) => (
            <section key={g.key} aria-label={g.key}>
              <div className="sticky top-[68px] z-10 flex items-baseline justify-between bg-canvas/95 px-4 py-1.5 backdrop-blur">
                <h2 className="text-[13px] font-extrabold text-ink">{relativeDayLabel(g.dayStart, now)}</h2>
                <span className="thermal text-[12px] text-ink-2">支出 ¥{yuan(g.totalOut)}</span>
              </div>
              <ul className="space-y-1.5 px-4 pb-2">
                {g.items.map((t) => (
                  <Row key={t.id} txn={t} onEdit={() => entry.open({ initial: t, editing: true })} onDelete={() => void del(t)} />
                ))}
              </ul>
            </section>
          ))}
          <p className="px-4 pt-2 text-center text-[11px] text-ink-2">到底啦～</p>
        </div>
      )}
    </>
  );
}
