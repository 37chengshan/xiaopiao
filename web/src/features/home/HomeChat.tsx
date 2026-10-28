import { motion } from "framer-motion";
import { ArrowUp, Plus } from "lucide-react";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { toast } from "sonner";
import { DemoPill } from "@/components/app/DemoPill";
import { Chip, XButton } from "@/components/app/kit";
import { ErrorState, LoadingState } from "@/components/app/States";
import { t } from "@/lib/strings";
import { cn } from "@/lib/utils";
import { useLedger } from "@/services/LedgerProvider";
import { templateAssistant } from "../../ai/assistant";
import { type Txn, startOfDay, yuan } from "../../domain/types";
import { buildDailyReceipt } from "../../logic/dailyReceipt";
import { type DraftTxn } from "../../logic/quickAdd";
import { type RegretTarget, type WindowMonths } from "../../logic/regret";
import { detectSubscriptions } from "../../logic/subscriptions";
import { useEntry } from "../entry/EntryProvider";
import { RegretSimulator } from "../insights/RegretSimulator";
import { Receipt } from "../receipt/Receipt";
import { DraftsCard, SubscriptionSummaryCard, WeeklySummaryCard } from "./cards";
import { TypedText } from "./TypedText";

type Card =
  | { kind: "drafts"; drafts: DraftTxn[]; saved: boolean }
  | { kind: "receipt" }
  | { kind: "weekly" }
  | { kind: "subs" }
  | { kind: "regret"; target: RegretTarget | null; pct: number; months: WindowMonths }
  | { kind: "import" };

interface Msg {
  id: string;
  role: "ai" | "user";
  text: string;
  card?: Card;
  chips?: string[];
}

/** Chat survives tab switches (module scope) but not reloads; keyed by ledger mode. */
const store: { mode: string | null; msgs: Msg[]; seen: Set<string> } = { mode: null, msgs: [], seen: new Set() };
let seq = 0;
const nid = () => `m${Date.now()}-${++seq}`;

/** Claude-style assistant turn: a small serif mark, plain text on the canvas, cards underneath at full width. */
function Mark() {
  return (
    <span className="mt-[3px] grid size-6 shrink-0 place-items-center rounded-full bg-fill font-serif text-[13px] font-semibold text-ink" aria-hidden>
      票
    </span>
  );
}

function AiMessage({ msg, children }: { msg: Msg; children?: React.ReactNode }) {
  const [typed, setTyped] = useState(store.seen.has(msg.id));
  return (
    <div>
      <div className="flex items-start gap-2.5">
        <Mark />
        <p className="min-w-0 flex-1 text-[16px] leading-[26px] text-ink">
          <TypedText
            text={msg.text}
            skip={store.seen.has(msg.id)}
            onDone={() => {
              store.seen.add(msg.id);
              setTyped(true);
            }}
          />
        </p>
      </div>
      {typed && children && <div className="mt-3">{children}</div>}
    </div>
  );
}

export function HomeChat() {
  const ledger = useLedger();
  const { txns, now, isLoading, isError, refetch, mode, add } = ledger;
  const entry = useEntry();
  const [msgs, setMsgs] = useState<Msg[]>(store.mode === mode ? store.msgs : []);
  const [input, setInput] = useState("");
  const [thinking, setThinking] = useState(false);
  const scrollRef = useRef<HTMLDivElement>(null);
  const dockRef = useRef<HTMLDivElement>(null);
  const [dockH, setDockH] = useState(120);
  const [scrolled, setScrolled] = useState(false);

  useEffect(() => {
    store.mode = mode;
    store.msgs = msgs;
  }, [mode, msgs]);

  // AI speaks first, once data is ready
  useEffect(() => {
    if (isLoading || isError || msgs.length > 0 || mode === null) return;
    const o = templateAssistant.opening(txns, now);
    setMsgs([{ id: nid(), role: "ai", text: o.text, chips: o.chips }]);
  }, [isLoading, isError, msgs.length, mode, txns, now]);

  // keep the newest content in view, including cards that grow after they mount (receipt, typing)
  const stick = useRef(true);
  const contentRef = useRef<HTMLDivElement>(null);
  useEffect(() => {
    stick.current = true;
  }, [msgs.length]);
  useEffect(() => {
    const el = contentRef.current;
    const box = scrollRef.current;
    if (!el || !box) return;
    const ro = new ResizeObserver(() => {
      if (stick.current) box.scrollTo({ top: box.scrollHeight });
    });
    ro.observe(el);
    return () => ro.disconnect();
  }, [isLoading, isError]);

  // the composer dock floats over the messages; keep the last message clear of it
  useEffect(() => {
    const el = dockRef.current;
    if (!el) return;
    const ro = new ResizeObserver(() => setDockH(el.offsetHeight));
    ro.observe(el);
    return () => ro.disconnect();
  }, [isLoading, isError]);

  const patch = useCallback((id: string, fn: (m: Msg) => Msg) => setMsgs((ms) => ms.map((m) => (m.id === id ? fn(m) : m))), []);

  const send = useCallback(
    (raw: string) => {
      const text = raw.trim();
      if (!text || thinking) return;
      setInput("");
      const intent = templateAssistant.understand(text, now);
      const reply = (r: Omit<Msg, "id" | "role">) => {
        setThinking(true);
        setTimeout(() => {
          setThinking(false);
          setMsgs((ms) => [...ms, { id: nid(), role: "ai", ...r }]);
        }, 450);
      };
      setMsgs((ms) => [...ms, { id: nid(), role: "user", text }]);
      switch (intent.kind) {
        case "ADD": {
          const drafts = intent.drafts.map((d) => ({ ...d, source: d.source }));
          reply({ text: `听到了 ${drafts.length} 笔，对不对呀？确认之后才会真的记下～`, card: { kind: "drafts", drafts, saved: false } });
          break;
        }
        case "TODAY_SPEND": {
          const todays = txns.filter((t) => t.direction === "OUT" && t.occurredAt >= startOfDay(now));
          const total = todays.reduce((s, t) => s + t.amountCents, 0);
          reply({
            text: todays.length ? `今天到现在花了 ¥${yuan(total)}，共 ${todays.length} 笔。最近一笔是「${todays[todays.length - 1].merchant}」。` : "今天一分钱没花，钱包在睡大觉",
            chips: ["打印小票", "这周周报"],
          });
          break;
        }
        case "RECEIPT":
          reply({ text: "来啦，今天的小票正在出纸～", card: { kind: "receipt" } });
          break;
        case "WEEKLY":
          reply({ text: "本周的颁奖典礼，请看～", card: { kind: "weekly" } });
          break;
        case "SUBSCRIPTIONS":
          if (detectSubscriptions(txns, now).length === 0) reply({ text: "暂时没发现自动续费，干净得很" });
          else reply({ text: "侦探出动！这些是规律扣款的订阅：", card: { kind: "subs" } });
          break;
        case "REGRET":
          reply({ text: "我来算算，只是纯算术，不构成建议哦～", card: { kind: "regret", target: intent.target, pct: intent.reductionPct, months: intent.months } });
          break;
        case "IMPORT":
          reply({ text: "可以导入支付宝/微信的 CSV 账单，会自动去重。", card: { kind: "import" } });
          break;
        default:
          reply({ text: "这个我还没听懂，试试直接说「午饭 32 奶茶 15」，或者问我「今天花了多少」。", chips: ["午饭 32 奶茶 15", "今天花了多少", "打印小票"] });
      }
    },
    [now, thinking, txns],
  );

  const confirmDrafts = async (m: Msg) => {
    if (m.card?.kind !== "drafts") return;
    const rows: Txn[] = m.card.drafts.map((d) => ({ id: d.id, amountCents: d.amountCents, direction: d.direction, merchant: d.merchant, category: d.category, occurredAt: d.occurredAt, source: d.source }));
    try {
      await add(rows);
      patch(m.id, (x) => (x.card?.kind === "drafts" ? { ...x, card: { ...x.card, saved: true } } : x));
      toast.success(`记好 ${rows.length} 笔`);
    } catch {
      toast.error("没记上，再试一次");
    }
  };

  const receipt = useMemo(() => buildDailyReceipt(txns, startOfDay(now), now), [txns, now]);

  const renderCard = (m: Msg) => {
    const c = m.card;
    if (!c) return null;
    switch (c.kind) {
      case "drafts":
        return (
          <DraftsCard
            drafts={c.drafts}
            now={now}
            saved={c.saved}
            onConfirm={() => void confirmDrafts(m)}
            onChange={(d) => patch(m.id, (x) => (x.card?.kind === "drafts" ? { ...x, card: { ...x.card, drafts: x.card.drafts.map((o) => (o.id === d.id ? d : o)) } } : x))}
            onRemove={(id) => patch(m.id, (x) => (x.card?.kind === "drafts" ? { ...x, card: { ...x.card, drafts: x.card.drafts.filter((d) => d.id !== id) } } : x))}
            onEdit={(d) =>
              entry.open({
                initial: { ...d },
                onSave: (t) =>
                  patch(m.id, (x) =>
                    x.card?.kind === "drafts" ? { ...x, card: { ...x.card, drafts: x.card.drafts.map((o) => (o.id === d.id ? { ...o, amountCents: t.amountCents, direction: t.direction, merchant: t.merchant, category: t.category, occurredAt: t.occurredAt } : o)) } } : x,
                  ),
              })
            }
          />
        );
      case "receipt":
        return <div className="rounded-[14px] bg-bg-grouped pb-2"><Receipt receipt={receipt} /></div>;
      case "weekly":
        return <WeeklySummaryCard txns={txns} now={now} />;
      case "subs":
        return <SubscriptionSummaryCard txns={txns} now={now} />;
      case "regret":
        return (
          <div className="-mx-4">
            <RegretSimulator txns={txns} now={now} initialTarget={c.target} initialPct={c.pct} initialMonths={c.months} />
          </div>
        );
      case "import":
        return (
          <XButton size="sm" onClick={() => entry.open({ tab: "import" })}>
            选择账单文件
          </XButton>
        );
    }
  };

  const lastChips = [...msgs].reverse().find((m) => m.role === "ai")?.chips;
  const lastIsAi = msgs[msgs.length - 1]?.role === "ai";

  return (
    <div className="absolute inset-0">
      <div className="absolute inset-x-0 top-0 z-20 flex h-[calc(44px+env(safe-area-inset-top,0px))] items-center justify-between px-4 pt-[env(safe-area-inset-top,0px)]">
        <div
          aria-hidden
          className={cn("material edge-fade-bottom absolute inset-x-0 -bottom-3 top-0 transition-opacity duration-200 ease-[var(--ease-out)]", scrolled ? "opacity-100" : "opacity-0")}
        />
        <h1 className="relative text-[22px] font-semibold leading-[28px] tracking-[-0.015em] text-ink">{new Date(now).toLocaleDateString("zh-CN", { month: "long", day: "numeric", weekday: "short" })}</h1>
        <div className="relative">
          <DemoPill />
        </div>
      </div>
      {isLoading ? (
        <div className="pt-[calc(56px+env(safe-area-inset-top,0px))]">
          <LoadingState rows={3} />
        </div>
      ) : isError ? (
        <div className="pt-[calc(56px+env(safe-area-inset-top,0px))]">
          <ErrorState onRetry={refetch} />
        </div>
      ) : (
        <>
          <div
            ref={scrollRef}
            onScroll={(e) => {
              const b = e.currentTarget;
              stick.current = b.scrollHeight - b.scrollTop - b.clientHeight < 80;
              setScrolled(b.scrollTop > 8);
            }}
            style={{ paddingBottom: `calc(var(--tabbar-h) + ${dockH}px + 8px)` }}
            className="absolute inset-0 overflow-y-auto overscroll-contain px-4 pt-[calc(56px+env(safe-area-inset-top,0px))]"
            aria-live="polite"
          >
            <div ref={contentRef} className="space-y-6">
              {msgs.map((m) =>
                m.role === "user" ? (
                  <motion.div key={m.id} initial={{ opacity: 0, y: 6 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.2, ease: [0.23, 1, 0.32, 1] }} className="flex justify-end">
                    <div className="max-w-[82%] rounded-[18px] bg-surface-2 px-3.5 py-2 text-[16px] leading-[24px] text-ink">{m.text}</div>
                  </motion.div>
                ) : (
                  <AiMessage key={m.id} msg={m}>
                    {renderCard(m)}
                  </AiMessage>
                ),
              )}
              {thinking && (
                <div className="flex items-center gap-2.5" role="status" aria-label="小票正在输入">
                  <Mark />
                  <div className="flex gap-1">
                    {[0, 1, 2].map((i) => (
                      <motion.span key={i} className="size-1.5 rounded-full bg-ink-3" animate={{ opacity: [0.3, 1, 0.3] }} transition={{ repeat: Infinity, duration: 1, delay: i * 0.16 }} />
                    ))}
                  </div>
                </div>
              )}
            </div>
          </div>

          <div ref={dockRef} className="absolute inset-x-0 bottom-[var(--tabbar-h)] z-20 bg-gradient-to-t from-bg from-65% to-transparent pb-[18px] pt-5">
            {lastIsAi && lastChips && !thinking && (
              <div className="hide-scrollbar mb-2 flex gap-2 overflow-x-auto px-4">
                {lastChips.map((c) => (
                  <Chip key={c} onClick={() => send(c)} className="text-ink">
                    {c}
                  </Chip>
                ))}
              </div>
            )}
            <form
              onSubmit={(e) => {
                e.preventDefault();
                send(input);
              }}
              className="mx-3 flex items-center gap-0.5 rounded-[26px] border border-hairline bg-surface p-1 shadow-[0_1px_2px_oklch(0_0_0/5%)]"
            >
              <button type="button" onClick={() => entry.open()} aria-label="手动记一笔" className="grid size-9 shrink-0 place-items-center rounded-full text-ink-2 transition-transform duration-100 active:scale-[0.94]">
                <Plus className="size-5" strokeWidth={1.75} />
              </button>
              <input
                value={input}
                onChange={(e) => setInput(e.target.value)}
                placeholder={t("chat.placeholder")}
                aria-label="消息"
                enterKeyHint="send"
                className="h-9 min-w-0 flex-1 bg-transparent px-1 text-[16px] text-ink outline-none placeholder:text-ink-3"
              />
              <button
                type="submit"
                disabled={!input.trim()}
                aria-label="发送"
                className="grid size-9 shrink-0 place-items-center rounded-full bg-ink text-bg transition-[transform,background-color,color] duration-150 active:scale-[0.94] disabled:bg-fill disabled:text-ink-3 disabled:active:scale-100"
              >
                <ArrowUp className="size-5" strokeWidth={2.25} />
              </button>
            </form>
          </div>
        </>
      )}
    </div>
  );
}
