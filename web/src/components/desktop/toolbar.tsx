import { Menu, Printer, Search, UserRound } from "lucide-react";
import { useMemo } from "react";
import { useLedger } from "@/services/LedgerProvider";
import { startOfDay } from "@/domain/types";
import { money } from "./format";
import { go } from "./nav";
import { s } from "./strings";
import { Kbd } from "./ui";

/** Toolbar receipt control: a 28×34 strip of paper with today's total, click for the print preview. */
function ReceiptWidget({ onPrint }: { onPrint: () => void }) {
  const { txns, now } = useLedger();
  const total = useMemo(() => {
    const today = startOfDay(now);
    return txns.filter((t) => t.direction === "OUT" && t.occurredAt >= today).reduce((sum, t) => sum + t.amountCents, 0);
  }, [txns, now]);
  return (
    <button
      type="button"
      onClick={onPrint}
      title={`${s("toolbar.receipt")} · ${money(total)}`}
      aria-label={s("toolbar.receipt")}
      className="press group relative grid h-[34px] w-[28px] shrink-0 place-items-center bg-d-paper text-d-ink shadow-[0_1px_2px_oklch(0_0_0/12%)] transition-transform hover:-translate-y-0.5 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-d-accent"
      style={{ clipPath: "polygon(0 0, 100% 0, 100% calc(100% - 4px), 87.5% 100%, 75% calc(100% - 4px), 62.5% 100%, 50% calc(100% - 4px), 37.5% 100%, 25% calc(100% - 4px), 12.5% 100%, 0 calc(100% - 4px))" }}
    >
      <span className="font-mono text-[8px] leading-none tracking-[-0.02em] text-d-ink/80">
        {total >= 100000 ? `${Math.round(total / 100 / 1000)}k` : Math.round(total / 100)}
      </span>
      <Printer aria-hidden className="mt-0.5 size-[9px] text-d-ink/45" strokeWidth={2} />
    </button>
  );
}

export function Toolbar({ title, onSearch, onPrint, onOpenSidebar }: { title: string; onSearch: () => void; onPrint: () => void; onOpenSidebar?: () => void }) {
  return (
    <header className="flex h-[52px] shrink-0 items-center gap-3 border-b border-d-hairline px-4 lg:px-6">
      {onOpenSidebar && (
        <button type="button" onClick={onOpenSidebar} aria-label={s("nav.open")} className="press grid size-8 place-items-center rounded-lg text-d-ink-2 hover:bg-d-fill md:hidden">
          <Menu className="size-4" strokeWidth={1.75} aria-hidden />
        </button>
      )}
      <h1 className="min-w-0 shrink truncate text-[15px] font-semibold tracking-[-0.01em] text-d-ink">{title}</h1>

      <button
        type="button"
        onClick={onSearch}
        aria-label={s("toolbar.searchAria")}
        className="press ml-2 hidden h-8 w-[260px] shrink-0 items-center gap-2 rounded-lg bg-d-fill px-2.5 text-left text-[12.5px] text-d-ink-3 transition-colors hover:bg-d-hairline md:flex"
      >
        <Search className="size-3.5 shrink-0" strokeWidth={1.9} aria-hidden />
        <span className="min-w-0 flex-1 truncate">{s("toolbar.search")}</span>
        <Kbd>⌘K</Kbd>
      </button>

      <div className="min-w-0 flex-1" />

      <p className="hidden items-center gap-1.5 text-[11.5px] text-d-ink-3 sm:flex" title={s("toolbar.localHint")}>
        <span aria-hidden className="size-1.5 rounded-full bg-d-ink-3" />
        {s("toolbar.local")}
      </p>

      <ReceiptWidget onPrint={onPrint} />

      <button
        type="button"
        onClick={() => go("/app/settings")}
        aria-label={s("nav.settings")}
        className="press grid size-7 shrink-0 place-items-center rounded-full bg-d-ink text-[10px] font-semibold text-d-canvas"
      >
        <UserRound className="size-3.5" strokeWidth={2} aria-hidden />
      </button>
    </header>
  );
}
