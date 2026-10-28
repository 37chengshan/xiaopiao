import { useNavigate } from "@tanstack/react-router";
import { motion, useReducedMotion } from "framer-motion";
import { FlaskConical, Users } from "lucide-react";
import { Group, IconTile, Row, SectionHeader, XButton } from "@/components/app/kit";
import { EmptyState } from "@/components/app/States";
import { cn } from "@/lib/utils";
import { STATUS_LABELS, computeResults, type Experiment, type OptionResult } from "../logic/experiment";
import { copy } from "../copy";
import { spring } from "../motion";

const pct = (v: number) => `${Math.round(v * 100)}%`;

/** Share bar: neutral, the leading option in clay. A thin 95% interval whisker sits under the bar in ink-3. */
function OptionBar({ label, r, leader, index }: { label: string; r: OptionResult; leader: boolean; index: number }) {
  const reduce = useReducedMotion();
  const [lo, hi] = r.ci;
  return (
    <div className="px-4 py-4">
      <div className="flex items-baseline justify-between gap-3">
        <span className="text-[16px] font-medium leading-[22px] text-ink">{label}</span>
        <span className={cn("num text-[34px] font-semibold leading-[38px] tracking-[-0.03em]", leader ? "text-clay" : "text-ink")}>{pct(r.share)}</span>
      </div>
      <div
        className="mt-2"
        role="img"
        aria-label={`${label} ${pct(r.share)}，${r.count} 人，95% 区间 ${pct(lo)} 到 ${pct(hi)}`}
      >
        <div className="h-2.5 overflow-hidden rounded-full bg-fill">
          <motion.div
            className={cn("h-full origin-left rounded-full", leader ? "bg-clay" : "bg-ink-3/60")}
            style={{ width: `${r.share * 100}%` }}
            initial={reduce ? { opacity: 0 } : { scaleX: 0 }}
            animate={reduce ? { opacity: 1 } : { scaleX: 1 }}
            transition={{ ...spring, delay: index * 0.05 }}
          />
        </div>
        <div className="relative mt-2 h-2">
          <motion.div
            className="absolute top-1/2 h-px -translate-y-1/2 bg-ink-3"
            style={{ left: `${lo * 100}%`, width: `${(hi - lo) * 100}%` }}
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            transition={{ delay: 0.2, duration: 0.18 }}
          >
            <span className="absolute -left-px top-1/2 h-2 w-px -translate-y-1/2 bg-ink-3" />
            <span className="absolute -right-px top-1/2 h-2 w-px -translate-y-1/2 bg-ink-3" />
          </motion.div>
        </div>
      </div>
      <p className="mt-1 text-[13px] leading-[18px] text-ink-2">
        {r.count} 人 · 95% 区间 {pct(lo)}–{pct(hi)}
      </p>
    </div>
  );
}

export function ResultsStep({
  experiment,
  onFinish,
  onNext,
  onBackToLaunch,
}: {
  experiment: Experiment | null;
  onFinish: () => void;
  onNext: () => void;
  onBackToLaunch: () => void;
}) {
  const navigate = useNavigate();
  if (!experiment || experiment.status === "draft") {
    return (
      <EmptyState
        icon={FlaskConical}
        title={copy.results.emptyTitle}
        hint={copy.results.emptyHint}
        action={
          <XButton variant="secondary" onClick={onBackToLaunch}>
            {copy.results.emptyAction}
          </XButton>
        }
      />
    );
  }
  const res = computeResults(experiment);
  const [a, b] = experiment.options;
  const running = experiment.status === "running";
  return (
    <div className="pb-6">
      <div className="px-4 pt-1">
        <span className="inline-flex h-6 items-center whitespace-nowrap rounded-full bg-fill px-2.5 text-[11px] font-medium tracking-[0.02em] text-ink-2">
          {STATUS_LABELS[experiment.status]}
        </span>
        <h2 className="mt-1.5 text-[18px] font-semibold leading-[24px] text-ink [overflow-wrap:anywhere]">{experiment.question}</h2>
      </div>

      <div className="pt-4">
        <Group>
          <OptionBar label={a.label} r={res.options[0]} leader={res.leader === "A"} index={0} />
          <OptionBar label={b.label} r={res.options[1]} leader={res.leader === "B"} index={1} />
        </Group>
      </div>

      <SectionHeader>{copy.results.sample}</SectionHeader>
      <Group>
        <div aria-live="polite">
          <Row
            leading={<IconTile icon={Users} />}
            title={res.note}
            subtitle={<span className="whitespace-normal">演示样本 {res.demoCount} 人 + 你邀请的顾客 {res.playerCount} 人</span>}
            className="[&_span.truncate]:whitespace-normal"
          />
        </div>
      </Group>

      <div className="space-y-2 px-4 pt-5">
        <XButton variant="primary" className="w-full" onClick={onNext}>
          {copy.results.next}
        </XButton>
        {running ? (
          <>
            <XButton variant="secondary" className="w-full" onClick={() => navigate({ to: "/game" })}>
              {copy.results.again}
            </XButton>
            <div className="flex justify-center pt-1">
              <XButton variant="plain" size="sm" className="text-ink-2" onClick={onFinish}>
                {copy.results.finish}
              </XButton>
            </div>
          </>
        ) : (
          <p className="pt-1 text-center text-[13px] text-ink-2">{copy.results.ended}</p>
        )}
      </div>
    </div>
  );
}
