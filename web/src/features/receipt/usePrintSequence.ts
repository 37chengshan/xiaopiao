import { animate, type AnimationPlaybackControls, type MotionValue } from "framer-motion";
import { useCallback, useEffect, useReducer, useRef, useState, type RefObject } from "react";
import { EASE_OUT } from "@/lib/motion";
import { nextPhase } from "./physics";
import { dueTicks, planPrint } from "./printTimeline";
import { measureRows, type StampState } from "./ReceiptPaper";
import { haptic } from "./haptics";
import { printTick, stampThump } from "./sound";

const START_DELAY = 240;
/** The inline desktop stage starts on the click instead of on mount, so it only needs a breath. */
const MANUAL_DELAY = 90;
const STAMP_DELAY = 180;

/**
 * Drives one print run: the leading edge travels linearly down the paper (`reveal`, px), each row it uncovers
 * ticks, the total ticker starts when the edge passes it, then the stamp lands. `skip` finishes from wherever the
 * edge currently is (no teleport). `onTick` fires on every gear beat (paper micro-shake).
 *
 * `auto` = play as soon as the paper is measured (the phone's print moment). With `auto: false` the paper rests
 * finished until `start()` is called, which is what the desktop page wants: the sheet is visible as a preview and
 * the button replays the ritual.
 */
export function usePrintSequence({
  paperRef,
  reveal,
  reduce,
  onTick,
  auto = true,
}: {
  paperRef: RefObject<HTMLDivElement | null>;
  reveal: MotionValue<number>;
  reduce: boolean;
  onTick?: () => void;
  auto?: boolean;
}) {
  const [phase, send] = useReducer(nextPhase, "idle");
  // A manual stage rests on the finished sheet; the phone's stage starts on a blank roll behind its scrim.
  const resting = !auto || reduce;
  const [stamp, setStamp] = useState<StampState>(resting ? "shown" : "hidden");
  const [tickerRun, setTickerRun] = useState(resting);
  const [instant, setInstant] = useState(resting);
  const [edge, setEdge] = useState(!resting);
  const [paperH, setPaperH] = useState(0);
  const run = useRef<AnimationPlaybackControls | null>(null);
  const timers = useRef<number[]>([]);
  const height = useRef(0);
  const tickHandler = useRef(onTick);
  tickHandler.current = onTick;

  const clear = () => {
    run.current?.stop();
    timers.current.forEach(clearTimeout);
    timers.current = [];
  };

  /** Jump to the finished sheet: everything uncovered, ticker rolled, stamp on the paper, no leading edge. */
  const settle = useCallback(() => {
    clear();
    setEdge(false);
    setTickerRun(true);
    setInstant(true);
    setStamp("shown");
    send("skip");
    reveal.set(height.current + 80);
  }, [reveal]);

  /**
   * The same picture as `settle`, but the phase stays `idle`: a manual stage rests on a finished-looking
   * sheet as its preview, and a tear only makes sense once this run has actually printed.
   */
  const rest = useCallback(() => {
    clear();
    setEdge(false);
    setTickerRun(true);
    setInstant(true);
    setStamp("shown");
    reveal.set(height.current + 80);
  }, [reveal]);

  const play = useCallback(
    (delay: number) => {
      const paper = paperRef.current;
      if (!paper) return;
      clear();
      const m = measureRows(paper);
      height.current = m.height;
      setPaperH(m.height);
      if (reduce) {
        settle();
        return;
      }
      const plan = planPrint(m.height, m.rowBottoms, m.totalBottom);
      // Replay: back to a blank roll, then feed again from the slot.
      send("close");
      setInstant(false);
      setTickerRun(false);
      setStamp("hidden");
      setEdge(true);
      reveal.set(0);
      let next = 0;
      let totalStarted = false;
      timers.current.push(
        window.setTimeout(() => {
          send("start");
          run.current = animate(reveal, m.height, {
            duration: plan.duration,
            ease: "linear",
            onUpdate: (v) => {
              const n = dueTicks(m.rowBottoms, next, v);
              if (n > next) {
                printTick();
                haptic("tick");
                tickHandler.current?.();
                next = n;
              }
              if (!totalStarted && v >= m.totalBottom) {
                totalStarted = true;
                setTickerRun(true);
              }
            },
            onComplete: () => {
              reveal.set(m.height + 80);
              setEdge(false);
              timers.current.push(
                window.setTimeout(() => {
                  setStamp("slam");
                  stampThump();
                  haptic("stamp");
                  send("done");
                }, STAMP_DELAY),
              );
            },
          });
        }, delay),
      );
    },
    [paperRef, reveal, reduce, settle],
  );

  // Rest on the finished sheet (manual stage) or print by itself (phone's print moment).
  useEffect(() => {
    const paper = paperRef.current;
    if (!paper) return;
    const h = measureRows(paper).height;
    height.current = h;
    setPaperH(h);
    if (!auto) rest();
    else if (reduce) settle();
    else play(START_DELAY);
    return clear;
  }, [paperRef, reduce, auto, play, settle, rest]);

  const skip = () => {
    if (reduce) {
      settle();
      return;
    }
    clear();
    setEdge(false);
    setTickerRun(true);
    setStamp("shown");
    send("skip");
    // interruptible: the edge carries on from where it is, it does not jump
    run.current = animate(reveal, height.current + 80, { duration: 0.18, ease: EASE_OUT });
  };

  return { phase, send, stamp, tickerRun, instant, edge, paperH, skip, start: () => play(MANUAL_DELAY) };
}
