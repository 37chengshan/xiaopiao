import { startOfDay } from "@/domain/types";
import { hhmm } from "@/logic/time";
import { useLedger } from "@/services/LedgerProvider";
import { useLocale } from "@/lib/strings";
import { useRouterState } from "@tanstack/react-router";
import { bookWeek, dayEntry } from "./statusModel";
import { useBizBook } from "@/features/biz/store";
import { money } from "./format";
import { s } from "./strings";

const fill = (template: string, ...values: (string | number)[]) =>
  values.reduce<string>((acc, v) => acc.replace("{}", String(v)), template);

/**
 * The 28px status strip under the page (tt 2026-10-28): where the data lives, what today looks like,
 * and when the ledger last moved — quiet facts, no charts. Desktop only; phones keep the full height.
 */
export function StatusBar() {
  useLocale();
  const { txns, now } = useLedger();
  useBizBook();
  const pathname = useRouterState({ select: (state) => state.location.pathname });
  const shop = pathname.startsWith("/app/biz");
  const shopToday = shop ? dayEntry() : null;
  const shopWeek = shop ? bookWeek() : 0;

  const today = startOfDay(now);
  const monthStart = new Date(now);
  monthStart.setDate(1);
  monthStart.setHours(0, 0, 0, 0);

  const todayCount = txns.filter((t) => t.occurredAt >= today && t.direction === "OUT").length;
  const monthCents = txns.reduce((sum, t) => (t.occurredAt >= monthStart.getTime() && t.direction === "OUT" ? sum + t.amountCents : sum), 0);
  const lastWrite = txns.reduce((max, t) => Math.max(max, t.occurredAt), 0);

  return (
    <footer className="hidden h-7 shrink-0 items-center gap-3 border-t border-d-hairline bg-d-canvas px-4 text-[11px] text-d-ink-3 sm:flex">
      <span className="inline-flex items-center gap-1.5">
        <span aria-hidden className="size-1.5 rounded-full bg-d-accent" />
        {s("status.local")}
      </span>
      <span aria-hidden className="h-3 w-px bg-d-hairline" />
      {shop ? (
        <>
          <span className="tabular-nums">{fill(s("status.bizToday"), shopToday?.orders ?? 0, money(shopToday?.revenueCents ?? 0))}</span>
          <span aria-hidden className="h-3 w-px bg-d-hairline" />
          <span className="tabular-nums">{fill(s("status.bizWeek"), money(shopWeek))}</span>
        </>
      ) : (
        <>
          <span className="tabular-nums">{fill(s("status.today"), todayCount)}</span>
          <span aria-hidden className="h-3 w-px bg-d-hairline" />
          <span className="tabular-nums">{fill(s("status.month"), money(-monthCents))}</span>
        </>
      )}
      {lastWrite > 0 && (
        <>
          <span aria-hidden className="h-3 w-px bg-d-hairline" />
          <span className="tabular-nums">{fill(s("status.lastWrite"), hhmm(lastWrite))}</span>
        </>
      )}
      <span className="ml-auto hidden items-center gap-3 md:flex">
        <span className="rounded-full border border-d-hairline px-1.5 text-[10px] tracking-[0.04em]">build i18n-2</span>
        <span>{s("status.search")}</span>
      </span>
    </footer>
  );
}
