import { animate, motion, useMotionValue, useReducedMotion, useTransform } from "framer-motion";
import { forwardRef, useCallback, useImperativeHandle, useLayoutEffect, useRef } from "react";
import type { OptionId } from "@/features/merchant/logic/experiment";
import type { GameRound } from "../logic/game";
import { QuestionFace } from "./QuestionCard";

export interface SwipeDeckHandle {
  /** dir -1 = left (option A), 1 = right (option B) */
  fling: (dir: 1 | -1, velocity?: number) => void;
}

/** Release velocity is projected forward like scroll deceleration (apple-design §6, rate 0.99). */
const project = (v: number, rate = 0.99) => ((v / 1000) * rate) / (1 - rate);
const COMMIT_DISTANCE = 0.3; // of card width, measured at the projected rest point
const MAX_TILT = 12;

/**
 * Two-card deck. The top card follows the pointer 1:1 (offset-preserving), tilts with x, and on release
 * either flies off carrying the finger's velocity or springs back from where it is. The card beneath
 * rises toward full size in proportion to how far the top card has travelled.
 */
export const SwipeDeck = forwardRef<
  SwipeDeckHandle,
  { round: GameRound; next?: GameRound; onChoose: (c: OptionId, dir: 1 | -1) => void }
>(function SwipeDeck({ round, next, onChoose }, ref) {
  const reduce = useReducedMotion();
  const x = useMotionValue(0);
  const opacity = useMotionValue(1);
  const box = useRef<HTMLDivElement>(null);
  const flying = useRef(false);
  const drag = useRef<{ id: number; startX: number; startY: number; originX: number; locked: boolean; hist: { t: number; x: number }[] } | null>(null);

  // New top card: reset travel before paint so the card that was underneath is already in place.
  useLayoutEffect(() => {
    x.stop();
    x.set(0);
    opacity.set(1);
    flying.current = false;
  }, [round.id, x, opacity]);

  const width = () => box.current?.offsetWidth ?? 360;
  const rotate = useTransform(x, (v) => (reduce ? 0 : Math.max(-1, Math.min(1, v / (width() * 0.9))) * MAX_TILT));
  const progress = useTransform(x, (v) => Math.min(1, Math.abs(v) / (width() * 0.6)));
  const underScale = useTransform(progress, (p) => 0.94 + p * 0.06);
  const underY = useTransform(progress, (p) => 12 * (1 - p));
  const leftHint = useTransform(x, (v) => Math.max(0, Math.min(1, -v / (width() * 0.35))));
  const rightHint = useTransform(x, (v) => Math.max(0, Math.min(1, v / (width() * 0.35))));

  const fling = useCallback(
    (dir: 1 | -1, velocity = dir * 900) => {
      if (flying.current) return;
      flying.current = true;
      const choice: OptionId = dir === 1 ? "B" : "A";
      const done = () => onChoose(choice, dir);
      if (reduce) {
        animate(opacity, 0, { duration: 0.15, onComplete: done });
        return;
      }
      animate(x, dir * width() * 1.35, { type: "spring", bounce: 0.1, duration: 0.34, velocity, onComplete: done });
    },
    [onChoose, opacity, reduce, x],
  );
  useImperativeHandle(ref, () => ({ fling }), [fling]);

  const velocityOf = (hist: { t: number; x: number }[]) => {
    const last = hist[hist.length - 1];
    const first = hist.find((h) => last.t - h.t <= 90) ?? last;
    return last.t === first.t ? 0 : ((last.x - first.x) / (last.t - first.t)) * 1000;
  };

  const onPointerDown = (e: React.PointerEvent) => {
    if (flying.current || (e.pointerType === "mouse" && e.button !== 0)) return;
    x.stop(); // grab a card that is springing back, from its live position
    e.currentTarget.setPointerCapture(e.pointerId);
    drag.current = { id: e.pointerId, startX: e.clientX, startY: e.clientY, originX: x.get(), locked: false, hist: [{ t: e.timeStamp, x: e.clientX }] };
  };

  const onPointerMove = (e: React.PointerEvent) => {
    const d = drag.current;
    if (!d || d.id !== e.pointerId) return;
    const dx = e.clientX - d.startX;
    if (!d.locked) {
      if (Math.abs(dx) < 6) return; // hysteresis before committing to the horizontal gesture
      d.locked = true;
    }
    d.hist.push({ t: e.timeStamp, x: e.clientX });
    if (d.hist.length > 8) d.hist.shift();
    x.set(d.originX + dx);
  };

  const release = (e: React.PointerEvent, cancelled: boolean) => {
    const d = drag.current;
    if (!d || d.id !== e.pointerId) return;
    drag.current = null;
    if (!d.locked) return;
    const v = cancelled ? 0 : velocityOf(d.hist);
    const projected = x.get() + project(v);
    if (!cancelled && Math.abs(projected) > width() * COMMIT_DISTANCE) fling(projected > 0 ? 1 : -1, v);
    else animate(x, 0, { type: "spring", bounce: 0.2, duration: 0.4, velocity: v });
  };

  return (
    <div ref={box} className="relative h-[372px]" role="group" aria-label={round.question}>
      {next && (
        <motion.div aria-hidden className="absolute inset-0" style={{ scale: underScale, y: underY, transformOrigin: "50% 100%" }}>
          <QuestionFace kicker={next.kind === "experiment" ? "小店想问你" : "热身小题"} question={next.question} options={next.options} />
        </motion.div>
      )}
      <motion.div
        className="absolute inset-0 cursor-grab touch-pan-y select-none active:cursor-grabbing"
        style={{ x, rotate, opacity, transformOrigin: "50% 120%" }}
        onPointerDown={onPointerDown}
        onPointerMove={onPointerMove}
        onPointerUp={(e) => release(e, false)}
        onPointerCancel={(e) => release(e, true)}
      >
        <QuestionFace
          kicker={round.kind === "experiment" ? "小店想问你" : "热身小题"}
          question={round.question}
          options={round.options}
          leftHint={leftHint}
          rightHint={rightHint}
        />
      </motion.div>
    </div>
  );
});
