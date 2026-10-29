import { animate, motion, useMotionValue, useReducedMotion, useTransform } from "framer-motion";
import { ChevronsDown, Scissors } from "lucide-react";
import { useCallback, useEffect, useRef, useState } from "react";
import { EASE_OUT } from "@/lib/motion";
import { cn } from "@/lib/utils";
import { ReceiptPaper } from "./ReceiptPaper";
import { haptic } from "./haptics";
import { nextPhase, type Phase } from "./physics";
import { unlockAudio } from "./sound";
import { usePrintSequence } from "./usePrintSequence";
import { useTear } from "./useTear";
import { PAPER_W, STUB_OVERLAP, TOOTH_DEPTH, paperClipPath, stubClipPath } from "./zigzag";
import { type ReceiptDoc } from "./receiptModel";
import { useReceiptStrings } from "./strings";

const SLOT_W = PAPER_W + 40;
/** Height of the printer head; the paper emerges from under its bottom edge. */
const HEAD_H = 112;
/** A torn sheet comes to rest this far below the slot: it is off the roll, in your hand. */
const REST_Y = 46;

/**
 * The print ritual, inline in a desktop card (docs/DESIGN.md §7, same pacing as the phone's ReceiptStage):
 * the head sits at the top, the paper feeds out of its slot line by line with a 2px gear shake per row, the
 * total rolls up, the stamp slams, and then the sheet can be torn off — pulled down with the pointer or with
 * the button. Everything is transform/clip-path; `prefers-reduced-motion` renders the finished sheet and the
 * buttons act instantly. Printing again mid-run interrupts and starts over from the slot.
 */
export function ReceiptPrinterStage({ doc, stamped = true, className }: { doc: ReceiptDoc; /** whether the paper carries its stamp at all (the page's 印章 switch) */ stamped?: boolean; className?: string }) {
  const t = useReceiptStrings();
  const reduce = useReducedMotion() ?? false;
  const paperRef = useRef<HTMLDivElement>(null);
  const reveal = useMotionValue(0);
  const x = useMotionValue(0);
  const y = useMotionValue(0);
  const rot = useMotionValue(0);
  const opacity = useMotionValue(1);
  const stretch = useMotionValue(1);
  /** 2px vertical nudge per printer tick: the sheet shakes like it is being pulled through the gears. */
  const shake = useMotionValue(0);

  const kick = useCallback(() => {
    if (reduce) return;
    shake.stop();
    shake.set(2);
    void animate(shake, 0, { duration: 0.06, ease: EASE_OUT });
  }, [reduce, shake]);

  const seq = usePrintSequence({ paperRef, reveal, reduce, onTick: kick, auto: false });

  const tear = useTear(
    { x, y, rot, opacity, stretch },
    {
      enabled: seq.phase === "printed",
      reduce,
      restY: REST_Y,
      onTear: () => seq.send("tear"),
      onTorn: () => seq.send("torn"),
    },
  );

  // A fresh sheet: rewind the tear so the next print starts from the slot.
  const print = () => {
    unlockAudio();
    seq.send("close");
    x.set(0);
    rot.set(0);
    opacity.set(1);
    stretch.set(1);
    if (reduce) y.set(0);
    else void animate(y, 0, { type: "spring", bounce: 0, duration: 0.3 });
    seq.start();
  };

  const phase = seq.phase;

  useEffect(() => {
    if (phase === "printed" && !reduce) {
      // the finished sheet settles in the slot: a small swing that rings down
      rot.set(0.6);
      void animate(rot, 0, { type: "spring", stiffness: 260, damping: 5, mass: 0.6 });
    }
    if (phase === "torn") haptic("success");
  }, [phase, reduce, rot]);

  const height = seq.paperH;
  const revealClip = useTransform(reveal, (v) => (height ? `inset(0px 0px ${Math.max(0, height - v)}px 0px)` : "inset(0px 0px 100% 0px)"));
  const developY = useTransform(reveal, (v) => Math.max(0, v) - 56);
  const edgeY = useTransform(reveal, (v) => Math.max(0, v) - 1);
  const edgeVisible = seq.edge && phase !== "printed" && phase !== "torn";

  const busy = phase === "printing";
  const printed = phase === "printed";
  const torn = phase === "torn" || phase === "tearing";

  return (
    <div className={cn("relative flex flex-col items-center", className)}>
      {/* The ritual's controls sit above the head, never below a 600px sheet: always in reach. */}
      <div className="mb-4 flex h-9 items-center gap-3">
        {busy ? (
          <ActionButton icon={ChevronsDown} onClick={seq.skip}>
            {t("stage.skip")}
          </ActionButton>
        ) : printed ? (
          <ActionButton icon={Scissors} onClick={() => tear.tear(700)}>
            {t("stage.tear")}
          </ActionButton>
        ) : torn ? (
          <ActionButton onClick={print}>{t("stage.again")}</ActionButton>
        ) : (
          <ActionButton onClick={print}>{t("print")}</ActionButton>
        )}
        <p className="min-w-0 text-[11.5px] text-d-ink-3" aria-live="polite">
          {busy ? t("stage.printing") : printed ? t("stage.ready") : torn ? t("stage.torn") : t("stage.idle")}
        </p>
      </div>

      <div className="relative" style={{ width: SLOT_W }}>
        {/* printer head */}
        <div
          aria-hidden
          className="relative z-20 h-[112px] rounded-[14px] border border-d-hairline bg-d-surface shadow-[inset_0_1px_0_oklch(1_0_0/70%),0_1px_2px_oklch(0_0_0/6%)]"
          style={{ height: HEAD_H }}
        >
          <div className="absolute inset-x-4 top-3 h-1.5 rounded-full bg-d-fill" />
          <div className="absolute inset-x-4 top-6 flex items-center justify-between">
            <span className="h-2 w-10 rounded-full bg-d-fill" />
            <span className="size-1.5 rounded-full bg-d-accent" />
          </div>
          <div className="absolute inset-x-6 top-1/2 flex flex-col items-center gap-1">
            <span className="h-1 w-16 rounded-full bg-d-hairline" />
            <span className="h-0.5 w-9 rounded-full bg-d-hairline/70" />
          </div>
          {/* the slot the paper comes out of */}
          <div className="absolute -bottom-[7px] left-1/2 h-[14px] w-[calc(100%-28px)] -translate-x-1/2 rounded-full bg-d-ink/85 shadow-[inset_0_2px_3px_oklch(0_0_0/60%)]">
            <div className="absolute inset-x-1 top-1/2 h-[3px] -translate-y-1/2 rounded-full bg-black" />
          </div>
        </div>

        {/* paper well: the sheet slides out from under the slot and the page grows as it feeds */}
        <div className="relative z-10 mx-auto" style={{ width: PAPER_W, perspective: 800 }}>
          <div aria-hidden className="absolute left-0 bg-paper" style={{ top: -STUB_OVERLAP - 4, height: STUB_OVERLAP + TOOTH_DEPTH + 4, width: PAPER_W, clipPath: stubClipPath() }} />
          <motion.div
            {...tear.handlers}
            className="relative select-none"
            style={{ x, y, rotate: rot, opacity, scaleY: stretch, transformOrigin: "50% 0", width: PAPER_W, touchAction: "none", cursor: printed ? (tear.dragging ? "grabbing" : "grab") : "default" }}
            aria-label={printed ? t("stage.drag") : undefined}
          >
            <motion.div aria-hidden className="pointer-events-none absolute inset-x-0 top-0 h-6 bg-gradient-to-b from-black/25 to-transparent" style={{ clipPath: revealClip }} />
            <motion.div className="relative" style={{ clipPath: revealClip }}>
              <motion.div style={{ y: shake }}>
                <ReceiptPaper ref={paperRef} doc={doc} stamp={stamped ? seq.stamp : "hidden"} tickerRun={seq.tickerRun} instant={seq.instant} />
              </motion.div>
              {edgeVisible && (
                <motion.div aria-hidden className="pointer-events-none absolute inset-x-0 top-0 h-14 bg-gradient-to-t from-paper/75 to-transparent" style={{ y: developY }} />
              )}
            </motion.div>
            {edgeVisible && <motion.div aria-hidden className="absolute inset-x-0 h-0.5 bg-paper-ink/25" style={{ y: edgeY }} />}
            {torn && (
              <div
                aria-hidden
                className="pointer-events-none absolute inset-x-0 top-0 h-1.5 bg-d-canvas/0"
                style={{ clipPath: paperClipPath(PAPER_W, { top: true, bottom: false }) }}
              />
            )}
          </motion.div>
        </div>
      </div>
    </div>
  );
}

function ActionButton({ icon: Icon, onClick, children }: { icon?: typeof Scissors; onClick: () => void; children: React.ReactNode }) {
  return (
    <button
      type="button"
      onClick={onClick}
      className="d-press inline-flex h-9 items-center gap-1.5 rounded-full bg-d-ink px-4 text-[12.5px] font-medium text-d-canvas hover:opacity-90"
    >
      {Icon && <Icon className="size-4" strokeWidth={1.9} aria-hidden />}
      {children}
    </button>
  );
}

export type { Phase };
export { nextPhase };
