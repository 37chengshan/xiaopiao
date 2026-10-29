import { ChevronLeft, ChevronRight } from "lucide-react";
import { useMemo, useState } from "react";
import { EmptyState, ErrorState, LoadingState } from "@/components/app/States";
import { useLocale } from "@/lib/strings";
import { useLedger } from "@/services/LedgerProvider";
import { startOfDay } from "../../domain/types";
import { buildDailyReceipt } from "../../logic/dailyReceipt";
import { addDays } from "../../logic/time";
import { Receipt } from "./Receipt";
import { useReceiptStrings } from "./strings";

const stepBtn = "grid size-11 place-items-center rounded-full text-ink-2 transition-colors duration-100 active:bg-fill disabled:opacity-30";

export function TodayPage() {
  const t = useReceiptStrings();
  const locale = useLocale();
  const { txns, now, isLoading, isError, refetch } = useLedger();
  const today = startOfDay(now);
  const [day, setDay] = useState(today);
  const [playKey, setPlayKey] = useState(0);
  const receipt = useMemo(() => buildDailyReceipt(txns, day, now), [txns, day, now]);

  const nav = (delta: number) => {
    setDay((d) => Math.min(today, addDays(d, delta)));
    setPlayKey((k) => k + 1);
  };

  return (
    <>
      <div className="relative flex h-14 items-center justify-center gap-1 px-4 pt-[env(safe-area-inset-top,0px)]">
        <button type="button" onClick={() => nav(-1)} aria-label={t("today.prev")} className={stepBtn}>
          <ChevronLeft className="size-5" strokeWidth={1.75} />
        </button>
        <span className="w-24 text-center text-[16px] font-medium text-ink">
          {day === today
            ? t("today.today")
            : day === addDays(today, -1)
              ? t("today.yesterday")
              : new Intl.DateTimeFormat(locale === "en" ? "en-US" : "zh-CN", { month: "short", day: "numeric" }).format(day)}
        </span>
        <button type="button" onClick={() => nav(1)} disabled={day >= today} aria-label={t("today.next")} className={stepBtn}>
          <ChevronRight className="size-5" strokeWidth={1.75} />
        </button>
      </div>
      {isLoading ? <LoadingState rows={3} /> : isError ? <ErrorState onRetry={refetch} /> : txns.length === 0 && day === today ? (
        <EmptyState emoji="🖨️" title={t("today.empty.title")} hint={t("today.empty.hint")} />
      ) : (
        <Receipt receipt={receipt} playKey={playKey} />
      )}
    </>
  );
}
