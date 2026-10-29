import { ArrowUp, Check, ChevronDown, Copy, Layers, Plus, RotateCcw, Sparkles, Square, ThumbsDown, ThumbsUp, Trash2, Upload } from "lucide-react";
import { AnimatePresence, motion } from "framer-motion";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { toast } from "sonner";
import { templateAssistant } from "@/ai/assistant";
import { CATEGORIES, type Category, type Txn, startOfDay } from "@/domain/types";
import { categoryLabel } from "../labels";
import { useLocale } from "@/lib/strings";
import { buildWeeklyReport } from "@/logic/weeklyReport";
import { decodeBillBytes, parseBillCsv } from "@/logic/csv";
import { cycleLabel, detectSubscriptions } from "@/logic/subscriptions";
import { simulateRegret } from "@/logic/regret";
import { addDays, weekStartOf } from "@/logic/time";
import { useLedger } from "@/services/LedgerProvider";
import { cn } from "@/lib/utils";
import { ReceiptPaper } from "@/features/receipt/ReceiptPaper";
import { type DraftTxn } from "@/logic/quickAdd";
import { MiniBars, type Point } from "../charts";
import { money } from "../format";
import { useDayDoc } from "../receiptDoc";
import { seriesOf } from "../statsModel";
import { Button, Card, Kbd, Skeleton } from "../ui";
import { s } from "../strings";
import { useShell } from "../Shell";
import { DraftsCard } from "./aiDrafts";
import { ImportPreview, type ImportPreviewState } from "./aiImport";
import { s as ai } from "./copy/ai";

type Card =
  | { kind: "drafts"; drafts: DraftTxn[]; saved: boolean }
  | { kind: "receipt" }
  | { kind: "weekly" }
  | { kind: "subs" }
  | { kind: "month"; rows: { category: Category; cents: number }[] }
  | { kind: "regret"; targetCategory: Category | null; pct: number; months: 1 | 3 | 6 }
  | { kind: "import" };

type RefSpec = { kind: "chart7" } | { kind: "chartWeek" } | { kind: "chartMonth" } | { kind: "entries"; ids: string[] } | { kind: "receipt" } | { kind: "subs" } | { kind: "weekly" };

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
  /** the user sentence that produced this answer, so regenerate can re-run it in place */
  prompt?: string;
}

type Vote = "up" | "down";

/** Chat survives page switches (module scope), same rule as the phone chat. */
const store: { msgs: Msg[]; votes: Record<string, Vote> } = { msgs: [], votes: {} };
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

/**
 * What the sources panel shows before the first answer: the same blocks a real answer cites
 * (a 7-day strip, today's total, the last three entries), so the column is never a blank wall.
 */
function LocalBook({ txns, today, onRecord }: { txns: Txn[]; today: number; onRecord: () => void }) {
  const points: Point[] = seriesOf("custom", { start: addDays(today, -6), end: addDays(today, 1) }, txns, null);
  const weekTotal = points.reduce((sum, p) => sum + p.value, 0);
  const todayTotal = txns.filter((t) => t.direction === "OUT" && t.occurredAt >= today).reduce((sum, t) => sum + t.amountCents, 0);
  const recent = [...txns].sort((a, b) => b.occurredAt - a.occurredAt).slice(0, 3);

  return (
    <div className="space-y-2">
      <p className="px-1 text-[11.5px] font-medium text-d-ink-2">{s("ai.localBook")}</p>

      <div className="rounded-lg bg-d-fill/50 p-2.5">
        <p className="mb-1.5 flex items-baseline justify-between text-[11.5px] text-d-ink-2">
          <span>{s("ai.localWeek")}</span>
          <span className="num text-[11px] text-d-ink-3">{money(weekTotal)}</span>
        </p>
        <MiniBars points={points} />
      </div>

      <dl className="grid grid-cols-2 gap-2">
        <div className="rounded-lg bg-d-fill/50 px-2.5 py-2">
          <dt className="text-[11px] text-d-ink-3">{s("ai.localToday")}</dt>
          <dd className="num mt-0.5 text-[15px] font-semibold text-d-ink">{money(todayTotal)}</dd>
        </div>
        <div className="rounded-lg bg-d-fill/50 px-2.5 py-2">
          <dt className="text-[11px] text-d-ink-3">{s("ci.weekCount")}</dt>
          <dd className="num mt-0.5 text-[15px] font-semibold text-d-ink">
            {points.reduce((n, p) => n + (p.value > 0 ? 1 : 0), 0)}
            <span className="ml-0.5 text-[11px] font-normal text-d-ink-3">{s("ov.dayUnit")}</span>
          </dd>
        </div>
      </dl>

      {recent.length > 0 && (
        <div className="rounded-lg bg-d-fill/50 p-2">
          <p className="px-1 pb-1 text-[11px] text-d-ink-3">{s("ai.localRecent")}</p>
          {recent.map((t) => (
            <div key={t.id} className="flex items-center gap-2 px-1 py-1 text-[12px]">
              <span aria-hidden>{CATEGORIES[t.category].emoji}</span>
              <span className="min-w-0 flex-1 truncate text-d-ink">{t.merchant}</span>
              <span className="num shrink-0 text-d-ink-2">{money(t.amountCents)}</span>
            </div>
          ))}
        </div>
      )}

      <p className="px-1 pt-1 text-[11px] leading-[16px] text-d-ink-3">{s("ai.localBookHint")}</p>

      <Button size="sm" variant="ghost" icon={Plus} onClick={onRecord}>
        {s("cm.record")}
      </Button>
    </div>
  );
}

const fill = (template: string, ...vals: (string | number)[]) =>
  vals.reduce<string>((acc, v) => acc.replace("{}", String(v)), template);

export function AiPage() {
  const locale = useLocale();
  const { txns, now, add, isLoading, isError, refetch } = useLedger();
  const shell = useShell();
  const today = startOfDay(now);
  const monthStart = useMemo(() => {
    const d = new Date(today);
    return new Date(d.getFullYear(), d.getMonth(), 1).getTime();
  }, [today]);
  const todayDoc = useDayDoc(today);
  const fileRef = useRef<HTMLInputElement>(null);
  const inputRef = useRef<HTMLTextAreaElement>(null);
  const scrollRef = useRef<HTMLDivElement>(null);
  const contentRef = useRef<HTMLDivElement>(null);
  const stick = useRef(true);

  const [msgs, setMsgs] = useState<Msg[]>(store.msgs);
  const [input, setInput] = useState("");
  const [busy, setBusy] = useState(false);
  const [showJump, setShowJump] = useState(false);
  const [expandedTools, setExpandedTools] = useState<Set<string>>(new Set());
  const [votes, setVotes] = useState<Record<string, Vote>>(store.votes);
  const [copied, setCopied] = useState<string | null>(null);
  const [elapsed, setElapsed] = useState(0);
  const [activeAnswerId, setActiveAnswerId] = useState<string | null>(null);
  const [importPreview, setImportPreview] = useState<ImportPreviewState | null>(null);
  const turn = useRef<{ cancelled: boolean; msgId: string } | null>(null);
  /** the greeting alone may be rewritten in place when the language changes (it is also the only
   * message a restored chat can hold) */
  const openingId = useRef<string | null>(store.msgs.length === 1 && store.msgs[0].role === "ai" ? store.msgs[0].id : null);

  useEffect(() => {
    store.msgs = msgs;
  }, [msgs]);

  useEffect(() => {
    store.votes = votes;
  }, [votes]);

  // The assistant speaks first, once the ledger is readable, and re-speaks in the new language if
  // the reader switches language before saying anything.
  useEffect(() => {
    if (isLoading || isError) return;
    const text = locale === "en" ? s("ai.opening") : templateAssistant.opening(txns, now).text;
    if (msgs.length === 0) {
      openingId.current = nid();
      setMsgs([{ id: openingId.current, role: "ai", phase: "done", chunks: [text], text }]);
    } else if (msgs.length === 1 && msgs[0].id === openingId.current && msgs[0].text !== text) {
      setMsgs([{ ...msgs[0], chunks: [text], text }]);
    }
  }, [msgs, txns, now, isLoading, isError, locale]);

  const lastText = msgs[msgs.length - 1]?.chunks?.join("") ?? "";

  // Keep the newest content in view, unless the reader scrolled up.
  useEffect(() => {
    const el = scrollRef.current;
    if (!el || !stick.current) return;
    el.scrollTop = el.scrollHeight;
  }, [msgs.length, lastText]);

  // Cards and charts grow after they mount; watch the content box, not just the token count.
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

  /**
   * One turn: thinking → tool capsules → streamed tokens → card → done.
   * `replaceId` re-runs an existing answer in place (regenerate) instead of appending a new turn.
   */
  const runTurn = useCallback(
    async (raw: string, replaceId?: string) => {
      const text = raw.trim();
      if (!text || busy) return;
      if (!replaceId) setInput("");
      stick.current = true;
      setShowJump(false);
      inputRef.current?.focus();

      const intent = templateAssistant.understand(text, now);
      const answerId = replaceId ?? nid();
      if (replaceId) {
        patch(replaceId, (m) => ({ ...m, phase: "thinking", chunks: [], tools: [], card: undefined, refs: undefined, prompt: text }));
      } else {
        const userMsg: Msg = { id: nid(), role: "user", text };
        const aiMsg: Msg = { id: answerId, role: "ai", phase: "thinking", chunks: [], tools: [], prompt: text };
        setMsgs((list) => [...list, userMsg, aiMsg]);
      }
      // The panel follows whichever answer is being written.
      setActiveAnswerId(answerId);
      setBusy(true);
      const token = { cancelled: false, msgId: answerId };
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

        if (isLoading) {
          // Never answer "nothing spent" off a ledger that has not arrived yet.
          answer = ai("answer.loading");
        } else if (isError) {
          answer = ai("answer.offline");
        } else {
          switch (intent.kind) {
            case "ADD": {
              tools.push({ id: "draft", label: s("tool.draft"), done: false });
              summaries = [fill(s("ai.answer.count"), intent.drafts.length)];
              answer = fill(s("ai.answer.drafts"), intent.drafts.length);
              card = { kind: "drafts", drafts: intent.drafts, saved: false };
              break;
            }
            case "TODAY_SPEND": {
              tools.push({ id: "today", label: s("tool.today"), done: false });
              summaries = [fill(s("ai.answer.count"), todaysOut.length)];
              answer = todaysOut.length ? fill(s("ai.answer.today"), money(todayTotal), todaysOut.length) : s("ai.answer.todayNone");
              refs = [{ kind: "chart7" }, { kind: "entries", ids: todaysOut.slice(-5).map((t) => t.id) }];
              break;
            }
            case "MONTH_SPEND": {
              tools.push({ id: "month", label: s("tool.month"), done: false });
              const inMonth = txns.filter((t) => t.direction === "OUT" && t.occurredAt >= monthStart && t.occurredAt < addDays(today, 1));
              const byCategory = new Map<Category, number>();
              for (const t of inMonth) byCategory.set(t.category, (byCategory.get(t.category) ?? 0) + t.amountCents);
              const rows = [...byCategory.entries()].map(([category, cents]) => ({ category, cents })).sort((a, b) => b.cents - a.cents);
              const total = inMonth.reduce((sum, t) => sum + t.amountCents, 0);
              summaries = [fill(s("ai.answer.count"), inMonth.length)];
              if (rows.length === 0) {
                answer = ai("answer.monthNone");
              } else {
                answer = fill(ai("answer.month"), money(total), inMonth.length, categoryLabel(rows[0].category), money(rows[0].cents));
                card = { kind: "month", rows };
              }
              refs = [{ kind: "chartMonth" }];
              break;
            }
            case "RECEIPT": {
              tools.push({ id: "receipt", label: s("tool.receipt"), done: false });
              summaries = [s("ai.answer.receiptSummary")];
              answer = s("ai.answer.receipt");
              card = { kind: "receipt" };
              refs = [{ kind: "receipt" }];
              break;
            }
            case "WEEKLY": {
              tools.push({ id: "week", label: s("tool.week"), done: false }, { id: "peak", label: s("tool.peak"), done: false });
              summaries = [fill(s("ai.answer.count"), week.txnCount), week.topCategories[0] ? categoryLabel(week.topCategories[0].category) : "—"];
              answer = fill(s("ai.answer.week"), money(week.totalCents), week.txnCount);
              card = { kind: "weekly" };
              refs = [{ kind: "weekly" }, { kind: "chartWeek" }];
              break;
            }
            case "SUBSCRIPTIONS": {
              tools.push({ id: "subs", label: s("tool.subs"), done: false });
              summaries = [fill(s("ai.answer.subsCount"), subList.length)];
              answer = subList.length ? s("ai.answer.subs") : s("ai.answer.subsNone");
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
              summaries = [fill(s("ai.monthExpected"), money(result.monthlyCents))];
              answer = fill(s("ai.answer.regret"), intent.months, intent.reductionPct);
              card = { kind: "regret", targetCategory: target, pct: intent.reductionPct, months: intent.months };
              refs = [{ kind: "chart7" }];
              break;
            }
            case "IMPORT": {
              answer = s("ai.answer.import");
              card = { kind: "import" };
              break;
            }
            default: {
              answer = s("ai.answer.fallback");
            }
          }
        }

        // tool capsules: start, then finish with a one-line result
        for (let i = 0; i < tools.length; i++) {
          if (token.cancelled) return;
          patch(answerId, (m) => ({ ...m, tools: [...(m.tools ?? []), tools[i]] }));
          await sleep(160 + Math.random() * 220);
          if (token.cancelled) return;
          patch(answerId, (m) => ({ ...m, tools: (m.tools ?? []).map((t) => (t.id === tools[i].id ? { ...t, done: true, summary: summaries[i] } : t)) }));
        }

        // tokens: 20–40ms each, with a little jitter
        patch(answerId, (m) => ({ ...m, phase: "streaming" }));
        for (const piece of tokenize(answer)) {
          if (token.cancelled) return;
          patch(answerId, (m) => ({ ...m, chunks: [...(m.chunks ?? []), piece] }));
          await sleep(20 + Math.random() * 20);
        }
        if (token.cancelled) return;

        await sleep(140);
        if (token.cancelled) return;
        patch(answerId, (m) => ({ ...m, phase: "card", card, refs }));
        await sleep(320);
        if (token.cancelled) return;
        patch(answerId, (m) => ({ ...m, phase: "done" }));
      } catch {
        // Cloud failures fall back to the local engine; if even that throws, offer a retry line.
        if (!token.cancelled) patch(answerId, (m) => ({ ...m, phase: "error" }));
      } finally {
        // A stale turn must not unlock (or relock) the composer for the live one.
        if (turn.current === token) {
          turn.current = null;
          setBusy(false);
        }
        inputRef.current?.focus();
      }
    },
    [busy, isError, isLoading, monthStart, now, patch, today, txns],
  );

  const stop = () => {
    const token = turn.current;
    if (!token) return;
    token.cancelled = true;
    turn.current = null;
    setBusy(false);
    patch(token.msgId, (m) => (m.phase === "thinking" || m.phase === "streaming" || m.phase === "card" ? { ...m, phase: "stopped" } : m));
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

  const editDrafts = (m: Msg, next: DraftTxn[]) => {
    patch(m.id, (x) => (x.card?.kind === "drafts" ? { ...x, card: { ...x.card, drafts: next } } : x));
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

  /** Thumbs are a local tally the user can change; the badge stays on the answer that got it. */
  const vote = (m: Msg, v: Vote) => {
    const on = votes[m.id] !== v;
    setVotes((prev) => {
      const next = { ...prev };
      if (on) next[m.id] = v;
      else delete next[m.id];
      return next;
    });
    if (on) toast(ai("action.feedbackThanks"));
  };

  const weekly = useMemo(() => buildWeeklyReport(txns, weekStartOf(now)), [txns, now]);

  const readBill = useCallback(
    async (file: File) => {
      try {
        const parsed = parseBillCsv(decodeBillBytes(await file.arrayBuffer()));
        if (parsed.txns.length === 0) {
          toast(s("ledger.importEmpty"));
          return;
        }
        const known = new Set(txns.map((t) => t.dedupeKey ?? t.id));
        setImportPreview({
          rows: parsed.txns,
          failed: parsed.failed,
          duplicates: parsed.txns.filter((t) => known.has(t.dedupeKey ?? t.id)).length,
        });
      } catch {
        toast(s("ledger.importEmpty"));
      }
    },
    [txns],
  );

  const confirmImport = async () => {
    if (!importPreview) return;
    const rows = importPreview.rows;
    try {
      const result = await add(rows);
      setImportPreview(null);
      if (result.added === 0) toast(s("ledger.importDupAll"));
      else toast(`${s("ledger.importDone")} ${result.added}`);
    } catch {
      toast(s("common.error"));
    }
  };

  // The panel follows the answer the reader last picked; otherwise the newest one.
  const activeAnswer = useMemo(() => {
    if (activeAnswerId) {
      const picked = msgs.find((m) => m.id === activeAnswerId && m.role === "ai");
      if (picked) return picked;
    }
    return [...msgs].reverse().find((m) => m.role === "ai") ?? null;
  }, [msgs, activeAnswerId]);

  const cardNode = (m: Msg) => {
    const c = m.card;
    if (!c) return null;
    if (c.kind === "drafts") {
      return (
        <DraftsCard
          drafts={c.drafts}
          now={now}
          saved={c.saved}
          onChange={(d) => editDrafts(m, c.drafts.map((o) => (o.id === d.id ? d : o)))}
          onRemove={(id) => editDrafts(m, c.drafts.filter((d) => d.id !== id))}
          onConfirm={() => void confirmDrafts(m)}
        />
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
            ...weekly.topCategories.map((row) => ({ label: categoryLabel(row.category), value: money(row.cents) })),
          ]}
        />
      );
    }
    if (c.kind === "month") {
      return (
        <Facts
          rows={[
            { label: s("stats.total"), value: money(c.rows.reduce((sum, row) => sum + row.cents, 0)) },
            ...c.rows.slice(0, 5).map((row) => ({ label: categoryLabel(row.category), value: money(row.cents) })),
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
            { label: s("ai.monthExpected"), value: money(result.monthlyCents) },
            { label: fill(s("ai.savePct"), c.pct), value: money(result.savedCents) },
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

  /** Every ref of the active answer keeps its ring, streaming or not: it is what the panel is following. */
  const refContent = (ref: RefSpec) => {
    if (ref.kind === "chart7" || ref.kind === "chartWeek" || ref.kind === "chartMonth") {
      const [start, end, label] =
        ref.kind === "chartWeek"
          ? [weekStartOf(now), addDays(weekStartOf(now), 7), s("ci.weekly")]
          : ref.kind === "chartMonth"
            ? [monthStart, addDays(today, 1), ai("chart.month")]
            : [addDays(today, -6), addDays(today, 1), s("ov.last7")];
      const points: Point[] = seriesOf("custom", { start, end }, txns, null);
      const total = points.reduce((sum, p) => sum + p.value, 0);
      return (
        <div key={ref.kind} className="rounded-lg bg-d-fill/50 p-2.5 ring-1 ring-d-accent/25">
          <p className="mb-1.5 flex items-baseline justify-between text-[11.5px] text-d-ink-2">
            <span>{label}</span>
            <span className="num text-[11px] text-d-ink-3">{money(total)}</span>
          </p>
          {total === 0 ? (
            <p className="py-3 text-center text-[11.5px] text-d-ink-3">{s("ai.refsNoData")}</p>
          ) : (
            <div role="img" aria-label={s("ai.tabChart")}>
              <MiniBars points={points} />
            </div>
          )}
        </div>
      );
    }
    if (ref.kind === "receipt") {
      return (
        <div key="receipt" className="flex justify-center rounded-lg bg-d-fill/50 p-2 ring-1 ring-d-accent/25">
          <ReceiptPaper doc={todayDoc} stamp="shown" />
        </div>
      );
    }
    if (ref.kind === "entries") {
      const rows = ref.ids.map((id) => txns.find((t) => t.id === id)).filter((t): t is Txn => Boolean(t));
      if (rows.length === 0) return null;
      return (
        <div key="entries" className="rounded-lg bg-d-fill/50 p-2 ring-1 ring-d-accent/25">
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
        <div key="weekly" className="rounded-lg bg-d-fill/50 p-2.5 ring-1 ring-d-accent/25">
          <Facts
            rows={[
              { label: s("stats.total"), value: money(weekly.totalCents) },
              { label: s("stats.count"), value: `${weekly.txnCount} ${s("ledger.itemUnit")}` },
              ...weekly.topCategories.map((c) => ({ label: categoryLabel(c.category), value: money(c.cents) })),
            ]}
          />
        </div>
      );
    }
    const subs = detectSubscriptions(txns, now);
    return (
      <div key="subs" className="rounded-lg bg-d-fill/50 p-2 ring-1 ring-d-accent/25">
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

  const refsPanel = () => {
    const refs = activeAnswer?.refs ?? [];
    if (refs.length > 0) return refs.map((ref) => refContent(ref));
    if (activeAnswer && (activeAnswer.phase === "done" || activeAnswer.phase === "stopped" || activeAnswer.phase === "error")) {
      return (
        <div className="px-1 pt-1">
          <p className="text-[12px] font-medium text-d-ink-2">{s("ai.refsEmptyTitle")}</p>
          <p className="mt-1 text-[11.5px] leading-[16px] text-d-ink-3">{s("ai.refsEmpty")}</p>
          <p className="mt-3 text-[11.5px] text-d-ink-3">{ai("refs.tryAsking")}</p>
          <div className="mt-1.5 flex flex-wrap gap-1.5">
            {[s("ai.suggestion1"), s("ai.suggestion2"), s("ai.suggestion3")].map((q) => (
              <button
                key={q}
                type="button"
                onClick={() => void runTurn(q)}
                className="d-press rounded-full bg-d-fill px-2.5 py-1 text-[11.5px] text-d-ink-2 transition-colors hover:bg-d-hairline hover:text-d-ink focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-d-accent-ink"
              >
                {q}
              </button>
            ))}
          </div>
        </div>
      );
    }
    return <LocalBook txns={txns} today={today} onRecord={() => shell.openEntry()} />;
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

  const actionButton = "d-press flex h-6 items-center gap-1 rounded-md px-1.5 hover:bg-d-fill hover:text-d-ink";

  const actionsRow = (m: Msg) => {
    if (m.phase !== "done") return null;
    const fb = votes[m.id];
    const picked = activeAnswer?.id === m.id;
    return (
      <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} transition={{ duration: 0.16 }} className="mt-2 flex items-center gap-1 text-[12px] text-d-ink-3">
        <button type="button" aria-label={s("ai.copy")} onClick={() => void copyAnswer(m)} className={actionButton}>
          {copied === m.id ? <Check className="size-3" strokeWidth={2.2} aria-hidden /> : <Copy className="size-3" strokeWidth={1.9} aria-hidden />}
          {copied === m.id ? s("ai.copied") : s("ai.copy")}
        </button>
        {m.prompt && (
          <button type="button" aria-label={s("ai.regenerate")} onClick={() => void runTurn(m.prompt ?? "", m.id)} className={actionButton}>
            <RotateCcw className="size-3" strokeWidth={1.9} aria-hidden />
            {s("ai.regenerate")}
          </button>
        )}
        <button type="button" aria-label={ai("action.sources")} aria-pressed={picked} onClick={() => setActiveAnswerId(m.id)} className={cn(actionButton, picked && "text-d-accent-ink")}>
          <Layers className="size-3" strokeWidth={1.9} aria-hidden />
        </button>
        <button type="button" aria-label={s("ai.useful")} aria-pressed={fb === "up"} onClick={() => vote(m, "up")} className={cn("d-press grid size-6 place-items-center rounded-md hover:bg-d-fill hover:text-d-ink", fb === "up" && "text-d-positive")}>
          <ThumbsUp className="size-3" strokeWidth={1.9} aria-hidden />
        </button>
        <button type="button" aria-label={s("ai.useless")} aria-pressed={fb === "down"} onClick={() => vote(m, "down")} className={cn("d-press grid size-6 place-items-center rounded-md hover:bg-d-fill hover:text-d-ink", fb === "down" && "text-d-negative")}>
          <ThumbsDown className="size-3" strokeWidth={1.9} aria-hidden />
        </button>
      </motion.div>
    );
  };

  const streaming = msgs.some((m) => m.role === "ai" && (m.phase === "thinking" || m.phase === "streaming" || m.phase === "card"));
  const started = msgs.some((m) => m.role === "user");

  return (
    <div className="grid grid-cols-12 gap-5 xl:h-[calc(100dvh-var(--d-chrome,88px)-44px)] xl:min-h-[520px]">
      <Card className="col-span-12 flex h-[520px] min-h-0 flex-col xl:col-span-8 xl:h-auto" padded={false} fill>
        <header className="flex h-11 shrink-0 items-center justify-between border-b border-d-hairline px-4">
          <h2 className="flex items-center gap-1.5 text-[13px] font-semibold text-d-ink-2">
            <Sparkles className="size-3.5" strokeWidth={1.9} aria-hidden />
            {s("ai.title")}
          </h2>
          <Button
            size="sm"
            variant="ghost"
            icon={Trash2}
            onClick={() => {
              setMsgs([]);
              setVotes({});
              setActiveAnswerId(null);
            }}
          >
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
            <div ref={contentRef} className="mx-auto max-w-[680px] space-y-5">
              {isError && msgs.length > 0 && (
                <div className="flex items-center justify-between gap-3 rounded-lg bg-d-fill px-3 py-2 text-[12px] text-d-ink-2">
                  <span>{ai("state.error")}</span>
                  <button type="button" onClick={refetch} className="d-press shrink-0 font-medium text-d-accent-ink underline underline-offset-2">
                    {s("common.retry")}
                  </button>
                </div>
              )}

              {msgs.length === 0 && isLoading && <Skeleton rows={3} className="pl-[34px]" />}

              {msgs.length === 0 && isError && (
                <div className="flex items-start gap-2.5">
                  <Mark />
                  <div>
                    <p className="text-[13px] leading-[20px] text-d-ink-2">{ai("state.error")}</p>
                    <Button size="sm" className="mt-2" onClick={refetch}>
                      {s("common.retry")}
                    </Button>
                  </div>
                </div>
              )}

              {msgs.map((m) =>
                m.role === "user" ? (
                  <motion.div key={m.id} initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.16, ease: [0.23, 1, 0.32, 1] }} className="flex justify-end">
                    <p className="max-w-[80%] rounded-[18px] bg-d-fill px-3.5 py-2 text-[14px] leading-[22px] text-d-ink">{m.text}</p>
                  </motion.div>
                ) : (
                  // Clicking an answer points the sources column at it; the action row button does the same for keyboards.
                  <div key={m.id} onClick={() => setActiveAnswerId(m.id)}>
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
                            {m.prompt && (
                              <button type="button" onClick={() => void runTurn(m.prompt ?? "", m.id)} className="d-press text-[12px] font-medium text-d-accent-ink underline underline-offset-2">
                                {s("ai.retry")}
                              </button>
                            )}
                          </p>
                        )}
                        <AnimatePresence initial={false}>
                          {(m.phase === "card" || m.phase === "done") && m.card && (
                            <motion.div initial={{ height: 0, opacity: 0 }} animate={{ height: "auto", opacity: 1 }} transition={{ duration: 0.22, ease: [0.23, 1, 0.32, 1] }} className="overflow-hidden">
                              {cardNode(m)}
                            </motion.div>
                          )}
                        </AnimatePresence>
                        {actionsRow(m)}
                      </div>
                    </div>
                  </div>
                ),
              )}

              {!started && !isLoading && !isError && (
                <div className="flex flex-wrap gap-2 pl-[34px]">
                  {[s("ai.starter1"), s("ai.starter2"), s("ai.starter3")].map((q) => (
                    <button
                      key={q}
                      type="button"
                      onClick={() => void runTurn(q)}
                      className="d-press rounded-full bg-d-fill px-3 py-1.5 text-[12.5px] text-d-ink-2 transition-colors hover:bg-d-hairline hover:text-d-ink focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-d-accent-ink"
                    >
                      {q}
                    </button>
                  ))}
                </div>
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
            <span aria-hidden>·</span>
            <Kbd>Shift</Kbd>
            <span aria-hidden>+</span>
            <Kbd>Enter</Kbd> {ai("hint.newline")}
          </p>
        </div>
      </Card>

      <Card className="col-span-12 flex h-[340px] min-h-0 flex-col xl:col-span-4 xl:h-auto" padded={false} fill title={s("ai.refs")}>
        <div className="d-scroll min-h-0 flex-1 space-y-2 overflow-y-auto px-4 pb-4">{refsPanel()}</div>
      </Card>

      <input
        ref={fileRef}
        type="file"
        accept=".csv,text/csv"
        className="hidden"
        onChange={(e) => {
          const file = e.target.files?.[0];
          e.target.value = "";
          if (file) void readBill(file);
        }}
      />

      <ImportPreview preview={importPreview} onClose={() => setImportPreview(null)} onConfirm={() => void confirmImport()} />
    </div>
  );
}
