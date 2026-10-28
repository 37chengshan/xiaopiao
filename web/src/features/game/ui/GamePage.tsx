import { useNavigate } from "@tanstack/react-router";
import { motion, useReducedMotion } from "framer-motion";
import { ChevronLeft, ChevronRight, FlaskConical, Hand, Keyboard, ShieldCheck, Sparkles } from "lucide-react";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { PageHeader } from "@/components/app/PageHeader";
import { Group, IconTile, XButton } from "@/components/app/kit";
import { EmptyState } from "@/components/app/States";
import type { OptionId } from "@/features/merchant/logic/experiment";
import { merchantActions, useMerchantState } from "@/features/merchant/store";
import { spring } from "@/features/merchant/motion";
import { cn } from "@/lib/utils";
import { buildRounds, pickBadge, tagOf, type GameAnswer } from "../logic/game";
import { SwipeDeck, type SwipeDeckHandle } from "./SwipeDeck";

type Phase = "intro" | "playing" | "result";

function ProgressDots({ total, index }: { total: number; index: number }) {
  return (
    <div
      role="progressbar"
      aria-valuemin={1}
      aria-valuemax={total}
      aria-valuenow={Math.min(index + 1, total)}
      aria-label="游戏进度"
      className="flex justify-center gap-2"
    >
      {Array.from({ length: total }).map((_, i) => (
        <span
          key={i}
          className={cn(
            "size-1.5 rounded-full transition-[background-color,transform] duration-200 ease-[var(--ease-out)]",
            i === index ? "scale-125 bg-ink" : i < index ? "bg-ink-3" : "bg-fill",
          )}
        />
      ))}
    </div>
  );
}

export function GamePage() {
  const navigate = useNavigate();
  const reduce = useReducedMotion();
  const merchant = useMerchantState();
  const experiment = merchant.experiment;
  const live = experiment?.status === "running";
  const [phase, setPhase] = useState<Phase>("intro");
  const [seed, setSeed] = useState(7);
  const rounds = useMemo(() => buildRounds(experiment, seed), [experiment, seed]);
  const roundsRef = useRef(rounds);
  const [index, setIndex] = useState(0);
  const [answers, setAnswers] = useState<GameAnswer[]>([]);
  const deck = useRef<SwipeDeckHandle>(null);

  const start = () => {
    roundsRef.current = buildRounds(experiment, seed);
    setIndex(0);
    setAnswers([]);
    setPhase("playing");
  };

  const choose = useCallback(
    (choice: OptionId) => {
      const list = roundsRef.current;
      const round = list[index];
      if (!round) return;
      if (round.kind === "experiment") merchantActions.recordPlayerChoice(choice);
      setAnswers((a) => [...a, { roundId: round.id, kind: round.kind, choice, tag: tagOf(round, choice) }]);
      if (index + 1 >= list.length) setPhase("result");
      else setIndex(index + 1);
    },
    [index],
  );

  useEffect(() => {
    if (phase !== "playing") return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "ArrowLeft") deck.current?.fling(-1);
      else if (e.key === "ArrowRight") deck.current?.fling(1);
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [phase]);

  const replay = () => {
    setSeed((s) => s + 1);
    setPhase("intro");
  };

  const list = roundsRef.current;
  const current = phase === "playing" ? list[index] : undefined;
  const badge = pickBadge(answers);
  const counted = answers.some((a) => a.kind === "experiment");

  return (
    <div>
      <PageHeader
        title="二选一大作战"
        subtitle="楼下的芋泥奶茶铺"
        right={
          <XButton variant="plain" size="sm" onClick={() => navigate({ to: "/merchant" })}>
            回到小店
          </XButton>
        }
      />

      {phase === "intro" && (
        <motion.div initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }} transition={spring} className="pb-6">
          <p className="px-4 pt-1 text-[15px] leading-[22px] text-ink-2">{rounds.length} 道小题，凭直觉选，没有对错。</p>
          <div className="pt-4">
            <Group>
              <GuideRow icon={Hand} title="左滑选左边，右滑选右边" />
              <GuideRow icon={Keyboard} title="也可以点下方按钮，或按键盘 ← →" />
              <GuideRow
                icon={live ? FlaskConical : ShieldCheck}
                title={live ? `其中 1 题匿名计入「${experiment?.title}」` : "这一轮只是热身，不计入任何结果"}
                sub="只算游戏表态，不是真实消费，也不会被识别到个人"
              />
            </Group>
          </div>
          {!live && (
            <EmptyState
              icon={Sparkles}
              title="小店还没开实验"
              hint={
                experiment?.status === "ended"
                  ? "这轮实验已经结束啦，可以先热身玩玩。"
                  : "现在只有热身题。让老板先去发布一个实验吧。"
              }
              action={
                <XButton variant="secondary" size="sm" onClick={() => navigate({ to: "/merchant" })}>
                  去小店看看
                </XButton>
              }
              className="py-8"
            />
          )}
          <div className="px-4 pt-5">
            <XButton variant="primary" className="w-full" onClick={start}>
              {live ? "开始玩" : "先热身一下"}
            </XButton>
          </div>
        </motion.div>
      )}

      {phase === "playing" && current && (
        <div className="space-y-4 px-4 pb-6 pt-2">
          <div className="space-y-2">
            <ProgressDots total={list.length} index={index} />
            <p className="num text-center text-[13px] text-ink-2">
              第 {index + 1} / {list.length} 题
            </p>
          </div>
          <SwipeDeck ref={deck} round={current} next={list[index + 1]} onChoose={choose} />
          <div className="grid grid-cols-2 gap-3">
            <XButton variant="secondary" className="min-w-0" onClick={() => deck.current?.fling(-1)}>
              <ChevronLeft className="size-4 shrink-0" strokeWidth={2} aria-hidden />
              <span className="truncate">{current.options[0].label}</span>
            </XButton>
            <XButton variant="secondary" className="min-w-0" onClick={() => deck.current?.fling(1)}>
              <span className="truncate">{current.options[1].label}</span>
              <ChevronRight className="size-4 shrink-0" strokeWidth={2} aria-hidden />
            </XButton>
          </div>
        </div>
      )}

      {phase === "result" && (
        <div className="pb-6" aria-live="polite">
          <div className="flex flex-col items-center px-8 pb-2 pt-6 text-center">
            <motion.div
              initial={reduce ? { opacity: 0 } : { scale: 0.7, rotate: -8, opacity: 0 }}
              animate={{ scale: 1, rotate: 0, opacity: 1 }}
              transition={reduce ? { duration: 0.15 } : { type: "spring", bounce: 0.35, duration: 0.55 }}
              className="grid size-28 place-items-center rounded-full bg-surface text-[56px] shadow-[0_1px_2px_oklch(0_0_0/6%),0_14px_32px_-14px_oklch(0_0_0/22%)] ring-1 ring-hairline"
              aria-hidden
            >
              {badge.emoji}
            </motion.div>
            <p className="mt-5 text-[13px] text-ink-2">你是</p>
            <h2 className="mt-0.5 font-serif text-[30px] font-semibold leading-[38px] tracking-[-0.02em] text-ink">{badge.title}</h2>
            <p className="mt-2 max-w-[18rem] text-[15px] leading-[22px] text-ink-2">{badge.blurb}</p>
          </div>

          <div className="pt-5">
            <Group>
              <GuideRow
                icon={ShieldCheck}
                title={counted ? "你的选择已匿名计入小店的实验" : "这一轮是热身，没有计入实验"}
                sub="只算游戏表态，不是真实消费，也不会和你的账单混在一起"
              />
            </Group>
          </div>

          <div className="space-y-2 px-4 pt-5">
            <XButton variant="primary" className="w-full" onClick={replay}>
              再玩一轮
            </XButton>
            <XButton variant="secondary" className="w-full" onClick={() => navigate({ to: "/merchant" })}>
              回到小店
            </XButton>
          </div>
        </div>
      )}
    </div>
  );
}

function GuideRow({ icon, title, sub }: { icon: typeof Hand; title: string; sub?: string }) {
  return (
    <div className="flex items-start gap-3 px-4 py-3">
      <IconTile icon={icon} />
      <div className="min-w-0 flex-1">
        <p className="text-[16px] leading-[22px] text-ink">{title}</p>
        {sub && <p className="mt-0.5 text-[13px] leading-[18px] text-ink-2">{sub}</p>}
      </div>
    </div>
  );
}
