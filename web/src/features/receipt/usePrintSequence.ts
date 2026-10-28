import { animate, type AnimationPlaybackControls, type MotionValue } from "framer-motion";
import { useEffect, useReducer, useRef, useState, type RefObject } from "react";
import { nextPhase } from "./physics";
import { dueTicks, planPrint } from "./printTimeline";
import { measureRows, type StampState } from "./ReceiptPaper";
import { printTick, stampThump } from "./sound";

const START_DELAY = 240;
const STAMP_DELAY = 180;

export function vibrate(ms: number) {
  try {
    navigator.vibrate?.(ms);
  } catch {
    /* not supported */
  }
}

/**
 * Drives one print run: the leading edge travels linearly down the paper (`reveal`, px), each row it uncovers
 * ticks, the total ticker starts when the edge passes it, then the stamp lands. `skip` jumps to the finished paper.
 */
export function usePrintSequence({ paperRef, reveal, reduce }: { paperRef: RefObject<HTMLDivElement | null>; reveal: MotionValue<number>; reduce: boolean }) {
  const [phase, send] = useReducer(nextPhase, "idle");
  const [stamp, setStamp] = useState<StampState>(reduce ? "shown" : "hidden");
  const [tickerRun, setTickerRun] = useState(reduce);
  const [instant, setInstant] = useState(reduce);
  const [edge, setEdge] = useState(!reduce);
  const [paperH, setPaperH] = useState(0);
  const run = useRef<AnimationPlaybackControls | null>(null);
  const timers = useRef<number[]>([]);
  const height = useRef(0);

  const clear = () => {
    run.current?.stop();
    timers.current.forEach(clearTimeout);
    timers.current = [];
  };

  useEffect(() => {
    const paper = paperRef.current;
    if (!paper) return;
    const m = measureRows(paper);
    height.current = m.height;
    setPaperH(m.height);
    if (reduce) {
      reveal.set(m.height + 80);
      send("skip");
      return;
    }
    reveal.set(0);
    const plan = planPrint(m.height, m.rowBottoms, m.totalBottom);
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
                vibrate(18);
                send("done");
              }, STAMP_DELAY),
            );
          },
        });
      }, START_DELAY),
    );
    return clear;
  }, [paperRef, reveal, reduce]);

  const skip = () => {
    clear();
    reveal.set(height.current + 80);
    setEdge(false);
    setInstant(true);
    setTickerRun(true);
    setStamp("shown");
    send("skip");
  };

  return { phase, send, stamp, tickerRun, instant, edge, paperH, skip };
}
