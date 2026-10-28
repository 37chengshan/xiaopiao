import { Link } from "@tanstack/react-router";
import {
  AnimatePresence,
  motion,
  useMotionValue,
  useReducedMotion,
  useTransform,
  type PanInfo,
} from "framer-motion";
import { ArrowLeft, ChevronLeft, ChevronRight } from "lucide-react";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { Card, DemoPill, EmptyState, PhoneFrame, PrimaryButton } from "@/features/merchant/ui/common";
import type { OptionId } from "@/features/merchant/logic/experiment";
import { merchantActions, useMerchantState } from "@/features/merchant/store";
import { spring, tokens } from "@/features/merchant/tokens";
import { buildRounds, pickBadge, tagOf, type GameAnswer, type GameRound } from "../logic/game";

const SWIPE_DISTANCE = 100;
const SWIPE_VELOCITY = 500;

type Phase = "intro" | "playing" | "result";

function ProgressDots({ total, index }: { total: number; index: number }) {
  return (
    <div role="progressbar" aria-valuemin={1} aria-valuemax={total} aria-valuenow={Math.min(index + 1, total)} aria-label="游戏进度" className="flex justify-center gap-1.5">
      {Array.from({ length: total }).map((_, i) => (
        <motion.span
          key={i}
          animate={{ scale: i === index ? 1.35 : 1, backgroundColor: i < index ? tokens.coral : i === index ? tokens.ink : tokens.hairline }}
          transition={spring}
          className="size-2 rounded-full"
        />
      ))}
    </div>
  );
}

function OptionFace({ o, side }: { o: GameRound["options"][number]; side: "left" | "right" }) {
  return (
    <div className="relative flex flex-1 flex-col items-center justify-center gap-1.5 px-2 text-center">
      <span aria-hidden className="text-[44px] leading-none">
        {o.emoji}
      </span>
      <p className="text-[16px] font-extrabold">{o.label}</p>
      {o.blurb ? <p className="text-[11px] leading-snug text-(--mb-ink-soft)">{o.blurb}</p> : null}
      <span className="mt-1 text-[11px] font-semibold text-(--mb-ink-soft)">{side === "left" ? "← 左滑" : "右滑 →"}</span>
    </div>
  );
}

function SwipeCard({ round, onChoose }: { round: GameRound; onChoose: (c: OptionId, dir: 1 | -1) => void }) {
  const reduce = useReducedMotion();
  const x = useMotionValue(0);
  const rotate = useTransform(x, [-200, 0, 200], reduce ? [0, 0, 0] : [-14, 0, 14]);
  const leftHint = useTransform(x, [-120, 0], [1, 0]);
  const rightHint = useTransform(x, [0, 120], [0, 1]);
  const leftBg = useTransform(leftHint, (v) => `color-mix(in oklch, ${tokens.coral} ${Math.round(v * 22)}%, transparent)`);
  const rightBg = useTransform(rightHint, (v) => `color-mix(in oklch, ${tokens.grape} ${Math.round(v * 28)}%, transparent)`);
  const [a, b] = round.options;

  const onDragEnd = (_: unknown, info: PanInfo) => {
    if (info.offset.x > SWIPE_DISTANCE || info.velocity.x > SWIPE_VELOCITY) onChoose("B", 1);
    else if (info.offset.x < -SWIPE_DISTANCE || info.velocity.x < -SWIPE_VELOCITY) onChoose("A", -1);
  };

  return (
    <motion.div
      drag="x"
      dragSnapToOrigin
      dragElastic={0.7}
      dragTransition={{ bounceStiffness: 500, bounceDamping: 30 }}
      onDragEnd={onDragEnd}
      style={{ x, rotate, touchAction: "pan-y" }}
      initial={{ opacity: 0, scale: 0.92, y: 18 }}
      animate={{ opacity: 1, scale: 1, y: 0 }}
      variants={{
        exit: (dir: 1 | -1) => ({ x: dir * 420, opacity: 0, rotate: dir * 18, transition: { duration: 0.28 } }),
      }}
      exit="exit"
      transition={spring}
      whileDrag={{ cursor: "grabbing" }}
      className="absolute inset-0 flex cursor-grab touch-pan-y select-none flex-col overflow-hidden rounded-3xl border border-(--mb-line) bg-(--mb-card) shadow-[0_8px_30px_-12px_oklch(0.3_0.02_60/0.35)]"
    >
      <div className="px-5 pt-5 text-center">
        <span className="rounded-full bg-(--mb-canvas) px-2.5 py-1 text-[10px] font-semibold text-(--mb-ink-soft)">
          {round.kind === "experiment" ? "小店想问你" : "热身小题"}
        </span>
        <h2 className="mt-3 text-[20px] font-extrabold leading-snug">{round.question}</h2>
      </div>
      <div className="relative mt-2 flex flex-1">
        <motion.div aria-hidden className="absolute inset-y-0 left-0 w-1/2" style={{ background: leftBg }} />
        <motion.div aria-hidden className="absolute inset-y-0 right-0 w-1/2" style={{ background: rightBg }} />
        <div className="relative flex flex-1 divide-x divide-(--mb-line)">
          <OptionFaceMotion o={a} side="left" hint={leftHint} />
          <OptionFaceMotion o={b} side="right" hint={rightHint} />
        </div>
      </div>
    </motion.div>
  );
}

function OptionFaceMotion({ o, side, hint }: { o: GameRound["options"][number]; side: "left" | "right"; hint: ReturnType<typeof useMotionValue<number>> }) {
  const opacity = useTransform(hint, [0, 1], [0.6, 1]);
  const scale = useTransform(hint, [0, 1], [1, 1.08]);
  return (
    <motion.div className="flex flex-1" style={{ opacity, scale }}>
      <OptionFace o={o} side={side} />
    </motion.div>
  );
}

export function GamePage() {
  const merchant = useMerchantState();
  const experiment = merchant.experiment;
  const live = experiment?.status === "running";
  const [phase, setPhase] = useState<Phase>("intro");
  const [seed, setSeed] = useState(7);
  const rounds = useMemo(() => buildRounds(experiment, seed), [experiment, seed]);
  const roundsRef = useRef(rounds);
  const [index, setIndex] = useState(0);
  const [answers, setAnswers] = useState<GameAnswer[]>([]);
  const [exitDir, setExitDir] = useState<1 | -1>(1);
  const busy = useRef(false);

  const start = () => {
    roundsRef.current = buildRounds(experiment, seed);
    setIndex(0);
    setAnswers([]);
    setPhase("playing");
    busy.current = false;
  };

  const choose = useCallback(
    (choice: OptionId, dir: 1 | -1) => {
      if (busy.current) return;
      const list = roundsRef.current;
      const round = list[index];
      if (!round) return;
      busy.current = true;
      setExitDir(dir);
      if (round.kind === "experiment") merchantActions.recordPlayerChoice(choice);
      const next = [...answers, { roundId: round.id, kind: round.kind, choice, tag: tagOf(round, choice) }];
      setAnswers(next);
      window.setTimeout(() => {
        busy.current = false;
        if (index + 1 >= list.length) setPhase("result");
        else setIndex(index + 1);
      }, 220);
    },
    [answers, index],
  );

  useEffect(() => {
    if (phase !== "playing") return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "ArrowLeft") choose("A", -1);
      else if (e.key === "ArrowRight") choose("B", 1);
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [phase, choose]);

  const replay = () => {
    setSeed((s) => s + 1);
    setPhase("intro");
  };

  const current = phase === "playing" ? roundsRef.current[index] : undefined;
  const badge = pickBadge(answers);
  const counted = answers.some((a) => a.kind === "experiment");

  return (
    <PhoneFrame>
      <header className="flex items-center justify-between gap-2 pb-4">
        <Link
          to="/merchant"
          className="inline-flex items-center gap-1 rounded-full py-1 pr-2 text-[13px] font-semibold text-(--mb-ink-soft) outline-offset-2 focus-visible:outline-2 focus-visible:outline-(--mb-ink)"
        >
          <ArrowLeft className="size-4" aria-hidden /> 回到小店
        </Link>
        <DemoPill />
      </header>

      {phase === "intro" ? (
        <motion.div initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }} transition={spring} className="space-y-4">
          <div>
            <p className="text-[12px] font-semibold text-(--mb-coral-deep)">楼下的芋泥奶茶铺</p>
            <h1 className="text-[28px] font-extrabold leading-tight tracking-tight">二选一大作战</h1>
            <p className="mt-1.5 text-[14px] leading-relaxed text-(--mb-ink-soft)">
              {rounds.length} 道小题，凭直觉选。左滑选左边，右滑选右边，也可以点按钮或按键盘 ← →。
            </p>
          </div>
          {live ? (
            <Card className="bg-(--mb-coral-soft) border-transparent">
              <p className="text-[13px] font-bold">🧪 小店正在做一个小实验</p>
              <p className="mt-1 text-[12px] leading-relaxed text-(--mb-ink-soft)">
                其中 1 题会被匿名计入「{experiment?.title}」，只是游戏表态，不影响你的真实消费，也不会被识别到个人。
              </p>
            </Card>
          ) : (
            <EmptyState
              emoji="🫧"
              title="小店还没开实验"
              body={
                experiment?.status === "ended"
                  ? "这轮实验已经结束啦，可以先热身玩玩，不会计入任何结果。"
                  : "现在只有热身题，选择不会计入任何结果。让老板先去发布一个实验吧。"
              }
              action={
                <Link
                  to="/merchant"
                  className="inline-flex h-11 w-full items-center justify-center rounded-2xl border border-(--mb-line) bg-(--mb-card) text-[14px] font-semibold outline-offset-2 focus-visible:outline-2 focus-visible:outline-(--mb-ink)"
                >
                  去小店看看
                </Link>
              }
            />
          )}
          <PrimaryButton onClick={start}>{live ? "开始玩" : "先热身一下"}</PrimaryButton>
        </motion.div>
      ) : null}

      {phase === "playing" && current ? (
        <div className="space-y-4">
          <ProgressDots total={roundsRef.current.length} index={index} />
          <p className="text-center text-[12px] text-(--mb-ink-soft)">
            第 {index + 1} / {roundsRef.current.length} 题
          </p>
          <div className="relative h-[22rem]" role="group" aria-label={current.question}>
            <AnimatePresence custom={exitDir} initial={false}>
              <SwipeCard key={current.id} round={current} onChoose={choose} />
            </AnimatePresence>
          </div>
          <div className="grid grid-cols-2 gap-3">
            <button
              type="button"
              onClick={() => choose("A", -1)}
              className="flex h-14 items-center justify-center gap-1 rounded-2xl border border-(--mb-line) bg-(--mb-card) px-2 text-[14px] font-bold outline-offset-2 active:scale-95 focus-visible:outline-2 focus-visible:outline-(--mb-ink)"
            >
              <ChevronLeft className="size-4 shrink-0" aria-hidden />
              <span className="truncate">{current.options[0].label}</span>
            </button>
            <button
              type="button"
              onClick={() => choose("B", 1)}
              className="flex h-14 items-center justify-center gap-1 rounded-2xl bg-(--mb-ink) px-2 text-[14px] font-bold text-white outline-offset-2 active:scale-95 focus-visible:outline-2 focus-visible:outline-(--mb-ink)"
            >
              <span className="truncate">{current.options[1].label}</span>
              <ChevronRight className="size-4 shrink-0" aria-hidden />
            </button>
          </div>
        </div>
      ) : null}

      {phase === "result" ? (
        <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} className="space-y-4" aria-live="polite">
          <motion.div
            initial={{ scale: 0.6, rotate: -8, opacity: 0 }}
            animate={{ scale: 1, rotate: 0, opacity: 1 }}
            transition={{ type: "spring", stiffness: 260, damping: 14 }}
            className="rounded-3xl border border-(--mb-line) bg-(--mb-card) px-6 py-8 text-center shadow-[0_8px_30px_-12px_oklch(0.3_0.02_60/0.35)]"
          >
            <motion.div
              aria-hidden
              animate={{ rotate: [0, -8, 8, 0] }}
              transition={{ delay: 0.5, duration: 0.8 }}
              className="mx-auto grid size-24 place-items-center rounded-full bg-(--mb-coral-soft) text-5xl ring-4 ring-(--mb-coral)"
            >
              {badge.emoji}
            </motion.div>
            <p className="mt-4 text-[13px] text-(--mb-ink-soft)">你是</p>
            <h1 className="text-[28px] font-extrabold tracking-tight">『{badge.title}』！</h1>
            <p className="mx-auto mt-2 max-w-[18rem] text-[13px] leading-relaxed text-(--mb-ink-soft)">{badge.blurb}</p>
          </motion.div>

          <Card>
            <p className="text-[13px] font-bold">{counted ? "你的选择已匿名计入小店的实验 ✅" : "这一轮是热身，没有计入实验"}</p>
            <p className="mt-1 text-[12px] leading-relaxed text-(--mb-ink-soft)">
              只算「游戏表态」，不是真实消费，也不会和你的账单混在一起。
            </p>
          </Card>

          <div className="space-y-2">
            <PrimaryButton onClick={replay}>再玩一轮</PrimaryButton>
            <Link
              to="/merchant"
              className="inline-flex h-11 w-full items-center justify-center rounded-2xl border border-(--mb-line) bg-(--mb-card) text-[14px] font-semibold outline-offset-2 focus-visible:outline-2 focus-visible:outline-(--mb-ink)"
            >
              回到小店
            </Link>
          </div>
        </motion.div>
      ) : null}
    </PhoneFrame>
  );
}
