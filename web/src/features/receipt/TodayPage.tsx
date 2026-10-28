import { ChevronLeft, ChevronRight } from "lucide-react";
import { useMemo, useState } from "react";
import { EmptyState, ErrorState, LoadingState } from "@/components/app/States";
import { PageHeader } from "@/components/app/PageHeader";
import { useLedger } from "@/services/LedgerProvider";
import { startOfDay } from "../../domain/types";
import { buildDailyReceipt } from "../../logic/dailyReceipt";
import { addDays } from "../../logic/time";
import { Receipt } from "./Receipt";

const stepBtn = "grid size-11 place-items-center rounded-full text-ink-2 transition-colors duration-100 active:bg-fill disabled:opacity-30";

export function TodayPage() {
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
      <PageHeader title="今日小票" subtitle="每天一张，认真生活留下凭证" />
      <div className="flex items-center justify-center gap-1 pb-2">
        <button type="button" onClick={() => nav(-1)} aria-label="前一天" className={stepBtn}>
          <ChevronLeft className="size-5" strokeWidth={1.75} />
        </button>
        <span className="w-24 text-center text-[16px] font-medium text-ink">
          {day === today ? "今天" : day === addDays(today, -1) ? "昨天" : `${new Date(day).getMonth() + 1}月${new Date(day).getDate()}日`}
        </span>
        <button type="button" onClick={() => nav(1)} disabled={day >= today} aria-label="后一天" className={stepBtn}>
          <ChevronRight className="size-5" strokeWidth={1.75} />
        </button>
      </div>
      {isLoading ? <LoadingState rows={3} /> : isError ? <ErrorState onRetry={refetch} /> : txns.length === 0 && day === today ? (
        <EmptyState emoji="🖨️" title="还没有记录，小票机在待命" hint="去「今日」跟小票说一句「午饭 32」，或导入账单" />
      ) : (
        <Receipt receipt={receipt} playKey={playKey} />
      )}
    </>
  );
}
