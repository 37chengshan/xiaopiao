import { AnimatePresence, motion, useMotionValue, useReducedMotion, useTransform } from "framer-motion";
import { ChevronsDown, X } from "lucide-react";
import { useEffect, useLayoutEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { toast } from "sonner";
import type { DailyReceipt } from "../../domain/types";
import { ReceiptPaper } from "./ReceiptPaper";
import { ShareSheet } from "./ShareSheet";
import { PAPER_W, STUB_OVERLAP, stubClipPath, TOOTH_DEPTH } from "./zigzag";
import { haptic } from "./haptics";
import { unlockAudio } from "./sound";
import { usePrintSequence } from "./usePrintSequence";
import { usePushBack } from "./usePushBack";
import { useReceiptDoc } from "./useReceiptDoc";
import { useTear } from "./useTear";

const SLOT_TOP = 10;
const SLOT_H = 22;
const PAPER_TOP = SLOT_TOP + SLOT_H + STUB_OVERLAP - 8;
const RESERVED_BOTTOM = 132;

const safeTop = (px: number) => `calc(env(safe-area-inset-top, 0px) + ${px}px)`;

/**
 * The print moment (docs/DESIGN.md §7): a layer over the phone column. The page behind steps back, paper feeds down
 * from the slot, a bottom capsule says what to do, dragging the paper down tears it off and opens the share sheet.
 */
export function ReceiptStage({ receipt, column, onClose }: { receipt: DailyReceipt; column: HTMLElement; onClose: () => void }) {
  const reduce = useReducedMotion() ?? false;
  const doc = useReceiptDoc(receipt);
  const paperRef = useRef<HTMLDivElement>(null);
  const rootRef = useRef<HTMLDivElement>(null);
  const reveal = useMotionValue(0);
  const x = useMotionValue(0);
  const y = useMotionValue(0);
  const rot = useMotionValue(0);
  const opacity = useMotionValue(1);
  const [avail, setAvail] = useState(0);

  const seq = usePrintSequence({ paperRef, reveal, reduce });
  const { phase, send } = seq;
  const tear = useTear(
    { x, y, rot, opacity },
    { enabled: phase === "printed", reduce, onTear: () => send("tear"), onTorn: () => send("torn") },
  );
  usePushBack(column, true, reduce);

  useEffect(() => {
    if (phase === "torn") haptic("success");
  }, [phase]);

  const close = () => {
    if (phase === "torn") toast("小票已撕下，明天见 ✂️");
    send("close");
    onClose();
  };

  useLayoutEffect(() => {
    unlockAudio();
    setAvail(rootRef.current?.clientHeight ?? 0);
    rootRef.current?.focus({ preventScroll: true });
  }, []);

  useEffect(() => {
    if (phase === "torn") return;
    const onKey = (e: KeyboardEvent) => e.key === "Escape" && close();
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  });

  const fit = seq.paperH && avail ? Math.max(0.5, Math.min(1, (avail - PAPER_TOP - RESERVED_BOTTOM) / seq.paperH)) : 1;
  const clipHeight = useTransform(reveal, (v) => Math.max(0, v));
  const edgeY = useTransform(reveal, (v) => Math.max(0, v) - 1);
  const busy = phase === "idle" || phase === "printing";
  const ready = phase === "printed";

  return createPortal(
    <div ref={rootRef} tabIndex={-1} role="dialog" aria-modal="true" aria-label="今日小票" className="absolute inset-0 z-40 overflow-hidden outline-none">
      <motion.div
        aria-hidden
        className="absolute inset-0 bg-scrim"
        initial={reduce ? false : { opacity: 0 }}
        animate={{ opacity: 1 }}
        transition={{ duration: 0.22, ease: [0.23, 1, 0.32, 1] }}
        onClick={ready ? close : undefined}
      />

      {/* paper */}
      <div className="pointer-events-none absolute inset-x-0 flex justify-center" style={{ top: safeTop(PAPER_TOP) }}>
        <div style={{ transform: `scale(${fit})`, transformOrigin: "top center" }}>
          <motion.div
            {...tear.handlers}
            className="pointer-events-auto relative select-none [filter:drop-shadow(0_10px_18px_rgb(0_0_0/0.28))]"
            style={{ x, y, rotate: rot, opacity, width: PAPER_W, touchAction: "none", cursor: ready ? (tear.dragging ? "grabbing" : "grab") : "default" }}
            aria-label={ready ? "向下拖动撕下小票" : undefined}
          >
            <div aria-hidden className="absolute left-0 bg-paper" style={{ top: -STUB_OVERLAP, width: PAPER_W, height: STUB_OVERLAP + TOOTH_DEPTH, clipPath: stubClipPath() }} />
            <motion.div className="relative overflow-hidden" style={{ height: clipHeight }}>
              <ReceiptPaper ref={paperRef} doc={doc} stamp={seq.stamp} tickerRun={seq.tickerRun} instant={seq.instant} />
            </motion.div>
            {seq.edge && <motion.div aria-hidden className="absolute inset-x-0 h-0.5 bg-paper-ink/25" style={{ y: edgeY }} />}
          </motion.div>
        </div>
      </div>

      {/* printer slot */}
      <div
        aria-hidden
        className="pointer-events-none absolute left-1/2 -translate-x-1/2 rounded-full bg-black/90 ring-1 ring-white/15"
        style={{ top: safeTop(SLOT_TOP), width: PAPER_W + 36, height: SLOT_H }}
      >
        <div className="absolute inset-x-3 top-1/2 h-1.5 -translate-y-1/2 rounded-full bg-black shadow-[inset_0_1px_2px_rgb(0_0_0/0.9)]" />
      </div>

      {/* status capsule */}
      <motion.div
        className="material absolute inset-x-0 mx-auto flex h-14 w-[min(358px,calc(100%-32px))] items-center gap-1 rounded-full pl-5 pr-1.5 shadow-[0_8px_30px_rgb(0_0_0/0.18),inset_0_0_0_1px_var(--hairline)]"
        style={{ bottom: "max(24px, env(safe-area-inset-bottom, 0px))" }}
        initial={reduce ? false : { opacity: 0, y: 28 }}
        animate={phase === "tearing" || phase === "torn" ? { opacity: 0, y: 12, pointerEvents: "none" } : { opacity: 1, y: 0 }}
        transition={{ type: "spring", bounce: 0, duration: 0.4 }}
      >
        <AnimatePresence mode="wait" initial={false}>
          <motion.p
            key={busy ? "busy" : tear.armed ? "armed" : "ready"}
            className="flex min-w-0 flex-1 items-center gap-2 text-[15px] text-ink"
            initial={reduce ? false : { opacity: 0, y: 6 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, transition: { duration: 0.08 } }}
            transition={{ duration: 0.18, ease: [0.23, 1, 0.32, 1] }}
            aria-live="polite"
          >
            {!busy && <ChevronsDown className="size-4 shrink-0 text-ink-2" strokeWidth={1.75} aria-hidden />}
            <span className="truncate">{busy ? "正在打印小票…" : tear.armed ? "松手撕下" : "向下拖动撕下小票"}</span>
          </motion.p>
        </AnimatePresence>
        <button
          type="button"
          onClick={busy ? seq.skip : () => tear.tear(700)}
          className="h-11 shrink-0 rounded-full px-3.5 text-[15px] font-medium text-clay transition-opacity duration-100 active:opacity-50"
        >
          {busy ? "跳过" : "撕下"}
        </button>
        <button type="button" onClick={close} aria-label="关闭" className="grid size-11 shrink-0 place-items-center rounded-full text-ink-2 transition-colors duration-100 active:bg-fill">
          <X className="size-[18px]" strokeWidth={2} aria-hidden />
        </button>
      </motion.div>

      <ShareSheet receipt={receipt} open={phase === "torn"} onClose={close} />
    </div>,
    column,
  );
}
