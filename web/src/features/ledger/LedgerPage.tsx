import { AnimatePresence, motion, useMotionValue, useReducedMotion, useTransform } from "framer-motion";
import { t } from "@/lib/strings";
import { FileUp, NotebookPen, Trash2 } from "lucide-react";
import { useEffect, useMemo, useRef } from "react";
import { toast } from "sonner";
import { CATEGORY_ICONS, CATEGORY_TONES } from "@/components/app/categoryIcons";
import { CountUpMoney } from "@/components/app/CountUp";
import { Group, IconButton, IconTile, Money, Row, SectionHeader, XButton } from "@/components/app/kit";
import { PageHeader } from "@/components/app/PageHeader";
import { EmptyState, ErrorState, LoadingState } from "@/components/app/States";
import { DUR, EASE_OUT } from "@/lib/motion";
import { cn } from "@/lib/utils";
import { useLedger } from "@/services/LedgerProvider";
import { CATEGORIES, type Txn } from "../../domain/types";
import { startOfDay } from "../../domain/types";
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
function TxnRow({ txn, fresh, reduce, onEdit, onDelete }: { txn: Txn; fresh: boolean; reduce: boolean; onEdit: () => void; onDelete: () => void }) {
  const dragged = useRef(false);
  const x = useMotionValue(0);
  const reveal = useTransform(x, [-24, 0], [1, 0]);
  const cat = CATEGORIES[txn.category];
  const income = txn.direction === "IN";
  return (
    <motion.div
      layout="position"
      initial={fresh && !reduce ? { opacity: 0, y: 8 } : false}
      animate={{ opacity: 1, y: 0 }}
      exit={reduce ? { opacity: 0, transition: { duration: 0.01 } } : { opacity: 0, y: -6, transition: { duration: DUR.collapse, ease: EASE_OUT } }}
      transition={{ duration: DUR.row, ease: EASE_OUT }}
      className="group relative overflow-hidden"
    >
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
          leading={<IconTile icon={CATEGORY_ICONS[txn.category]} tone={CATEGORY_TONES[txn.category]} />}
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
    </motion.div>
  );
}

/** Hero: this month's spend as one big number, plus the last 7 days as a quiet bar strip (today in ink). */
function MonthHero({ txns, now }: { txns: readonly Txn[]; now: number }) {
  const d = new Date(now);
  const monthStart = new Date(d.getFullYear(), d.getMonth(), 1).getTime();
  const out = txns.filter((x) => x.direction === "OUT");
  const month = out.filter((x) => x.occurredAt >= monthStart).reduce((s, x) => s + x.amountCents, 0);
  const today = startOfDay(now);
  const days = Array.from({ length: 7 }, (_, i) => {
    const from = today - (6 - i) * 86400000;
    return out.filter((x) => x.occurredAt >= from && x.occurredAt < from + 86400000).reduce((s, x) => s + x.amountCents, 0);
  });
  const max = Math.max(...days, 1);
  return (
    <div className="mx-4 mb-1 flex items-end justify-between gap-4 rounded-[20px] bg-surface p-4">
      <div>
        <p className="text-[13px] text-ink-2">{d.getMonth() + 1} 月支出</p>
        <CountUpMoney cents={month} className="mt-0.5 block text-[40px] font-semibold leading-[46px] text-ink" />
      </div>
      <div className="flex h-12 items-end gap-[5px]" aria-hidden>
        {days.map((v, i) => (
          <span key={i} className={cn("w-[7px] rounded-full", i === 6 ? "bg-ink" : "bg-fill")} style={{ height: `${Math.max(12, (v / max) * 100)}%` }} />
        ))}
      </div>
    </div>
  );
}

export function LedgerPage() {
  const { txns, now, isLoading, isError, refetch, remove, restore } = useLedger();
  const entry = useEntry();
  const groups = useMemo(() => groupByDay(txns), [txns]);
  const reduce = useReducedMotion() ?? false;
  // Only rows added after the first paint rise in; existing rows are already there and must not re-animate.
  const seen = useRef<Set<string> | null>(null);
  if (!seen.current) seen.current = new Set(txns.map((t) => t.id));
  const fresh = useMemo(() => new Set(txns.filter((t) => !seen.current!.has(t.id)).map((t) => t.id)), [txns]);
  useEffect(() => {
    for (const t of txns) seen.current!.add(t.id);
  }, [txns]);

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
        title={t("nav.ledger")}
        right={
          <>
            <IconButton icon={FileUp} label="导入账单" onClick={() => entry.open({ tab: "import" })} />
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
          <MonthHero txns={txns} now={now} />
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
                <AnimatePresence initial={false}>
                  {g.items.map((t) => (
                    <TxnRow key={t.id} txn={t} fresh={fresh.has(t.id)} reduce={reduce} onEdit={() => entry.open({ initial: t, editing: true })} onDelete={() => void del(t)} />
                  ))}
                </AnimatePresence>
              </Group>
            </section>
          ))}
          <p className="px-4 pt-6 text-center text-[12px] text-ink-2">到底啦</p>
        </div>
      )}
    </>
  );
}
