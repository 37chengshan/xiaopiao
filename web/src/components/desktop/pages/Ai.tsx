import { ArrowUp, Check, ChevronDown, Copy, Plus, RotateCcw, Sparkles, Square, ThumbsDown, ThumbsUp, Trash2, Upload } from "lucide-react";
import { AnimatePresence, motion } from "framer-motion";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { toast } from "sonner";
import { templateAssistant } from "@/ai/assistant";
import { CATEGORIES, type Category, type Txn, startOfDay } from "@/domain/types";
import { buildWeeklyReport } from "@/logic/weeklyReport";
import { decodeBillBytes, parseBillCsv } from "@/logic/csv";
import { cycleLabel, detectSubscriptions } from "@/logic/subscriptions";
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

type RefSpec = { kind: "chart7" } | { kind: "chartWeek" } | { kind: "entries"; ids: string[] } | { kind: "receipt" } | { kind: "subs" } | { kind: "weekly" };

/** Event phases mirror the agent protocol in docs/AGENT.md: tool_start / tool_end / token / card / done. */
type Phase = "thinking" | "streaming" | "card" | "done" | "stopped" | "error";

interface ToolStep {
  id: string;
  label: string;
  done: boolean;
  summary?: string;
}

interface Msg {
  id: string;
  role: "ai" | "user";
  /** assistant text arrives as chunks so each one can fade in without typewriter jitter */
  chunks?: string[];
  text?: string;
  phase?: Phase;
  tools?: ToolStep[];
  card?: Card;
  refs?: RefSpec[];
}

/** Chat survives page switches (module scope), same rule as the phone chat. */
const store: { msgs: Msg[] } = { msgs: [] };
let seq = 0;
const nid = () => `dm${Date.now()}-${++seq}`;

/** Tokens: digits/percentages/₹ amounts stay whole; CJK and Latin split per character. */
function tokenize(text: string): string[] {
  return text.match(/\d[\d,.]*%?|[A-Za-z]+|\s+|[\s\S]/g) ?? [];
}

/** The repo's tsconfig lib is ES2020, so `Promise.withResolvers` is not typed here. */
function sleep(ms: number) {
  return new Promise<void>((resolve) => {
    window.setTimeout(resolve, ms);
  });
}

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
  const today = startOfDay(now);
  const todayDoc = useDayDoc(today);
  const fileRef = useRef<HTMLInputElement>(null);
  const inputRef = useRef<HTMLTextAreaElement>(null);
  const scrollRef = useRef<HTMLDivElement>(null);
  const stick = useRef(true);

  const [msgs, setMsgs] = useState<Msg[]>(store.msgs);
  const [input, setInput] = useState("");
  const [busy, setBusy] = useState(false);
  const [showJump, setShowJump] = useState(false);
  const [expandedTools, setExpandedTools] = useState<Set<string>>(new Set());
  const [feedback, setFeedback] = useState<Record<string, "up" | "down">>({});
  const [copied, setCopied] = useState<string | null>(null);
  const [elapsed, setElapsed] = useState(0);
  const turn = useRef<{ cancelled: boolean } | null>(null);

  useEffect(() => {
    store.msgs = msgs;
  }, [msgs]);

  // The assistant speaks first, once.
  useEffect(() => {
    if (msgs.length > 0) return;
    const o = templateAssistant.opening(txns, now);
    setMsgs([{ id: nid(), role: "ai", phase: "done", chunks: [o.text], text: o.text }]);
  }, [msgs.length, txns, now]);

  const lastText = msgs[msgs.length - 1]?.chunks?.join("") ?? "";

  // Keep the newest content in view, unless the reader scrolled up.
  useEffect(() => {
    const el = scrollRef.current;
    if (!el || !stick.current) return;
    el.scrollTop = el.scrollHeight;
  }, [msgs.length, lastText]);

  // Thinking longer than 1.5s shows elapsed seconds (§A.5 step 2).
  useEffect(() => {
    const thinking = msgs.some((m) => m.role === "ai" && m.phase === "thinking");
    if (!thinking) {
      setElapsed(0);
      return;
    }
    const start = Date.now();
    const id = window.setInterval(() => setElapsed(Math.floor((Date.now() - start) / 1000)), 500);
    return () => window.clearInterval(id);
  }, [msgs]);

  const patch = useCallback((id: string, fn: (m: Msg) => Msg) => setMsgs((list) => list.map((m) => (m.id === id ? fn(m) : m))), []);

  const jumpToBottom = () => {
    const el = scrollRef.current;
    if (!el) return;
    stick.current = true;
    setShowJump(false);
    el.scrollTo({ top: el.scrollHeight, behavior: "smooth" });
  };

  /** One turn: thinking → tool capsules → streamed tokens → card → done. */
  const runTurn = useCallback(
    async (raw: string) => {
      const text = raw.trim();
      if (!text || busy) return;
      setInput("");
      stick.current = true;
      setShowJump(false);
      inputRef.current?.focus();

      const intent = templateAssistant.understand(text, now);
      const userMsg: Msg = { id: nid(), role: "user", text };
      const aiMsg: Msg = { id: nid(), role: "ai", phase: "thinking", chunks: [], tools: [] };
      setMsgs((list) => [...list, userMsg, aiMsg]);
      setBusy(true);
      const token = { cancelled: false };
      turn.current = token;

      try {
        await sleep(320 + Math.random() * 180);
        if (token.cancelled) return;

        // Plan: which tools the local engine "calls", what they report, and the sentences it streams.
        const subList = detectSubscriptions(txns, now);
        const todaysOut = txns.filter((t) => t.direction === "OUT" && t.occurredAt >= today);
        const todayTotal = todaysOut.reduce((sum, t) => sum + t.amountCents, 0);
        const week = buildWeeklyReport(txns, weekStartOf(now));
        const tools: ToolStep[] = [];
        let answer = "";
        let card: Card | undefined;
        let refs: RefSpec[] = [];
        let summaries: string[] = [];

        switch (intent.kind) {
          case "ADD": {
            tools.push({ id: "draft", label: s("tool.draft"), done: false });
            summaries = [`${intent.drafts.length} 笔`];
            answer = `听到了 ${intent.drafts.length} 笔，确认之后才会真的记下。`;
            card = { kind: "drafts", drafts: intent.drafts, saved: false };
            break;
          }
          case "TODAY_SPEND": {
            tools.push({ id: "today", label: s("tool.today"), done: false });
            summaries = [`${todaysOut.length} 笔`];
            answer = todaysOut.length ? `今天到现在花了 ${money(todayTotal)}，共 ${todaysOut.length} 笔。` : "今天一分钱还没花。";
            refs = [{ kind: "chart7" }, { kind: "entries", ids: todaysOut.slice(-5).map((t) => t.id) }];
            break;
          }
          case "RECEIPT": {
            tools.push({ id: "receipt", label: s("tool.receipt"), done: false });
            summaries = ["已出票"];
            answer = "来啦，今天的小票。";
            card = { kind: "receipt" };
            refs = [{ kind: "receipt" }];
            break;
          }
          case "WEEKLY": {
            tools.push({ id: "week", label: s("tool.week"), done: false }, { id: "peak", label: s("tool.peak"), done: false });
            summaries = [`${week.txnCount} 笔`, week.topCategories[0] ? CATEGORIES[week.topCategories[0].category].label : "—"];
            answer = `这周花了 ${money(week.totalCents)}，${week.txnCount} 笔。`;
            card = { kind: "weekly" };
            refs = [{ kind: "weekly" }, { kind: "chartWeek" }];
            break;
          }
          case "SUBSCRIPTIONS": {
            tools.push({ id: "subs", label: s("tool.subs"), done: false });
            summaries = [`${subList.length} 个`];
            answer = subList.length ? "这些是看起来规律扣款的订阅，取消要你自己在相应平台操作。" : "暂时没发现自动续费。";
            if (subList.length) {
              card = { kind: "subs" };
              refs = [{ kind: "subs" }];
            }
            break;
          }
          case "REGRET": {
            tools.push({ id: "month", label: s("tool.month"), done: false });
            const target = intent.target?.kind === "category" ? intent.target.category : null;
            const result = simulateRegret({
              txns,
              now,
              target: target ? { kind: "category", category: target } : { kind: "category", category: "DRINK" },
              reductionPct: intent.reductionPct,
              windowMonths: intent.months,
            });
            summaries = [`30 天 ${money(result.monthlyCents)}`];
            answer = `按 ${intent.months} 个月的观察窗口，少花 ${intent.reductionPct}% 大概是这个数。只是算术，不是建议。`;
            card = { kind: "regret", targetCategory: target, pct: intent.reductionPct, months: intent.months };
            refs = [{ kind: "chart7" }];
            break;
          }
          case "IMPORT": {
            answer = "可以导入支付宝 / 微信的 CSV 账单，导入前会先给你看预览。";
            card = { kind: "import" };
            break;
          }
          default: {
            answer = "这句我还没听懂，试试直接说「午饭 32 奶茶 15」，或者问我「今天花了多少」。";
          }
        }

        // tool capsules: start, then finish with a one-line result
        for (let i = 0; i < tools.length; i++) {
          if (token.cancelled) return;
          patch(aiMsg.id, (m) => ({ ...m, tools: [...(m.tools ?? []), tools[i]] }));
          await sleep(160 + Math.random() * 220);
          if (token.cancelled) return;
          patch(aiMsg.id, (m) => ({ ...m, tools: (m.tools ?? []).map((t) => (t.id === tools[i].id ? { ...t, done: true, summary: summaries[i] } : t)) }));
        }

        // tokens: 20–40ms each, with a little jitter
        patch(aiMsg.id, (m) => ({ ...m, phase: "streaming" }));
        for (const piece of tokenize(answer)) {
          if (token.cancelled) return;
          patch(aiMsg.id, (m) => ({ ...m, chunks: [...(m.chunks ?? []), piece] }));
          await sleep(20 + Math.random() * 20);
        }
        if (token.cancelled) return;

        await sleep(140);
        if (token.cancelled) return;
        patch(aiMsg.id, (m) => ({ ...m, phase: "card", card, refs }));
        await sleep(320);
        if (token.cancelled) return;
        patch(aiMsg.id, (m) => ({ ...m, phase: "done" }));
      } catch {
        // Cloud failures fall back to the local engine; if even that throws, offer a retry line.
        patch(aiMsg.id, (m) => ({ ...m, phase: "error" }));
      } finally {
        if (!token.cancelled) setBusy(false);
        turn.current = null;
        inputRef.current?.focus();
      }
    },
    [busy, now, patch, today, txns],
  );

  const stop = () => {
    if (!turn.current) return;
    turn.current.cancelled = true;
    setBusy(false);
    setMsgs((list) => list.map((m) => (m.role === "ai" && (m.phase === "thinking" || m.phase === "streaming" || m.phase === "card") ? { ...m, phase: "stopped" } : m)));
  };

  const confirmDrafts = async (m: Msg) => {
    if (m.card?.kind !== "drafts") return;
    const rows: Txn[] = m.card.drafts.map((d) => ({ id: d.id, amountCents: d.amountCents, direction: d.direction, merchant: d.merchant, category: d.category, occurredAt: d.occurredAt, source: d.source }));
    try {
      await add(rows);
      patch(m.id, (x) => (x.card?.kind === "drafts" ? { ...x, card: { ...x.card, saved: true } } : x));
      toast(`${s("ai.confirmed")} ${rows.length}`);
    } catch {
      toast(s("common.error"));
    }
  };

  const copyAnswer = async (m: Msg) => {
    const text = (m.chunks ?? []).join("");
    try {
      await navigator.clipboard.writeText(text);
      setCopied(m.id);
      window.setTimeout(() => setCopied(null), 1200);
    } catch {
      toast(s("common.error"));
    }
  };

  const lastAi = [...msgs].reverse().find((m) => m.role === "ai");
  const lastUser = [...msgs].reverse().find((m) => m.role === "user");
  const weekly = useMemo(() => buildWeeklyReport(txns, weekStartOf(now)), [txns, now]);

  const cardNode = (m: Msg) => {
    const c = m.card;
    if (!c) return null;
    if (c.kind === "drafts") {
      return (
        <div className="mt-2 rounded-lg bg-d-fill/50 p-2">
          {c.drafts.map((d) => (
            <div key={d.id} className="flex items-center gap-2 px-1 py-1 text-[12.5px]">
              <span aria-hidden>{CATEGORIES[d.category].emoji}</span>
              <span className="min-w-0 flex-1 truncate text-d-ink">{d.merchant}</span>
              <span className="text-[11.5px] text-d-ink-3">{hhmm(d.occurredAt)}</span>
              <span className="num text-d-ink">{money(d.amountCents)}</span>
            </div>
          ))}
          <div className="mt-2 flex justify-end gap-2 px-1">
            {c.saved ? (
              <span className="text-[12px] text-d-positive">{s("ai.confirmed")}</span>
            ) : (
              <Button size="sm" variant="primary" onClick={() => void confirmDrafts(m)}>
                {s("ai.confirm")}
              </Button>
            )}
          </div>
        </div>
      );
    }
    if (c.kind === "receipt") {
      return (
        <div className="mt-2 w-fit rounded-lg bg-d-fill/50 p-2">
          <div className="origin-top scale-[0.86]">
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
      return <Facts rows={subs.map((sub) => ({ label: `${sub.merchant} · ${cycleLabel(sub.cycleDays)}`, value: money(sub.amountCents) }))} />;
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

  const refContent = (ref: RefSpec, highlight: boolean) => {
    const ring = highlight ? "ring-1 ring-d-accent/25" : "";
    if (ref.kind === "chart7" || ref.kind === "chartWeek") {
      const points: Point[] =
        ref.kind === "chartWeek"
          ? seriesOf("week", { start: weekStartOf(now), end: addDays(weekStartOf(now), 7) }, txns, null)
          : seriesOf("custom", { start: addDays(today, -6), end: addDays(today, 1) }, txns, null);
      const total = points.reduce((sum, p) => sum + p.value, 0);
      return (
        <div key={ref.kind} className={cn("rounded-lg bg-d-fill/50 p-2.5 transition-shadow", ring)}>
          <p className="mb-1.5 flex items-baseline justify-between text-[11.5px] text-d-ink-2">
            <span>{ref.kind === "chartWeek" ? s("ci.weekly") : s("ov.last7")}</span>
            <span className="num text-[11px] text-d-ink-3">{money(total)}</span>
          </p>
          <MiniBars points={points} />
        </div>
      );
    }
    if (ref.kind === "receipt") {
      return (
        <div key="receipt" className={cn("flex justify-center rounded-lg bg-d-fill/50 p-2 transition-shadow", ring)}>
          <ReceiptPaper doc={todayDoc} stamp="shown" />
        </div>
      );
    }
    if (ref.kind === "entries") {
      const rows = ref.ids.map((id) => txns.find((t) => t.id === id)).filter((t): t is Txn => Boolean(t));
      if (rows.length === 0) return null;
      return (
        <div key="entries" className={cn("rounded-lg bg-d-fill/50 p-2 transition-shadow", ring)}>
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
        <div key="weekly" className={cn("rounded-lg bg-d-fill/50 p-2.5 transition-shadow", ring)}>
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
    const subs = detectSubscriptions(txns, now);
    return (
      <div key="subs" className={cn("rounded-lg bg-d-fill/50 p-2 transition-shadow", ring)}>
        {subs.slice(0, 5).map((sub) => (
          <div key={sub.merchant} className="flex items-center justify-between gap-2 px-1 py-1 text-[12px]">
            <span className="min-w-0 truncate text-d-ink">{sub.merchant}</span>
            <span className="num shrink-0 text-d-ink-3">{cycleLabel(sub.cycleDays)}</span>
            <span className="num shrink-0 text-d-ink">{money(sub.amountCents)}</span>
          </div>
        ))}
      </div>
    );
  };

  const toolsRow = (m: Msg) => {
    if (!m.tools || m.tools.length === 0) return null;
    const running = m.phase === "thinking" || m.phase === "streaming";
    const open = running || expandedTools.has(m.id);
    return (
      <div className="mt-2">
        <button
          type="button"
          onClick={() => setExpandedTools((prev) => {
            const next = new Set(prev);
            if (next.has(m.id)) next.delete(m.id);
            else next.add(m.id);
            return next;
          })}
          className="d-press flex h-6 items-center gap-1.5 rounded-full bg-d-fill px-2.5 text-[11.5px] text-d-ink-2 hover:text-d-ink"
          aria-expanded={open}
        >
          <span className="num">{running ? s("ai.toolsRunning") : s("ai.toolsUsed").replace("{n}", String(m.tools.length))}</span>
          <ChevronDown className={cn("size-3 transition-transform", open && "rotate-180")} strokeWidth={2} aria-hidden />
        </button>
        <AnimatePresence initial={false}>
          {open && (
            <motion.ul
              initial={{ height: 0, opacity: 0 }}
              animate={{ height: "auto", opacity: 1 }}
              exit={{ height: 0, opacity: 0 }}
              transition={{ duration: 0.18, ease: [0.23, 1, 0.32, 1] }}
              className="overflow-hidden"
            >
              {m.tools.map((tool) => (
                <li key={tool.id} className="mt-1 flex items-center gap-1.5 text-[11.5px] text-d-ink-3">
                  {tool.done ? (
                    <Check className="size-3 text-d-positive" strokeWidth={2.4} aria-hidden />
                  ) : (
                    <span aria-hidden className="size-3 animate-spin rounded-full border border-d-ink-3 border-t-transparent" />
                  )}
                  <span className="min-w-0 truncate text-d-ink-2">{tool.label}</span>
                  {tool.done && tool.summary && <span className="text-d-ink-3">· {tool.summary}</span>}
                </li>
              ))}
            </motion.ul>
          )}
        </AnimatePresence>
      </div>
    );
  };

  const actionsRow = (m: Msg, last: boolean) => {
    if (m.phase !== "done" || !last) return null;
    const fb = feedback[m.id];
    return (
      <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} transition={{ duration: 0.16 }} className="mt-2 flex items-center gap-1 text-[12px] text-d-ink-3">
        <button type="button" onClick={() => void copyAnswer(m)} className="d-press flex h-6 items-center gap-1 rounded-md px-1.5 hover:bg-d-fill hover:text-d-ink">
          {copied === m.id ? <Check className="size-3" strokeWidth={2.2} aria-hidden /> : <Copy className="size-3" strokeWidth={1.9} aria-hidden />}
          {copied === m.id ? s("ai.copied") : s("ai.copy")}
        </button>
        <button type="button" onClick={() => lastUser && void runTurn(lastUser.text ?? "")} className="d-press flex h-6 items-center gap-1 rounded-md px-1.5 hover:bg-d-fill hover:text-d-ink">
          <RotateCcw className="size-3" strokeWidth={1.9} aria-hidden />
          {s("ai.regenerate")}
        </button>
        <button type="button" aria-pressed={fb === "up"} onClick={() => setFeedback((f) => ({ ...f, [m.id]: "up" }))} className={cn("d-press grid size-6 place-items-center rounded-md hover:bg-d-fill hover:text-d-ink", fb === "up" && "text-d-positive")}>
          <ThumbsUp className="size-3" strokeWidth={1.9} aria-hidden />
        </button>
        <button type="button" aria-pressed={fb === "down"} onClick={() => setFeedback((f) => ({ ...f, [m.id]: "down" }))} className={cn("d-press grid size-6 place-items-center rounded-md hover:bg-d-fill hover:text-d-ink", fb === "down" && "text-d-negative")}>
          <ThumbsDown className="size-3" strokeWidth={1.9} aria-hidden />
        </button>
      </motion.div>
    );
  };

  const streaming = msgs.some((m) => m.role === "ai" && (m.phase === "thinking" || m.phase === "streaming" || m.phase === "card"));

  return (
    <div className="grid grid-cols-12 gap-5 xl:h-[calc(100dvh-var(--d-chrome,88px)-44px)] xl:min-h-[520px]">
      <Card className="col-span-12 flex h-[520px] min-h-0 flex-col xl:col-span-8 xl:h-auto" padded={false} fill>
        <header className="flex h-11 shrink-0 items-center justify-between border-b border-d-hairline px-4">
          <h2 className="flex items-center gap-1.5 text-[13px] font-semibold text-d-ink-2">
            <Sparkles className="size-3.5" strokeWidth={1.9} aria-hidden />
            {s("ai.title")}
          </h2>
          <Button size="sm" variant="ghost" icon={Trash2} onClick={() => setMsgs([])}>
            {s("ai.clear")}
          </Button>
        </header>

        <div className="relative min-h-0 flex-1">
          <div
            ref={scrollRef}
            onScroll={(e) => {
              const el = e.currentTarget;
              const near = el.scrollHeight - el.scrollTop - el.clientHeight < 60;
              stick.current = near;
              setShowJump(!near && streaming);
            }}
            className="d-scroll h-full overflow-y-auto px-5 py-4"
            aria-live="polite"
          >
            <div className="mx-auto max-w-[680px] space-y-5">
              {msgs.map((m, i) =>
                m.role === "user" ? (
                  <motion.div key={m.id} initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.16, ease: [0.23, 1, 0.32, 1] }} className="flex justify-end">
                    <p className="max-w-[80%] rounded-[18px] bg-d-fill px-3.5 py-2 text-[14px] leading-[22px] text-d-ink">{m.text}</p>
                  </motion.div>
                ) : (
                  <div key={m.id}>
                    <div className="flex items-start gap-2.5">
                      <Mark />
                      <div className="min-w-0 flex-1">
                        {m.phase === "thinking" && (
                          <p className="flex items-center gap-2 text-[13px] text-d-ink-3">
                            <span className="flex gap-1" aria-hidden>
                              {[0, 1, 2].map((k) => (
                                <motion.span
                                  key={k}
                                  className="size-1.5 rounded-full bg-d-ink-3"
                                  animate={{ opacity: [0.3, 1, 0.3] }}
                                  transition={{ repeat: Infinity, duration: 1, delay: k * 0.16 }}
                                />
                              ))}
                            </span>
                            {s("ai.thinking")}
                            {elapsed >= 1 && <span className="num text-[11.5px] text-d-ink-3">{s("ai.elapsed").replace("{n}", String(elapsed))}</span>}
                          </p>
                        )}
                        {toolsRow(m)}
                        {m.chunks && m.chunks.length > 0 && (
                          <p className="mt-2 text-[14px] leading-[23px] text-d-ink">
                            {m.chunks.map((piece, k) => (
                              <motion.span key={k} initial={{ opacity: 0 }} animate={{ opacity: 1 }} transition={{ duration: 0.12 }}>
                                {piece}
                              </motion.span>
                            ))}
                            {(m.phase === "streaming" || m.phase === "card") && <span className="d-cursor" aria-hidden />}
                          </p>
                        )}
                        {m.phase === "stopped" && <p className="mt-1 text-[11.5px] text-d-ink-3">{s("ai.stopped")}</p>}
                        {m.phase === "error" && (
                          <p className="mt-1 flex items-center gap-2 text-[12px] text-d-negative">
                            {s("ai.failed")}
                            <button type="button" onClick={() => lastUser && void runTurn(lastUser.text ?? "")} className="d-press text-[12px] font-medium text-d-accent underline underline-offset-2">
                              {s("ai.retry")}
                            </button>
                          </p>
                        )}
                        <AnimatePresence initial={false}>
                          {(m.phase === "card" || m.phase === "done") && m.card && (
                            <motion.div initial={{ height: 0, opacity: 0 }} animate={{ height: "auto", opacity: 1 }} transition={{ duration: 0.22, ease: [0.23, 1, 0.32, 1] }} className="overflow-hidden">
                              {cardNode(m)}
                            </motion.div>
                          )}
                        </AnimatePresence>
                        {actionsRow(m, i === msgs.length - 1)}
                      </div>
                    </div>
                  </div>
                ),
              )}
            </div>
          </div>

          {showJump && (
            <button
              type="button"
              onClick={jumpToBottom}
              className="d-press absolute bottom-3 right-4 flex h-7 items-center gap-1 rounded-full bg-d-ink px-3 text-[12px] font-medium text-d-canvas shadow-[0_6px_18px_-6px_oklch(0_0_0/40%)]"
            >
              ↓ {s("ai.newMessages")}
            </button>
          )}
        </div>

        <div className="shrink-0 border-t border-d-hairline p-3">
          <form
            onSubmit={(e) => {
              e.preventDefault();
              void runTurn(input);
            }}
            className="flex items-end gap-2 rounded-xl bg-d-fill p-1.5"
          >
            <button type="button" onClick={() => shell.openEntry()} aria-label={s("cm.record")} className="d-press grid size-8 shrink-0 place-items-center rounded-lg text-d-ink-2 hover:text-d-ink">
              <Plus className="size-4" strokeWidth={1.9} aria-hidden />
            </button>
            <textarea
              ref={inputRef}
              value={input}
              onChange={(e) => setInput(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === "Enter" && !e.shiftKey) {
                  e.preventDefault();
                  void runTurn(input);
                }
              }}
              rows={1}
              placeholder={s("ai.placeholder")}
              aria-label={s("ai.placeholder")}
              className="max-h-32 min-h-8 min-w-0 flex-1 resize-none bg-transparent py-1.5 text-[14px] leading-[20px] text-d-ink outline-none placeholder:text-d-ink-3"
            />
            {busy ? (
              <button type="button" onClick={stop} aria-label={s("ai.stop")} className="d-press grid size-8 shrink-0 place-items-center rounded-lg bg-d-ink text-d-canvas">
                <Square className="size-3 fill-current" strokeWidth={0} aria-hidden />
              </button>
            ) : (
              <button
                type="submit"
                disabled={!input.trim()}
                aria-label={s("ai.send")}
                className="d-press grid size-8 shrink-0 place-items-center rounded-lg bg-d-ink text-d-canvas disabled:bg-transparent disabled:text-d-ink-3"
              >
                <ArrowUp className="size-4" strokeWidth={2.2} aria-hidden />
              </button>
            )}
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
            <EmptyState icon={Sparkles} title={s("ai.refsEmptyTitle")} hint={s("ai.refsEmpty")} className="px-2 py-10" />
          ) : (
            lastAi.refs.map((ref) => refContent(ref, lastAi.phase === "streaming" || lastAi.phase === "card"))
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
