import { BarChart3, Users } from "lucide-react";
import { useState } from "react";
import { Group, IconTile, Row, Segmented } from "@/components/app/kit";
import { PageHeader } from "@/components/app/PageHeader";
import { EmptyState, ErrorState, LoadingState } from "@/components/app/States";
import { useLedger } from "@/services/LedgerProvider";
import { RegretSimulator } from "./RegretSimulator";
import { SubscriptionDetective } from "./SubscriptionDetective";
import { WeeklyAwards } from "./WeeklyAwards";

const TABS = [
  { value: "weekly", label: "周报颁奖" },
  { value: "subs", label: "订阅侦探" },
  { value: "regret", label: "后悔模拟" },
] as const;

export function InsightsPage() {
  const { txns, now, isLoading, isError, refetch } = useLedger();
  const [tab, setTab] = useState<(typeof TABS)[number]["value"]>("weekly");

  return (
    <>
      <PageHeader title="洞察" />
      <Segmented className="mx-4 mb-1" value={tab} onChange={setTab} options={TABS} />

      <div className="pb-6">
        {isLoading ? (
          <LoadingState />
        ) : isError ? (
          <ErrorState onRetry={refetch} />
        ) : txns.length === 0 ? (
          <EmptyState icon={BarChart3} title="还没有数据可分析" hint="记几笔或导入账单，周报、订阅侦探和模拟器就会活起来" />
        ) : (
          <>
            {tab === "weekly" && <WeeklyAwards txns={txns} now={now} />}
            {tab === "subs" && <SubscriptionDetective txns={txns} now={now} />}
            {tab === "regret" && <RegretSimulator txns={txns} now={now} />}
          </>
        )}

        <Group className="mt-8">
          <Row
            leading={<IconTile icon={Users} tone="blue" />}
            title="小圈子"
            trailing={<span className="inline-flex h-6 items-center rounded-full bg-fill px-2.5 text-[11px] font-medium text-ink-2">即将上线</span>}
          />
        </Group>
      </div>
    </>
  );
}
