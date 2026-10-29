import { ArrowUp, Share2, Users } from "lucide-react";
import { useEffect, useMemo, useRef, useState } from "react";
import { addDays, hhmm, weekStartOf } from "@/logic/time";
import { useLedger } from "@/services/LedgerProvider";
import { cn } from "@/lib/utils";
import { useLocale } from "@/lib/strings";
import {
  ME,
  SHARE_DEFAULTS,
  SHARE_ORDER,
  circles,
  feedRows,
  memberOf,
  receiptView,
  seedMessages,
  weeklyFacts,
  type CirclePost,
  type ReceiptPost,
  type Shares,
  type WeeklyFacts,
} from "../circleData";
import { categoryLabel } from "../labels";
import { money, relativeDay } from "../format";
import { Button, Card, Chip, EmptyState, Hint, Toggle } from "../ui";
import { s } from "../strings";
import { SHARE_HINT, SHARE_LABEL, c } from "./copy/circle";

function Avatar({ emoji, self }: { emoji: string; self?: boolean }) {
  return <span className={cn("grid size-7 shrink-0 place-items-center rounded-full text-[13px]", self ? "bg-d-ink text-d-canvas" : "bg-d-fill text-d-ink")} aria-hidden>{emoji}</span>;
}

/** One shared day of spending, rendered through its author's sharing profile. */
function ReceiptBody({ post, shares, now }: { post: ReceiptPost; shares: Shares; now: number }) {
  const view = receiptView(post.items, shares);
  const total = post.items.reduce((sum, item) => sum + item.cents, 0);
  return (
    <div className="w-[272px] max-w-full rounded-[14px] border border-d-hairline bg-d-surface p-3 text-left">
      <div className="flex items-center justify-between gap-2">
        <p className="text-[11.5px] text-d-ink-2">
          {relativeDay(post.dayStart, now)} · {c("circle.receipt")}
        </p>
        {view.totalCents !== null ? (
          <p className="num text-[15px] font-semibold leading-5 text-d-ink">{money(view.totalCents)}</p>
        ) : (
          <Chip>{c("circle.unshared")}</Chip>
        )}
      </div>

      {view.slices && view.slices.length > 0 && (
        <div className="mt-2 flex flex-wrap gap-1">
          {view.slices.map((slice) => (
            <Chip key={slice.category}>
              {categoryLabel(slice.category)} · {slice.percent}%
            </Chip>
          ))}
        </div>
      )}

      {view.full ? (
        <div className="mt-2 border-t border-dashed border-d-hairline pt-2">
          <p className="mb-1 text-[10.5px] tracking-[0.08em] text-d-ink-3">{c("circle.fullReceipt")}</p>
          <ul className="space-y-1 font-mono text-[11.5px] text-d-ink-2">
            {view.full.map((item, index) => (
              <li key={`${item.merchant}-${index}`} className="flex items-baseline justify-between gap-3">
                <span className="truncate">{item.merchant}</span>
                <span className="shrink-0 tabular-nums">{money(item.cents)}</span>
              </li>
            ))}
          </ul>
          <p className="mt-1.5 flex items-baseline justify-between border-t border-d-hairline pt-1.5 text-[12px] font-semibold text-d-ink">
            <span>{c("circle.total")}</span>
            <span className="num">{money(total)}</span>
          </p>
        </div>
      ) : view.detail ? (
        <ul className="mt-2 space-y-1 text-[11.5px] text-d-ink-2">
          {view.detail.map((item, index) => (
            <li key={`${item.merchant}-${index}`} className="flex items-baseline justify-between gap-3">
              <span className="truncate">{item.merchant}</span>
              <span className="num shrink-0">{money(item.cents)}</span>
            </li>
          ))}
        </ul>
      ) : null}

      {post.items.length === 0 && <p className="mt-1.5 text-[11.5px] text-d-ink-3">{c("circle.noSpend")}</p>}
    </div>
  );
}

/** A weekly report the author posted; the same switches that hide their receipts also mask this. */
function WeeklyBody({ facts, shares }: { facts: WeeklyFacts; shares: Shares }) {
  return (
    <div className="text-left">
      <p className="mb-1 text-[12px]">{s("ci.weekly")}</p>
      {shares.todayTotal ? (
        <p className="num text-[15px] font-semibold">{money(facts.totalCents)}</p>
      ) : (
        <p className="text-[12px] opacity-70">{c("circle.amountHidden")}</p>
      )}
      <p className="mt-0.5 text-[11.5px] opacity-70">
        {facts.count} {s("ledger.itemUnit")}
        {facts.top && shares.categoryMix ? ` · ${categoryLabel(facts.top)}` : ""}
      </p>
    </div>
  );
}

export function CirclePage() {
  const locale = useLocale();
  const { txns, now } = useLedger();
  const allCircles = useMemo(() => circles(locale), [locale]);
  const [circleId, setCircleId] = useState(allCircles[0].id);
  const [sent, setSent] = useState<CirclePost[]>([]);
  /** One draft per circle: switching rooms parks the text instead of dropping it into the wrong one. */
  const [drafts, setDrafts] = useState<Record<string, string>>({});
  const [myShares, setMyShares] = useState<Shares>(SHARE_DEFAULTS);
  const [shareTargetId, setShareTargetId] = useState(ME);
  const feedRef = useRef<HTMLDivElement | null>(null);
  const localSeq = useRef(0);

  const circle = allCircles.find((entry) => entry.id === circleId) ?? allCircles[0];
  const seeded = useMemo(() => seedMessages(now, locale), [now, locale]);
  const posts = useMemo(() => [...seeded, ...sent].filter((post) => post.circleId === circle.id), [seeded, sent, circle.id]);
  const rows = useMemo(() => feedRows(posts), [posts]);
  const week = useMemo(() => {
    const start = weekStartOf(now);
    return weeklyFacts(txns, start, addDays(start, 7));
  }, [txns, now]);

  const draft = drafts[circle.id] ?? "";
  const canShare = week.count > 0 && myShares.todayTotal && myShares.weeklyTitles;
  const blocked =
    week.count === 0
      ? s("ci.weekEmpty")
      : !myShares.todayTotal
        ? c("circle.blockedTotal")
        : !myShares.weeklyTitles
          ? c("circle.blockedTitles")
          : null;

  const sharesFor = (memberId: string): Shares => (memberId === ME ? myShares : memberOf(circle, memberId)?.shares ?? SHARE_DEFAULTS);
  const target = memberOf(circle, shareTargetId) ?? circle.members[0];
  const targetShares = target.self ? myShares : target.shares;

  // A new room starts at the top of its own history.
  useEffect(() => {
    feedRef.current?.scrollTo({ top: 0 });
  }, [circle.id]);

  const scrollToLatest = () => {
    requestAnimationFrame(() => {
      const feed = feedRef.current;
      if (feed) feed.scrollTo({ top: feed.scrollHeight, behavior: "smooth" });
    });
  };

  const send = () => {
    const body = draft.trim();
    if (!body) return;
    localSeq.current += 1;
    const at = Date.now();
    setSent((list) => [...list, { kind: "text", id: `local-${localSeq.current}`, circleId: circle.id, memberId: ME, at, text: body }]);
    setDrafts((prev) => ({ ...prev, [circle.id]: "" }));
    scrollToLatest();
  };

  const shareWeek = () => {
    if (!canShare) return;
    localSeq.current += 1;
    const at = Date.now();
    setSent((list) => [...list, { kind: "weekly", id: `local-${localSeq.current}`, circleId: circle.id, memberId: ME, at, weekly: week }]);
    scrollToLatest();
  };

  return (
    <div className="grid grid-cols-12 gap-5 xl:h-[calc(100dvh-var(--d-chrome,88px)-44px)] xl:min-h-[520px]">
      <Card className="col-span-12 flex min-h-0 flex-col xl:col-span-3" padded={false} fill title={s("ci.groups")}>
        <nav className="min-h-0 flex-1 space-y-0.5 overflow-y-auto px-2 pb-3">
          {allCircles.map((entry) => {
            const on = entry.id === circle.id;
            return (
              <button
                key={entry.id}
                type="button"
                onClick={() => setCircleId(entry.id)}
                aria-current={on ? "true" : undefined}
                className={cn(
                  "d-press flex h-8 w-full items-center gap-2 rounded-lg px-2 text-[13px] transition-colors",
                  on ? "bg-d-fill text-d-ink" : "text-d-ink-2 hover:bg-d-fill/50 hover:text-d-ink",
                )}
              >
                <span aria-hidden className="text-[13px]">{entry.emoji}</span>
                <span className="min-w-0 flex-1 truncate text-left">{entry.name}</span>
                <span className="num text-[11px] text-d-ink-3">{entry.members.length}</span>
              </button>
            );
          })}
        </nav>
      </Card>

      <Card className="col-span-12 flex h-[520px] min-h-0 flex-col xl:col-span-6 xl:h-auto" padded={false} fill>
        <header className="flex h-11 shrink-0 items-center justify-between border-b border-d-hairline px-4">
          <div className="min-w-0">
            <h2 className="flex items-center gap-1.5 truncate text-[13px] font-semibold text-d-ink-2">
              <Users className="size-3.5 shrink-0" strokeWidth={1.9} aria-hidden />
              {circle.emoji} {circle.name}
            </h2>
            <p className="mt-0.5 truncate pl-[22px] text-[11px] text-d-ink-3">
              {c("circle.roomMeta").replace("{count}", String(circle.members.length))}
            </p>
          </div>
          <button
            type="button"
            onClick={shareWeek}
            disabled={!canShare}
            title={blocked ?? undefined}
            className="d-press flex h-7 items-center gap-1 rounded-md bg-d-fill px-2 text-[12px] text-d-ink-2 hover:text-d-ink disabled:opacity-40 disabled:hover:text-d-ink-2"
          >
            <Share2 className="size-3.5" strokeWidth={1.8} aria-hidden />
            {s("ci.shareWeek")}
          </button>
        </header>

        <div ref={feedRef} className="d-scroll min-h-0 flex-1 space-y-3 overflow-y-auto px-4 py-3">
          {rows.length === 0 ? (
            <EmptyState art="/img/app-empty-circle.jpg" title={s("ci.empty")} className="py-8" />
          ) : (
            rows.map((row) => {
              if (row.kind === "day") {
                return (
                  <div key={row.key} className="flex items-center gap-2 py-0.5" role="separator">
                    <span className="h-px flex-1 bg-d-hairline" />
                    <span className="text-[11px] text-d-ink-3">{relativeDay(row.dayStart, now)}</span>
                    <span className="h-px flex-1 bg-d-hairline" />
                  </div>
                );
              }
              const post = row.post;
              const author = memberOf(circle, post.memberId);
              const mine = post.memberId === ME;
              return (
                <div key={row.key} className={cn("flex items-start gap-2", mine && "flex-row-reverse")}>
                  <Avatar emoji={author?.emoji ?? "🙂"} self={mine} />
                  <div className={cn("min-w-0 max-w-[84%]", mine && "flex flex-col items-end")}>
                    <p className={cn("mb-0.5 text-[11px] text-d-ink-3", mine && "text-right")}>
                      {author?.name ?? "—"} · {hhmm(post.at)}
                    </p>
                    {post.kind === "receipt" ? (
                      <ReceiptBody post={post} shares={sharesFor(post.memberId)} now={now} />
                    ) : (
                      <div className={cn("rounded-[14px] px-3 py-2 text-[13px] leading-[20px]", mine ? "bg-d-ink text-d-canvas" : "bg-d-fill text-d-ink")}>
                        {post.kind === "weekly" ? <WeeklyBody facts={post.weekly} shares={sharesFor(post.memberId)} /> : post.text}
                      </div>
                    )}
                  </div>
                </div>
              );
            })
          )}
        </div>

        <form
          className="flex shrink-0 items-center gap-2 border-t border-d-hairline p-3"
          onSubmit={(e) => {
            e.preventDefault();
            send();
          }}
        >
          <input
            value={draft}
            onChange={(e) => setDrafts((prev) => ({ ...prev, [circle.id]: e.target.value }))}
            placeholder={s("ci.input")}
            aria-label={s("ci.input")}
            className="h-9 min-w-0 flex-1 rounded-lg bg-d-fill px-3 text-[13px] text-d-ink outline-none placeholder:text-d-ink-3"
          />
          <button
            type="submit"
            disabled={!draft.trim()}
            aria-label={s("ci.send")}
            className="d-press grid size-9 shrink-0 place-items-center rounded-lg bg-d-ink text-d-canvas disabled:bg-d-fill disabled:text-d-ink-3"
          >
            <ArrowUp className="size-4" strokeWidth={2.2} aria-hidden />
          </button>
        </form>
      </Card>

      <div className="d-scroll col-span-12 flex min-h-0 flex-col gap-5 overflow-y-auto xl:col-span-3">
        <Card className="shrink-0" title={s("ci.weekCard")}>
          {week.count === 0 ? (
            <Hint>{s("ci.weekEmpty")}</Hint>
          ) : (
            <>
              <p className="text-[11.5px] text-d-ink-3">{s("ci.weekTotal")}</p>
              {myShares.todayTotal ? (
                <p className="num mt-0.5 text-[22px] font-semibold leading-7 text-d-ink">{money(week.totalCents)}</p>
              ) : (
                <p className="mt-0.5 text-[14px] font-medium text-d-ink-3">{c("circle.amountHidden")}</p>
              )}
              <p className="mt-1 text-[12px] text-d-ink-2">
                {week.count} {s("ledger.itemUnit")}
                {week.top && myShares.categoryMix ? ` · ${categoryLabel(week.top)}` : ""}
              </p>
              <Button size="sm" variant="primary" className="mt-3" onClick={shareWeek} disabled={!canShare}>
                {s("ci.shareWeek")}
              </Button>
              {blocked && <Hint className="mt-2">{blocked}</Hint>}
            </>
          )}
        </Card>

        <Card className="shrink-0" title={c("circle.matrixTitle")}>
          <Hint>{c("circle.matrixIntro")}</Hint>
          <div className="mt-2 flex flex-wrap gap-1">
            {circle.members.map((member) => {
              const on = member.id === target.id;
              return (
                <button
                  key={member.id}
                  type="button"
                  onClick={() => setShareTargetId(member.id)}
                  aria-pressed={on}
                  className={cn(
                    "d-press inline-flex h-6 items-center gap-1 rounded-md px-1.5 text-[11.5px] transition-colors",
                    on ? "bg-d-accent/10 text-d-accent-ink" : "bg-d-fill text-d-ink-2 hover:text-d-ink",
                  )}
                >
                  <span aria-hidden>{member.emoji}</span>
                  {member.name}
                </button>
              );
            })}
          </div>

          <p className="mt-3 text-[12px] font-medium text-d-ink">
            {target.self ? c("circle.matrixMine") : c("circle.matrixTheirs").replace("{name}", target.name)}
          </p>
          <ul className="mt-0.5 divide-y divide-d-hairline">
            {SHARE_ORDER.map((item) => {
              const on = targetShares[item];
              return (
                <li key={item} className="flex items-start gap-3 py-2">
                  <div className="min-w-0 flex-1">
                    <p className="text-[12.5px] text-d-ink">{c(SHARE_LABEL[item])}</p>
                    <p className="text-[11px] leading-[15px] text-d-ink-3">{c(SHARE_HINT[item])}</p>
                  </div>
                  {target.self ? (
                    <Toggle checked={on} onChange={(value) => setMyShares((prev) => ({ ...prev, [item]: value }))} label={c(SHARE_LABEL[item])} />
                  ) : (
                    <span className={cn("shrink-0 pt-0.5 text-[11.5px]", on ? "text-d-ink-2" : "text-d-ink-3")}>{on ? c("circle.shared") : c("circle.unshared")}</span>
                  )}
                </li>
              );
            })}
          </ul>
          <Hint className="mt-2">{c("circle.revokeNote")}</Hint>
          <Hint className="mt-1.5">{c("circle.hierarchyNote")}</Hint>
        </Card>

        <Card className="shrink-0" title={c("circle.visibilityTitle")}>
          <p className="text-[13px] font-medium text-d-ink">{c("circle.visibilityValue")}</p>
          <Hint className="mt-1.5">{c("circle.visibilityHint")}</Hint>
        </Card>
      </div>
    </div>
  );
}
