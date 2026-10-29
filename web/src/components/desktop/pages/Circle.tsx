import { ArrowUp, Share2, Users } from "lucide-react";
import { useMemo, useState } from "react";
import { CATEGORIES } from "@/domain/types";
import { hhmm, weekStartOf, addDays } from "@/logic/time";
import { useLedger } from "@/services/LedgerProvider";
import { cn } from "@/lib/utils";
import { CIRCLES, ME, memberOf, seedMessages, weeklyFacts, type CircleMessage } from "../circleData";
import { money } from "../format";
import { Button, Card, EmptyState, Hint } from "../ui";
import { s } from "../strings";

function Avatar({ emoji, self }: { emoji: string; self?: boolean }) {
  return <span className={cn("grid size-7 shrink-0 place-items-center rounded-full text-[13px]", self ? "bg-d-ink text-d-canvas" : "bg-d-fill text-d-ink")} aria-hidden>{emoji}</span>;
}

export function CirclePage() {
  const { txns, now } = useLedger();
  const [circleId, setCircleId] = useState(CIRCLES[0].id);
  const [messages, setMessages] = useState<CircleMessage[]>(() => seedMessages(Date.now()));
  const [draft, setDraft] = useState("");

  const circle = CIRCLES.find((c) => c.id === circleId) ?? CIRCLES[0];
  const week = useMemo(() => {
    const start = weekStartOf(now);
    return weeklyFacts(txns, start, addDays(start, 7));
  }, [txns, now]);
  const room = useMemo(() => messages.filter((m) => m.circleId === circle.id), [messages, circle.id]);

  const send = (text: string, weekly?: CircleMessage["weekly"]) => {
    const body = text.trim();
    if (!body && !weekly) return;
    setMessages((ms) => [...ms, { id: `local-${Date.now()}`, circleId: circle.id, memberId: ME, text: body, at: Date.now(), weekly }]);
    setDraft("");
  };

  const shareWeek = () => {
    const weekStart = weekStartOf(now);
    const facts = weeklyFacts(txns, weekStart, addDays(weekStart, 7));
    send(s("ci.weekly"), { totalCents: facts.totalCents, count: facts.count, top: facts.top });
  };

  return (
    <div className="grid grid-cols-12 gap-5 xl:h-[calc(100dvh-var(--d-chrome,88px)-44px)] xl:min-h-[520px]">
      <Card className="col-span-12 flex min-h-0 flex-col xl:col-span-3" padded={false} fill title={s("ci.groups")}>
        <nav className="min-h-0 flex-1 space-y-0.5 overflow-y-auto px-2 pb-3">
          {CIRCLES.map((c) => {
            const on = c.id === circle.id;
            return (
              <button
                key={c.id}
                type="button"
                onClick={() => setCircleId(c.id)}
                aria-current={on ? "true" : undefined}
                className={cn(
                  "d-press flex h-8 w-full items-center gap-2 rounded-lg px-2 text-[13px] transition-colors",
                  on ? "bg-d-fill text-d-ink" : "text-d-ink-2 hover:bg-d-fill/50 hover:text-d-ink",
                )}
              >
                <span aria-hidden className="text-[13px]">{c.emoji}</span>
                <span className="min-w-0 flex-1 truncate text-left">{c.name}</span>
                <span className="num text-[11px] text-d-ink-3">{c.members.length}</span>
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
              {s("ci.roomMeta").replace("{count}", String(circle.members.length))}
            </p>
          </div>
          <button type="button" onClick={shareWeek} className="d-press flex h-7 items-center gap-1 rounded-md bg-d-fill px-2 text-[12px] text-d-ink-2 hover:text-d-ink">
            <Share2 className="size-3.5" strokeWidth={1.8} aria-hidden />
            {s("ci.shareWeek")}
          </button>
        </header>

        <div className="d-scroll min-h-0 flex-1 space-y-3 overflow-y-auto px-4 py-3">
          {room.length === 0 ? (
            <EmptyState art="/img/app-empty-circle.webp" title={s("ci.empty")} className="py-8" />
          ) : (
            room.map((m) => {
              const member = memberOf(circle, m.memberId);
              const mine = m.memberId === ME;
              return (
                <div key={m.id} className={cn("flex items-start gap-2", mine && "flex-row-reverse")}>
                  <Avatar emoji={member?.emoji ?? "🙂"} self={mine} />
                  <div className={cn("min-w-0 max-w-[76%]", mine && "items-end text-right")}>
                    <p className="mb-0.5 text-[11px] text-d-ink-3">
                      {member?.name ?? "—"} · {hhmm(m.at)}
                    </p>
                    <div className={cn("rounded-[14px] px-3 py-2 text-[13px] leading-[20px]", mine ? "bg-d-ink text-d-canvas" : "bg-d-fill text-d-ink")}>
                      {m.weekly ? (
                        <div className="text-left">
                          <p className="mb-1 text-[12px]">{s("ci.weekly")}</p>
                          <p className="num text-[15px] font-semibold">{money(m.weekly.totalCents)}</p>
                          <p className="mt-0.5 text-[11.5px] opacity-70">
                            {m.weekly.count} {s("ledger.itemUnit")}
                            {m.weekly.top ? ` · ${CATEGORIES[m.weekly.top].label}` : ""}
                          </p>
                        </div>
                      ) : (
                        m.text
                      )}
                    </div>
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
            send(draft);
          }}
        >
          <input
            value={draft}
            onChange={(e) => setDraft(e.target.value)}
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

      <div className="col-span-12 flex min-h-0 flex-col gap-5 xl:col-span-3">
        <Card title={s("ci.weekCard")}>
          {week.count === 0 ? (
            <Hint>{s("ci.weekEmpty")}</Hint>
          ) : (
            <>
              <p className="text-[11.5px] text-d-ink-3">{s("ci.weekTotal")}</p>
              <p className="num mt-0.5 text-[22px] font-semibold leading-7 text-d-ink">{money(week.totalCents)}</p>
              <p className="mt-1 text-[12px] text-d-ink-2">
                {week.count} {s("ledger.itemUnit")}
                {week.top ? ` · ${CATEGORIES[week.top].label}` : ""}
              </p>
              <Button size="sm" variant="primary" className="mt-3" onClick={shareWeek}>
                {s("ci.shareWeek")}
              </Button>
            </>
          )}
        </Card>

        <Card title={s("ci.members")} padded={false}>
          <ul className="space-y-1 px-3 pb-3">
            {circle.members.map((m) => (
              <li key={m.id} className="flex items-center gap-2 py-1 text-[12.5px]">
                <Avatar emoji={m.emoji} self={m.self} />
                <span className="min-w-0 flex-1 truncate text-d-ink">{m.name}</span>
              </li>
            ))}
          </ul>
        </Card>

        <Card title={s("ci.visibility")}>
          <p className="text-[13px] font-medium text-d-ink">{s("ci.visibilityValue")}</p>
          <Hint className="mt-1.5">{s("ci.visibilityHint")}</Hint>
          <Hint className="mt-3">{s("ci.local")}</Hint>
        </Card>
      </div>
    </div>
  );
}
