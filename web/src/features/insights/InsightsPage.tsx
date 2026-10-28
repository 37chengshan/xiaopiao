import { useState } from "react";
import { EmptyState, ErrorState, LoadingState } from "@/components/app/States";
import { PageHeader } from "@/components/app/PageHeader";
import { cn } from "@/lib/utils";
import { useLedger } from "@/services/LedgerProvider";
import { RegretSimulator } from "./RegretSimulator";
import { SubscriptionDetective } from "./SubscriptionDetective";
import { WeeklyAwards } from "./WeeklyAwards";

const TABS = [
  ["weekly", "周报颁奖"],
  ["subs", "订阅侦探"],
  ["regret", "后悔模拟"],
] as const;

export function InsightsPage() {
  const { txns, now, isLoading, isError, refetch } = useLedger();
  const [tab, setTab] = useState<(typeof TABS)[number][0]>("weekly");

  return (
    <>
      <PageHeader title="洞察" subtitle="AI 只负责总结和模拟，决定权在你" />
      <div className="px-4 pb-6">
        <div className="mb-3 flex rounded-full bg-secondary p-0.5" role="tablist">
          {TABS.map(([k, label]) => (
            <button key={k} role="tab" aria-selected={tab === k} onClick={() => setTab(k)} className={cn("flex-1 rounded-full py-1.5 text-[13px] font-semibold transition", tab === k ? "bg-card text-ink shadow-sm" : "text-ink-2")}>
              {label}
            </button>
          ))}
        </div>

        {isLoading ? (
          <LoadingState />
        ) : isError ? (
          <ErrorState onRetry={refetch} />
        ) : txns.length === 0 ? (
          <EmptyState emoji="📊" title="还没有数据可分析" hint="记几笔或导入账单，周报、订阅侦探和模拟器就会活起来" />
        ) : (
          <>
            {tab === "weekly" && <WeeklyAwards txns={txns} now={now} />}
            {tab === "subs" && <SubscriptionDetective txns={txns} now={now} />}
            {tab === "regret" && <RegretSimulator txns={txns} now={now} />}
          </>
        )}

        <div className="mt-5 flex items-center gap-3 rounded-2xl border border-dashed border-hairline bg-card/60 p-4">
          <span className="text-3xl" aria-hidden>
            👯
          </span>
          <div className="min-w-0 flex-1">
            <p className="font-extrabold text-ink">
              小圈子 <span className="ml-1 rounded-full bg-secondary px-2 py-0.5 text-[11px] font-semibold text-ink-2">即将上线</span>
            </p>
            <p className="text-[12px] text-ink-2">和好友一起比周报头衔，只分享头衔，不分享金额。</p>
          </div>
        </div>
      </div>
    </>
  );
}
