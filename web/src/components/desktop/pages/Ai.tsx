import { ArrowUp, Plus, Sparkles, Trash2, Upload } from "lucide-react";
import { motion, useReducedMotion } from "framer-motion";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { toast } from "sonner";
import { templateAssistant } from "@/ai/assistant";
import { CATEGORIES, type Category, type Txn, startOfDay } from "@/domain/types";
import { buildWeeklyReport } from "@/logic/weeklyReport";
import { decodeBillBytes, parseBillCsv } from "@/logic/csv";
import { detectSubscriptions, cycleLabel } from "@/logic/subscriptions";
import { simulateRegret } from "@/logic/regret";
import { addDays, hhmm, weekStartOf } from "@/logic/time";
import { useLedger } from "@/services/LedgerProvider";
import { cn } from "@/lib/utils";
import { ReceiptPaper } from "@/features/receipt/ReceiptPaper";
import { type DraftTxn } from "@/logic/quickAdd";
import { MiniBars, type Point } from "../charts";
import { money } from "../format";
import { useDayDoc } from "../receiptDoc";
import { seriesOf } from "../statsModel";
import { Button, Card, EmptyState, Hint, Kbd } from "../ui";
import { s } from "../strings";
import { useShell } from "../Shell";

type Card =
  | { kind: "drafts"; drafts: DraftTxn[]; saved: boolean }
  | { kind: "receipt" }
  | { kind: "weekly" }
  | { kind: "subs" }
  | { kind: "regret"; targetCategory: Category | null; pct: number; months: 1 | 3 | 6 }
  | { kind: "import" };

type RefSpec =
  | { kind: "chart7" }
  | { kind: "chartWeek" }
  | { kind: "entries"; ids: string[] }
  | { kind: "receipt" }
  | { kind: "subs" }
  | { kind: "weekly" };

interface Msg {
  id: string;
  role: "ai" | "user";
  text: string;
  chips?: string[];
  card?: Card;
  refs?: RefSpec[];
}

/** Chat survives page switches (module scope), same rule as the phone chat. */
const store: { msgs: Msg[] } = { msgs: [] };
let seq = 0;
const nid = () => `dm${Date.now()}-${++seq}`;

function Mark() {
  return (
    <span aria-hidden className="mt-[2px] grid size-6 shrink-0 place-items-center rounded-full bg-d-fill text-[11px] font-semibold text-d-ink">
      票
    </span>
  );
}

function Facts({ rows }: { rows: { label: string; value: string }[] }) {
  return (
    <dl className="mt-2 space-y-1.5 rounded-lg bg-d-fill/60 px-3 py-2.5">
      {rows.map((r) => (
        <div key={r.label} className="flex items-baseline justify-between gap-3 text-[12.5px]">
          <dt className="text-d-ink-2">{r.label}</dt>
          <dd className="num text-d-ink">{r.value}</dd>
        </div>
      ))}
    </dl>
  );
}

export function AiPage() {
  const { txns, now, add } = useLedger();
  const shell = useShell();
  const reduce = useReducedMotion() ?? false;
  const today = startOfDay(now);
  const todayDoc = useDayDoc(today);
  const fileRef = useRef<HTMLInputElement>(null);
  const [msgs, setMsgs] = useState<Msg[]>(store.msgs);
  const [input, setInput] = useState("");
  const [thinking, setThinking] = useState(false);
  const scrollRef = useRef<HTMLDivElement>(null);
  const stick = useRef(true);

  useEffect(() => {
    store.msgs = msgs;
  }, [msgs]);

  // the assistant speaks first, once
  useEffect(() => {
    if (msgs.length > 0) return;
    const o = templateAssistant.opening(txns, now);
    setMsgs([{ id: nid(), role: "ai", text: o.text, chips: o.chips }]);
  }, [msgs.length, txns, now]);

  useEffect(() => {
    const el = scrollRef.current;
    if (el && stick.current) el.scrollTop = el.scrollHeight;
  }, [msgs, thinking]);

  const patch = useCallback((id: string, fn: (m: Msg) => Msg) => setMsgs((ms) => ms.map((m) => (m.id === id ? fn(m) : m))), []);

  const send = useCallback(
    (raw: string) => {
      const text = raw.trim();
      if (!text || thinking) return;
      setInput("");
      stick.current = true;
      const intent = templateAssistant.understand(text, now);
      setMsgs((ms) => [...ms, { id: nid(), role: "user", text }]);
      const reply = (r: Omit<Msg, "id" | "role">) => {
        setThinking(true);
        window.setTimeout(() => {
          setThinking(false);
          setMsgs((ms) => [...ms, { id: nid(), role: "ai", ...r }]);
        }, 420);
      };
      switch (intent.kind) {
        case "ADD": {
          reply({ text: `听到了 ${intent.drafts.length} 笔，确认之后才会真的记下。`, card: { kind: "drafts", drafts: intent.drafts, saved: false } });
          break;
        }
        case "TODAY_SPEND": {
          const todays = txns.filter((t) => t.direction === "OUT" && t.occurredAt >= today);
          const total = todays.reduce((sum, t) => sum + t.amountCents, 0);
          reply({
            text: todays.length ? `今天到现在花了 ${money(total)}，共 ${todays.length} 笔。` : "今天一分钱还没花。",
            chips: ["打印小票", "这周周报"],
            refs: [{ kind: "chart7" }, { kind: "entries", ids: todays.slice(-5).map((t) => t.id) }],
          });
          break;
        }
        case "RECEIPT":
          reply({ text: "来啦，今天的小票。", card: { kind: "receipt" }, refs: [{ kind: "receipt" }] });
          break;
        case "WEEKLY":
          reply({ text: "这是本周的汇总。", card: { kind: "weekly" }, refs: [{ kind: "weekly" }, { kind: "chartWeek" }] });
          break;
        case "SUBSCRIPTIONS": {
          const subs = detectSubscriptions(txns, now);
          reply(subs.length ? { text: "这些是看起来规律扣款的订阅，取消要你自己在相应平台操作。", card: { kind: "subs" }, refs: [{ kind: "subs" }] } : { text: "暂时没发现自动续费。" });
          break;
        }
        case "REGRET":
          reply({
            text: `按 ${intent.months} 个月的观察窗口，少花 ${intent.reductionPct}% 会是什么样，只是算术，不是建议。`,
            card: { kind: "regret", targetCategory: intent.target?.kind === "category" ? intent.target.category : null, pct: intent.reductionPct, months: intent.months },
            refs: [{ kind: "chart7" }],
          });
          break;
        case "IMPORT":
          reply({ text: "可以导入支付宝 / 微信的 CSV 账单，导入前会先给你看预览。", card: { kind: "import" } });
          break;
        default:
          reply({ text: "这句我还没听懂，试试直接说「午饭 32 奶茶 15」，或者问我「今天花了多少」。", chips: ["午饭 32 奶茶 15", "今天花了多少", "打印小票"] });
      }
    },
    [now, thinking, txns, today],
  );

  const confirmDrafts = async (m: Msg) => {
    if (m.card?.kind !== "drafts") return;
    const rows: Txn[] = m.card.drafts.map((d) => ({ id: d.id, amountCents: d.amountCents, direction: d.direction, merchant: d.merchant, category: d.category, occurredAt: d.occurredAt, source: d.source }));
    try {
      await add(rows);
      patch(m.id, (x) => (x.card?.kind === "drafts" ? { ...x, card: { ...x.card, saved: true } } : x));
      toast.success(`${s("ai.confirmed")} ${rows.length}`);
    } catch {
      toast.error(s("common.error"));
    }
  };

  const weekly = useMemo(() => buildWeeklyReport(txns, weekStartOf(now)), [txns, now]);
  const lastAi = [...msgs].reverse().find((m) => m.role === "ai");

  const refContent = (ref: RefSpec) => {
    if (ref.kind === "chart7" || ref.kind === "chartWeek") {
      const points: Point[] =
        ref.kind === "chartWeek"
          ? seriesOf("week", { start: weekStartOf(now), end: addDays(weekStartOf(now), 7) }, txns, null)
          : seriesOf("custom", { start: addDays(today, -6), end: addDays(today, 1) }, txns, null);
      const total = points.reduce((sum, p) => sum + p.value, 0);
      return (
        <div key={ref.kind} className="rounded-lg bg-d-fill/50 p-2.5">
          <p className="mb-1.5 flex items-baseline justify-between text-[11.5px] text-d-ink-2">
            <span>{ref.kind === "chartWeek" ? s("ci.weekly") : s("ov.trend")}</span>
            <span className="num text-[11px] text-d-ink-3">{money(total)}</span>
          </p>
          <MiniBars points={points} />
        </div>
      );
    }
    if (ref.kind === "receipt") {
      return (
        <div key="receipt" className="flex justify-center rounded-lg bg-d-fill/50 p-2">
          <ReceiptPaper doc={todayDoc} stamp="shown" />
        </div>
      );
    }
    if (ref.kind === "entries") {
      const rows = ref.ids.map((id) => txns.find((t) => t.id === id)).filter((t): t is Txn => Boolean(t));
      if (rows.length === 0) return null;
      return (
        <div key="entries" className="rounded-lg bg-d-fill/50 p-2">
          <p className="px-1 pb-1 text-[11.5px] text-d-ink-2">{s("ai.tabList")}</p>
          {rows.map((t) => (
            <div key={t.id} className="flex items-center gap-2 px-1 py-1 text-[12px]">
              <span aria-hidden>{CATEGORIES[t.category].emoji}</span>
              <span className="min-w-0 flex-1 truncate text-d-ink">{t.merchant}</span>
              <span className="num text-d-ink">{money(t.amountCents)}</span>
            </div>
          ))}
        </div>
      );
    }
    if (ref.kind === "weekly") {
      return (
        <div key="weekly" className="rounded-lg bg-d-fill/50 p-2.5">
          <Facts
            rows={[
              { label: s("stats.total"), value: money(weekly.totalCents) },
              { label: s("stats.count"), value: `${weekly.txnCount} ${s("ledger.itemUnit")}` },
              ...weekly.topCategories.map((c) => ({ label: CATEGORIES[c.category].label, value: money(c.cents) })),
            ]}
          />
        </div>
      );
    }
    if (ref.kind === "subs") {
      const subs = detectSubscriptions(txns, now);
      return (
        <div key="subs" className="rounded-lg bg-d-fill/50 p-2">
          {subs.slice(0, 5).map((sub) => (
            <div key={sub.merchant} className="flex items-center justify-between gap-2 px-1 py-1 text-[12px]">
              <span className="min-w-0 truncate text-d-ink">{sub.merchant}</span>
              <span className="num shrink-0 text-d-ink-3">{cycleLabel(sub.cycleDays)}</span>
              <span className="num shrink-0 text-d-ink">{money(sub.amountCents)}</span>
            </div>
          ))}
        </div>
      );
    }
    return null;
  };

  const renderCard = (m: Msg) => {
    const c = m.card;
    if (!c) return null;
    if (c.kind === "drafts") return null; // drafts get their own block (with the confirm button)
    if (c.kind === "receipt") {
      return (
        <div className="mt-2 w-fit rounded-lg bg-d-fill/50 p-2">
          <div className="scale-[0.86] origin-top">
            <ReceiptPaper doc={todayDoc} stamp="shown" />
          </div>
        </div>
      );
    }
    if (c.kind === "weekly") {
      return (
        <Facts
          rows={[
            { label: s("stats.total"), value: money(weekly.totalCents) },
            { label: s("stats.previous"), value: money(weekly.prevTotalCents) },
            ...weekly.topCategories.map((row) => ({ label: CATEGORIES[row.category].label, value: money(row.cents) })),
          ]}
        />
      );
    }
    if (c.kind === "subs") {
      const subs = detectSubscriptions(txns, now);
      return (
        <Facts rows={subs.map((sub) => ({ label: `${sub.merchant} · ${cycleLabel(sub.cycleDays)}`, value: money(sub.amountCents) }))} />
      );
    }
    if (c.kind === "regret") {
      const result = simulateRegret({
        txns,
        now,
        target: c.targetCategory ? { kind: "category", category: c.targetCategory } : { kind: "category", category: "DRINK" },
        reductionPct: c.pct,
        windowMonths: c.months,
      });
      return (
        <Facts
          rows={[
            { label: "30 天预计", value: money(result.monthlyCents) },
            { label: `省下 ${c.pct}%`, value: money(result.savedCents) },
            ...result.equivalents.slice(0, 2).map((e) => ({ label: e.label, value: `${e.count}` })),
          ]}
        />
      );
    }
    return (
      <Button className="mt-2" size="sm" icon={Upload} onClick={() => fileRef.current?.click()}>
        {s("ai.import")}
      </Button>
    );
  };

  return (
    <div className="grid grid-cols-12 gap-5 xl:h-[calc(100dvh-var(--d-chrome,88px)-44px)] xl:min-h-[520px]">
      <Card className="col-span-12 flex h-[520px] min-h-0 flex-col xl:col-span-8 xl:h-auto" padded={false} fill>
        <header className="flex h-11 shrink-0 items-center justify-between border-b border-d-hairline px-4">
          <h2 className="flex items-center gap-1.5 text-[13px] font-semibold text-d-ink-2">
            <Sparkles className="size-3.5" strokeWidth={1.9} aria-hidden />
            {s("ai.title")}
          </h2>
          <div className="flex items-center gap-1">
            <Button size="sm" variant="ghost" icon={Trash2} onClick={() => setMsgs([])}>
              {s("ai.clear")}
            </Button>
          </div>
        </header>

        <div
          ref={scrollRef}
          onScroll={(e) => {
            const el = e.currentTarget;
            stick.current = el.scrollHeight - el.scrollTop - el.clientHeight < 60;
          }}
          className="d-scroll min-h-0 flex-1 overflow-y-auto px-5 py-4"
          aria-live="polite"
        >
          <div className="mx-auto max-w-[680px] space-y-5">
            {msgs.map((m) =>
              m.role === "user" ? (
                <div key={m.id} className="flex justify-end">
                  <p className="max-w-[80%] rounded-[18px] bg-d-fill px-3.5 py-2 text-[14px] leading-[22px] text-d-ink">{m.text}</p>
                </div>
              ) : (
                <motion.div key={m.id} initial={reduce ? false : { opacity: 0, y: 6 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.18 }}>
                  <div className="flex items-start gap-2.5">
                    <Mark />
                    <div className="min-w-0 flex-1">
                      <p className="text-[14px] leading-[23px] text-d-ink">{m.text}</p>
                      {m.card?.kind === "drafts" && (
                        <div className="mt-2 rounded-lg bg-d-fill/50 p-2">
                          {m.card.drafts.map((d) => (
                            <div key={d.id} className="flex items-center gap-2 px-1 py-1 text-[12.5px]">
                              <span aria-hidden>{CATEGORIES[d.category].emoji}</span>
                              <span className="min-w-0 flex-1 truncate text-d-ink">{d.merchant}</span>
                              <span className="text-[11.5px] text-d-ink-3">{hhmm(d.occurredAt)}</span>
                              <span className="num text-d-ink">{money(d.amountCents)}</span>
                            </div>
                          ))}
                          <div className="mt-2 flex justify-end gap-2 px-1">
                            {m.card.saved ? (
                              <span className="text-[12px] text-d-positive">{s("ai.confirmed")}</span>
                            ) : (
                              <Button size="sm" variant="primary" onClick={() => void confirmDrafts(m)}>
                                {s("ai.confirm")}
                              </Button>
                            )}
                          </div>
                        </div>
                      )}
                      {renderCard(m)}
                      {m.id === lastAi?.id && m.chips && m.chips.length > 0 && !thinking && (
                        <div className="mt-2 flex flex-wrap gap-1.5">
                          {m.chips.map((chip) => (
                            <button
                              key={chip}
                              type="button"
                              onClick={() => send(chip)}
                              className="press h-6 rounded-md bg-d-fill px-2 text-[12px] text-d-ink-2 hover:text-d-ink"
                            >
                              {chip}
                            </button>
                          ))}
                        </div>
                      )}
                    </div>
                  </div>
                </motion.div>
              ),
            )}
            {thinking && (
              <div className="flex items-center gap-2.5" role="status" aria-label={s("ai.thinking")}>
                <Mark />
                <div className="flex gap-1">
                  {[0, 1, 2].map((i) => (
                    <motion.span
                      key={i}
                      className="size-1.5 rounded-full bg-d-ink-3"
                      animate={reduce ? { opacity: 0.6 } : { opacity: [0.3, 1, 0.3] }}
                      transition={reduce ? { duration: 0 } : { repeat: Infinity, duration: 1, delay: i * 0.16 }}
                    />
                  ))}
                </div>
              </div>
            )}
          </div>
        </div>

        <div className="shrink-0 border-t border-d-hairline p-3">
          <form
            onSubmit={(e) => {
              e.preventDefault();
              send(input);
            }}
            className="flex items-end gap-2 rounded-xl bg-d-fill p-1.5"
          >
            <button
              type="button"
              onClick={() => shell.openEntry()}
              aria-label={s("cm.record")}
              className="press grid size-8 shrink-0 place-items-center rounded-lg text-d-ink-2 hover:text-d-ink"
            >
              <Plus className="size-4" strokeWidth={1.9} aria-hidden />
            </button>
            <textarea
              value={input}
              onChange={(e) => setInput(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === "Enter" && !e.shiftKey) {
                  e.preventDefault();
                  send(input);
                }
              }}
              rows={1}
              placeholder={s("ai.placeholder")}
              aria-label={s("ai.placeholder")}
              className="max-h-32 min-h-8 min-w-0 flex-1 resize-none bg-transparent py-1.5 text-[14px] leading-[20px] text-d-ink outline-none placeholder:text-d-ink-3"
            />
            <button
              type="submit"
              disabled={!input.trim()}
              aria-label={s("ai.send")}
              className="press grid size-8 shrink-0 place-items-center rounded-lg bg-d-ink text-d-canvas disabled:bg-transparent disabled:text-d-ink-3"
            >
              <ArrowUp className="size-4" strokeWidth={2.2} aria-hidden />
            </button>
          </form>
          <p className="mt-1.5 flex items-center gap-1.5 text-[11px] text-d-ink-3">
            <Kbd>Enter</Kbd> {s("ai.send")}
            <Kbd>Shift</Kbd>+<Kbd>Enter</Kbd>
          </p>
        </div>
      </Card>

      <Card className="col-span-12 flex h-[340px] min-h-0 flex-col xl:col-span-4 xl:h-auto" padded={false} fill title={s("ai.refs")}>
        <div className="d-scroll min-h-0 flex-1 space-y-2 overflow-y-auto px-4 pb-4">
          {!lastAi?.refs || lastAi.refs.length === 0 ? (
            <EmptyState icon={Sparkles} title={s("ai.refs")} hint={s("ai.refsEmpty")} className="px-2 py-10" />
          ) : (
            lastAi.refs.map((ref) => (
              <div key={ref.kind}>
                {refContent(ref)}
              </div>
            ))
          )}
          <Hint className="pt-1">{s("st.storageBody")}</Hint>
        </div>
      </Card>

      <input
        ref={fileRef}
        type="file"
        accept=".csv,text/csv"
        className="hidden"
        onChange={async (e) => {
          const file = e.target.files?.[0];
          e.target.value = "";
          if (!file) return;
          const parsed = parseBillCsv(decodeBillBytes(await file.arrayBuffer()));
          if (parsed.txns.length === 0) {
            toast(s("ledger.importEmpty"));
            return;
          }
          const result = await add(parsed.txns);
          toast(`${s("ledger.importDone")} ${result.added}`);
        }}
      />
    </div>
  );
}
