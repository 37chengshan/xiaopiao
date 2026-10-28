import { motion } from "framer-motion";
import { ArrowUp, Mic, Plus } from "lucide-react";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { toast } from "sonner";
import { PageHeader } from "@/components/app/PageHeader";
import { ErrorState, LoadingState } from "@/components/app/States";
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
import { useSpeech } from "./useSpeech";

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

function AiBubble({ msg, children }: { msg: Msg; children?: React.ReactNode }) {
  const [typed, setTyped] = useState(store.seen.has(msg.id));
  return (
    <div className="flex items-start gap-2">
      <span className="grid size-8 shrink-0 place-items-center rounded-full bg-coral text-base" aria-hidden>
        🧾
      </span>
      <div className="min-w-0 max-w-[88%]">
        <div className="inline-block rounded-2xl rounded-tl-md bg-card px-3.5 py-2.5 text-[15px] leading-relaxed text-ink shadow-sm">
          <TypedText
            text={msg.text}
            skip={store.seen.has(msg.id)}
            onDone={() => {
              store.seen.add(msg.id);
              setTyped(true);
            }}
          />
        </div>
        {typed && children}
      </div>
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

  useEffect(() => {
    scrollRef.current?.scrollTo({ top: scrollRef.current.scrollHeight, behavior: "smooth" });
  }, [msgs.length, thinking]);

  const patch = useCallback((id: string, fn: (m: Msg) => Msg) => setMsgs((ms) => ms.map((m) => (m.id === id ? fn(m) : m))), []);

  const send = useCallback(
    (raw: string, voice = false) => {
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
          const drafts = intent.drafts.map((d) => ({ ...d, source: voice ? ("VOICE" as const) : d.source }));
          reply({ text: `听到了 ${drafts.length} 笔，对不对呀？确认之后才会真的记下～`, card: { kind: "drafts", drafts, saved: false } });
          break;
        }
        case "TODAY_SPEND": {
          const todays = txns.filter((t) => t.direction === "OUT" && t.occurredAt >= startOfDay(now));
          const total = todays.reduce((s, t) => s + t.amountCents, 0);
          reply({
            text: todays.length ? `今天到现在花了 ¥${yuan(total)}，共 ${todays.length} 笔。最近一笔是「${todays[todays.length - 1].merchant}」。` : "今天一分钱没花，钱包在睡大觉 😴",
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
          if (detectSubscriptions(txns, now).length === 0) reply({ text: "暂时没发现自动续费，干净得很 🕵️" });
          else reply({ text: "侦探出动！这些是规律扣款的订阅：", card: { kind: "subs" } });
          break;
        case "REGRET":
          reply({ text: "我来算算，只是纯算术，不构成建议哦～", card: { kind: "regret", target: intent.target, pct: intent.reductionPct, months: intent.months } });
          break;
        case "IMPORT":
          reply({ text: "可以导入支付宝/微信的 CSV 账单，会自动去重。", card: { kind: "import" } });
          break;
        default:
          reply({ text: "这个我还没听懂 😅 试试直接说「午饭 32 奶茶 15」，或者问我「今天花了多少」。", chips: ["午饭 32 奶茶 15", "今天花了多少", "打印小票"] });
      }
    },
    [now, thinking, txns],
  );

  const speech = useSpeech(
    (t) => setInput(t),
    (t) => {
      send(t, true);
    },
    (err) => {
      if (err === "unsupported") {
        setMsgs((ms) => [...ms, { id: nid(), role: "ai", text: "这个浏览器暂时不支持语音输入 🎙️ 换 Chrome / Safari 试试，或者直接打字告诉我～" }]);
      } else if (err === "not-allowed" || err === "service-not-allowed") toast.error("没有麦克风权限，可以在浏览器设置里打开");
      else if (err !== "no-speech" && err !== "aborted") toast.error("语音没听清，再说一次吧");
    },
  );

  const confirmDrafts = async (m: Msg) => {
    if (m.card?.kind !== "drafts") return;
    const rows: Txn[] = m.card.drafts.map((d) => ({ id: d.id, amountCents: d.amountCents, direction: d.direction, merchant: d.merchant, category: d.category, occurredAt: d.occurredAt, source: d.source }));
    try {
      await add(rows);
      patch(m.id, (x) => (x.card?.kind === "drafts" ? { ...x, card: { ...x.card, saved: true } } : x));
      toast.success(`记好 ${rows.length} 笔 ✅`);
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
        return <div className="-mx-2 mt-2 rounded-3xl bg-secondary/60 pb-2"><Receipt receipt={receipt} /></div>;
      case "weekly":
        return <WeeklySummaryCard txns={txns} now={now} />;
      case "subs":
        return <SubscriptionSummaryCard txns={txns} now={now} />;
      case "regret":
        return (
          <div className="mt-2 w-[min(100%,22rem)]">
            <RegretSimulator txns={txns} now={now} initialTarget={c.target} initialPct={c.pct} initialMonths={c.months} />
          </div>
        );
      case "import":
        return (
          <button onClick={() => entry.open({ tab: "import" })} className="mt-2 rounded-xl bg-coral px-4 py-2 text-sm font-bold text-white active:scale-95">
            选择账单文件
          </button>
        );
    }
  };

  const lastChips = [...msgs].reverse().find((m) => m.role === "ai")?.chips;
  const lastIsAi = msgs[msgs.length - 1]?.role === "ai";

  return (
    <div className="flex h-full flex-col">
      <PageHeader title="今日" subtitle="我先开口，你来确认" />
      {isLoading ? (
        <LoadingState rows={3} />
      ) : isError ? (
        <ErrorState onRetry={refetch} />
      ) : (
        <>
          <div ref={scrollRef} className="min-h-0 flex-1 space-y-3 overflow-y-auto px-4 pb-3 pt-2" aria-live="polite">
            {msgs.map((m) =>
              m.role === "user" ? (
                <motion.div key={m.id} initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }} className="flex justify-end">
                  <div className="max-w-[80%] rounded-2xl rounded-br-md bg-coral px-3.5 py-2.5 text-[15px] text-white shadow-sm">{m.text}</div>
                </motion.div>
              ) : (
                <AiBubble key={m.id} msg={m}>
                  {renderCard(m)}
                </AiBubble>
              ),
            )}
            {thinking && (
              <div className="flex items-center gap-2" role="status" aria-label="小票正在输入">
                <span className="grid size-8 place-items-center rounded-full bg-coral text-base" aria-hidden>
                  🧾
                </span>
                <div className="flex gap-1 rounded-2xl bg-card px-3.5 py-3 shadow-sm">
                  {[0, 1, 2].map((i) => (
                    <motion.span key={i} className="size-1.5 rounded-full bg-ink-2" animate={{ y: [0, -4, 0] }} transition={{ repeat: Infinity, duration: 0.7, delay: i * 0.12 }} />
                  ))}
                </div>
              </div>
            )}
          </div>

          {lastIsAi && lastChips && !thinking && (
            <div className="hide-scrollbar flex shrink-0 gap-1.5 overflow-x-auto px-4 pb-2">
              {lastChips.map((c) => (
                <button key={c} onClick={() => send(c)} className="shrink-0 rounded-full border border-hairline bg-card px-3 py-1.5 text-[13px] font-semibold text-ink active:scale-95">
                  {c}
                </button>
              ))}
            </div>
          )}

          <form
            onSubmit={(e) => {
              e.preventDefault();
              send(input);
            }}
            className="flex shrink-0 items-center gap-2 border-t border-hairline bg-canvas px-3 py-2.5"
          >
            <button type="button" onClick={() => entry.open()} aria-label="手动记一笔" className="grid size-10 shrink-0 place-items-center rounded-full bg-card text-ink shadow-sm active:scale-95">
              <Plus className="size-5" />
            </button>
            <input
              value={input}
              onChange={(e) => setInput(e.target.value)}
              placeholder={speech.listening ? "正在听…请说话" : "说点什么，例如：午饭 32 奶茶 15"}
              aria-label="消息"
              enterKeyHint="send"
              className="h-10 min-w-0 flex-1 rounded-full border border-hairline bg-card px-4 text-[15px] outline-none focus:border-coral"
            />
            {input.trim() ? (
              <button type="submit" aria-label="发送" className="grid size-10 shrink-0 place-items-center rounded-full bg-coral text-white active:scale-95">
                <ArrowUp className="size-5" />
              </button>
            ) : (
              <button
                type="button"
                onClick={() => (speech.listening ? speech.stop() : speech.start())}
                aria-label={speech.listening ? "停止语音输入" : "语音输入"}
                aria-pressed={speech.listening}
                className={cn("relative grid size-10 shrink-0 place-items-center rounded-full text-white active:scale-95", speech.listening ? "bg-stamp" : "bg-ink")}
              >
                {speech.listening && <span className="absolute inset-0 animate-ping rounded-full bg-stamp/50" aria-hidden />}
                <Mic className="relative size-5" />
              </button>
            )}
          </form>
        </>
      )}
    </div>
  );
}
