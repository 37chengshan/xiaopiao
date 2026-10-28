import { motion, useMotionValue, useTransform } from "framer-motion";
import { FileUp, NotebookPen, Plus, Trash2 } from "lucide-react";
import { useMemo, useRef } from "react";
import { toast } from "sonner";
import { CATEGORY_ICONS } from "@/components/app/categoryIcons";
import { Group, IconButton, IconTile, Money, Row, SectionHeader, XButton } from "@/components/app/kit";
import { PageHeader } from "@/components/app/PageHeader";
import { EmptyState, ErrorState, LoadingState } from "@/components/app/States";
import { cn } from "@/lib/utils";
import { useLedger } from "@/services/LedgerProvider";
import { CATEGORIES, type Txn } from "../../domain/types";
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

/** One ledger row. Swipe left to reveal/trigger delete (1:1 tracking, snaps back if released early); tap to edit. */
function TxnRow({ txn, onEdit, onDelete }: { txn: Txn; onEdit: () => void; onDelete: () => void }) {
  const dragged = useRef(false);
  const x = useMotionValue(0);
  const reveal = useTransform(x, [-24, 0], [1, 0]);
  const cat = CATEGORIES[txn.category];
  const income = txn.direction === "IN";
  return (
    <div className="group relative overflow-hidden">
      <motion.div style={{ opacity: reveal }} className="absolute inset-y-0 right-0 flex w-24 items-center justify-center gap-1 bg-negative text-[13px] font-medium text-on-clay" aria-hidden>
        <Trash2 className="size-[17px]" strokeWidth={1.9} />
        删除
      </motion.div>
      <motion.div
        drag="x"
        style={{ x, touchAction: "pan-y" }}
        dragConstraints={{ left: -96, right: 0 }}
        dragElastic={0.1}
        dragSnapToOrigin
        onDragStart={() => (dragged.current = true)}
        onDragEnd={(_, info) => {
          if (info.offset.x < -70) onDelete();
          setTimeout(() => (dragged.current = false), 50);
        }}
        className="relative flex items-center bg-surface"
      >
        <Row
          className="flex-1"
          leading={<IconTile icon={CATEGORY_ICONS[txn.category]} />}
          title={txn.merchant}
          subtitle={`${hhmm(txn.occurredAt)} · ${cat.label}${txn.source === "CSV" ? " · 导入" : ""}`}
          trailing={<Money cents={income ? txn.amountCents : -txn.amountCents} sign={income} className={cn(income && "text-positive")} />}
          onClick={() => !dragged.current && onEdit()}
        />
        <button
          type="button"
          onClick={onDelete}
          aria-label={`删除 ${txn.merchant}`}
          className="pointer-events-none mr-2 grid size-8 shrink-0 place-items-center rounded-full text-ink-3 opacity-0 transition-opacity focus-visible:pointer-events-auto focus-visible:opacity-100 [@media(hover:hover)]:group-hover:pointer-events-auto [@media(hover:hover)]:group-hover:opacity-100 [@media(hover:hover)]:hover:text-negative"
        >
          <Trash2 className="size-4" strokeWidth={1.75} />
        </button>
      </motion.div>
    </div>
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
          <>
            <IconButton icon={FileUp} label="导入账单" onClick={() => entry.open({ tab: "import" })} />
            <IconButton icon={Plus} label="记一笔" tone="ink" onClick={() => entry.open()} />
          </>
        }
      />
      {isLoading ? (
        <LoadingState />
      ) : isError ? (
        <ErrorState onRetry={refetch} />
      ) : groups.length === 0 ? (
        <EmptyState
          icon={NotebookPen}
          title="账本还是空的"
          hint="记第一笔，或者导入支付宝、微信账单"
          action={
            <div className="flex gap-2">
              <XButton variant="primary" size="sm" onClick={() => entry.open()}>
                记一笔
              </XButton>
              <XButton size="sm" onClick={() => entry.open({ tab: "import" })}>
                导入账单
              </XButton>
            </div>
          }
        />
      ) : (
        <div className="pb-6">
          {groups.map((g) => (
            <section key={g.key} aria-label={g.key}>
              <SectionHeader
                action={
                  g.totalOut > 0 ? (
                    <span className="text-[13px] leading-[18px] text-ink-2">
                      支出 <Money cents={g.totalOut} className="text-ink-2" />
                    </span>
                  ) : undefined
                }
              >
                {relativeDayLabel(g.dayStart, now)}
              </SectionHeader>
              <Group inset="icon">
                {g.items.map((t) => (
                  <TxnRow key={t.id} txn={t} onEdit={() => entry.open({ initial: t, editing: true })} onDelete={() => void del(t)} />
                ))}
              </Group>
            </section>
          ))}
          <p className="px-4 pt-6 text-center text-[12px] text-ink-2">到底啦</p>
        </div>
      )}
    </>
  );
}
