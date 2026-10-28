import { Link } from "@tanstack/react-router";
import { motion } from "framer-motion";
import { STATUS_LABELS, computeResults, type Experiment, type OptionResult } from "../logic/experiment";
import { softSpring, tokens } from "../tokens";
import { Card, DemoPill, EmptyState, GhostButton, PrimaryButton, SectionTitle } from "./common";

const pct = (v: number) => `${Math.round(v * 100)}%`;

/** Share bar with a 95% CI whisker drawn on a 0–100% track. */
function OptionBar({
  emoji,
  label,
  r,
  color,
  leader,
}: {
  emoji: string;
  label: string;
  r: OptionResult;
  color: string;
  leader: boolean;
}) {
  const [lo, hi] = r.ci;
  return (
    <div>
      <div className="mb-1.5 flex items-baseline justify-between gap-2">
        <span className="text-[14px] font-bold">
          {emoji} {label} {leader ? <span className="ml-1 rounded-full bg-(--mb-coral-soft) px-1.5 py-0.5 text-[10px] text-(--mb-coral-deep)">领先</span> : null}
        </span>
        <span className="text-[20px] font-extrabold tabular-nums">{pct(r.share)}</span>
      </div>
      <div
        className="relative h-6 overflow-hidden rounded-full bg-(--mb-canvas)"
        role="img"
        aria-label={`${label} ${pct(r.share)}，${r.count} 人，95% 区间 ${pct(lo)} 到 ${pct(hi)}`}
      >
        <motion.div
          className="absolute inset-y-0 left-0 rounded-full"
          style={{ background: color }}
          initial={{ width: 0 }}
          animate={{ width: `${r.share * 100}%` }}
          transition={softSpring}
        />
        <motion.div
          className="absolute top-1/2 h-0.5 -translate-y-1/2 rounded-full bg-(--mb-ink)"
          initial={{ opacity: 0 }}
          animate={{ opacity: 0.8, left: `${lo * 100}%`, width: `${(hi - lo) * 100}%` }}
          transition={{ ...softSpring, delay: 0.15 }}
        >
          <span className="absolute -left-px top-1/2 h-3 w-0.5 -translate-y-1/2 bg-(--mb-ink)" />
          <span className="absolute -right-px top-1/2 h-3 w-0.5 -translate-y-1/2 bg-(--mb-ink)" />
        </motion.div>
      </div>
      <p className="mt-1 text-[11px] text-(--mb-ink-soft)">
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
  if (!experiment || experiment.status === "draft") {
    return (
      <EmptyState
        emoji="🧋"
        title="还没有结果可看"
        body="先在上一步选个模板并发布，顾客玩过之后，结果会出现在这里。"
        action={<PrimaryButton onClick={onBackToLaunch}>去发起实验</PrimaryButton>}
      />
    );
  }
  const res = computeResults(experiment);
  const [a, b] = experiment.options;
  const stable = res.sampleSize >= 30;
  return (
    <div className="space-y-3">
      <Card>
        <div className="flex items-center justify-between gap-2">
          <p className="text-[12px] text-(--mb-ink-soft)">{experiment.title}</p>
          <span className="flex items-center gap-1.5">
            <span className="rounded-full bg-(--mb-ink) px-2.5 py-1 text-[11px] font-semibold text-white">
              {STATUS_LABELS[experiment.status]}
            </span>
            <DemoPill />
          </span>
        </div>
        <p className="mt-1 text-[16px] font-bold">{experiment.question}</p>
        <div className="mt-4 space-y-4">
          <OptionBar emoji={a.emoji} label={a.label} r={res.options[0]} color={tokens.coral} leader={res.leader === "A"} />
          <OptionBar emoji={b.emoji} label={b.label} r={res.options[1]} color={tokens.grape} leader={res.leader === "B"} />
        </div>
      </Card>

      <Card aria-live="polite">
        <SectionTitle>样本与可信度</SectionTitle>
        <div className="flex items-center gap-3">
          <div className="grid size-12 shrink-0 place-items-center rounded-2xl text-xl" style={{ background: stable ? tokens.coralSoft : tokens.canvas }} aria-hidden>
            {stable ? "👍" : "🌱"}
          </div>
          <div>
            <p className="text-[14px] font-bold">{res.note}</p>
            <p className="mt-0.5 text-[12px] text-(--mb-ink-soft)">
              演示样本 {res.demoCount} 人 + 你邀请的顾客 {res.playerCount} 人。游戏表态不等于真实下单。
            </p>
          </div>
        </div>
      </Card>

      <div className="space-y-2">
        {experiment.status === "running" ? (
          <Link
            to="/game"
            className="inline-flex h-12 w-full items-center justify-center gap-2 rounded-2xl bg-(--mb-coral) px-5 text-[15px] font-bold text-white outline-offset-2 focus-visible:outline-2 focus-visible:outline-(--mb-ink)"
          >
            邀请顾客再玩一轮 🎮
          </Link>
        ) : (
          <p className="rounded-2xl bg-(--mb-canvas) px-4 py-3 text-center text-[12px] text-(--mb-ink-soft)">
            实验已结束，不再接收新的游戏表态
          </p>
        )}
        {experiment.status === "running" ? (
          <GhostButton onClick={onFinish}>结束实验，锁定结果</GhostButton>
        ) : null}
        <GhostButton onClick={onNext}>看看 3 个下一步选择</GhostButton>
      </div>
    </div>
  );
}
