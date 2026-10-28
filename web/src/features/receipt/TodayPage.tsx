import { ChevronLeft, ChevronRight, RotateCcw } from "lucide-react";
import { useMemo, useState } from "react";
import { EmptyState, ErrorState, LoadingState } from "@/components/app/States";
import { PageHeader } from "@/components/app/PageHeader";
import { useLedger } from "@/services/LedgerProvider";
import { startOfDay } from "../../domain/types";
import { buildDailyReceipt } from "../../logic/dailyReceipt";
import { addDays } from "../../logic/time";
import { Receipt } from "./Receipt";

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
      <PageHeader
        title="今日小票"
        subtitle="每天只领一张～"
        right={
          <button onClick={() => setPlayKey((k) => k + 1)} aria-label="重新打印" className="grid size-9 place-items-center rounded-full bg-card text-ink-2 shadow-sm active:scale-95">
            <RotateCcw className="size-4" />
          </button>
        }
      />
      <div className="flex items-center justify-center gap-4 pb-1 text-sm">
        <button onClick={() => nav(-1)} aria-label="前一天" className="grid size-8 place-items-center rounded-full bg-card shadow-sm">
          <ChevronLeft className="size-4" />
        </button>
        <span className="w-24 text-center font-semibold">{day === today ? "今天" : day === addDays(today, -1) ? "昨天" : `${new Date(day).getMonth() + 1}月${new Date(day).getDate()}日`}</span>
        <button onClick={() => nav(1)} disabled={day >= today} aria-label="后一天" className="grid size-8 place-items-center rounded-full bg-card shadow-sm disabled:opacity-30">
          <ChevronRight className="size-4" />
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
