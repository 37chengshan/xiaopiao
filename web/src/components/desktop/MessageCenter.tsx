import { AnimatePresence, motion } from "framer-motion";
import { BarChart3, Repeat, ReceiptText, Users, type LucideIcon } from "lucide-react";
import { useMemo, useState } from "react";
import { useLedger } from "@/services/LedgerProvider";
import { detectSubscriptions } from "@/logic/subscriptions";
import { dateKey, hhmm } from "@/logic/time";
import { startOfDay, type Txn } from "@/domain/types";
import { s } from "./strings";
import { Button } from "./ui";

interface Message {
  id: string;
  icon: LucideIcon;
  title: string;
  at: number;
}

/** Local-only message center (§7.7): what the app itself would notify about, grouped by day. No marketing. */
function messagesFrom(txns: readonly Txn[], now: number): Message[] {
  const out: Message[] = [];
  if (txns.some((t) => t.occurredAt >= startOfDay(now))) out.push({ id: "receipt", icon: ReceiptText, title: s("msg.receipt"), at: now - 12 * 60_000 });
  out.push({ id: "weekly", icon: BarChart3, title: s("msg.weekly"), at: now - 3 * 3_600_000 });
  if (detectSubscriptions(txns, now).length > 0) out.push({ id: "subs", icon: Repeat, title: s("msg.subs"), at: now - 5 * 3_600_000 });
  out.push({ id: "circle", icon: Users, title: s("msg.circle"), at: now - 26 * 3_600_000 });
  return out.sort((a, b) => b.at - a.at);
}

export function MessageCenter({ open, onClose }: { open: boolean; onClose: () => void }) {
  const { txns, now } = useLedger();
  const messages = useMemo(() => messagesFrom(txns, now), [txns, now]);
  const [read, setRead] = useState<Set<string>>(new Set());

  const groups = useMemo(() => {
    const today = dateKey(now);
    const yesterday = dateKey(now - 86_400_000);
    const buckets: { key: string; label: string; items: Message[] }[] = [
      { key: "today", label: s("msg.today"), items: [] },
      { key: "yesterday", label: s("msg.yesterday"), items: [] },
      { key: "earlier", label: s("common.more"), items: [] },
    ];
    for (const m of messages) {
      const key = dateKey(m.at);
      buckets[key === today ? 0 : key === yesterday ? 1 : 2].items.push(m);
    }
    return buckets.filter((b) => b.items.length > 0);
  }, [messages, now]);

  return (
    <AnimatePresence>
      {open && (
        <motion.div className="fixed inset-0 z-50" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0, transition: { duration: 0.14 } }} transition={{ duration: 0.18 }}>
          <button type="button" aria-label={s("common.close")} className="absolute inset-0 bg-black/20" onClick={onClose} />
          <motion.aside
            role="dialog"
            aria-label={s("msg.title")}
            initial={{ x: 340 }}
            animate={{ x: 0 }}
            exit={{ x: 340, transition: { duration: 0.14 } }}
            transition={{ duration: 0.18, ease: [0.23, 1, 0.32, 1] }}
            className="d-scroll absolute inset-y-0 right-0 flex w-[320px] max-w-[92vw] flex-col bg-d-surface shadow-[-12px_0_40px_-20px_oklch(0_0_0/35%)]"
          >
            <header className="flex h-12 shrink-0 items-center justify-between border-b border-d-hairline px-4">
              <h2 className="text-[13px] font-semibold text-d-ink">{s("msg.title")}</h2>
              <Button size="sm" variant="ghost" onClick={() => setRead(new Set(messages.map((m) => m.id)))}>
                {s("msg.readAll")}
              </Button>
            </header>
            <div className="min-h-0 flex-1 overflow-y-auto px-4 py-3">
              {groups.length === 0 ? (
                <p className="py-10 text-center text-[12.5px] text-d-ink-3">{s("msg.empty")}</p>
              ) : (
                groups.map((group) => (
                  <section key={group.key} className="mb-4">
                    <h3 className="mb-1.5 text-[11px] font-semibold uppercase tracking-[0.06em] text-d-ink-3">{group.label}</h3>
                    <ul className="space-y-1">
                      {group.items.map((m) => {
                        const Icon = m.icon;
                        const unread = !read.has(m.id);
                        return (
                          <li key={m.id} className="flex items-center gap-2.5 rounded-lg px-1.5 py-2 hover:bg-d-fill/50">
                            <span className="grid size-7 shrink-0 place-items-center rounded-lg bg-d-fill text-d-ink-2">
                              <Icon className="size-3.5" strokeWidth={1.75} aria-hidden />
                            </span>
                            <span className="min-w-0 flex-1 truncate text-[12.5px] text-d-ink">{m.title}</span>
                            <span className="num shrink-0 text-[11px] text-d-ink-3">{hhmm(m.at)}</span>
                            {unread && <span aria-hidden className="size-1.5 shrink-0 rounded-full bg-d-accent" />}
                          </li>
                        );
                      })}
                    </ul>
                  </section>
                ))
              )}
            </div>
          </motion.aside>
        </motion.div>
      )}
    </AnimatePresence>
  );
}
