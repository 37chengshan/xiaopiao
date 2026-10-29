import { motion, useMotionValue, useReducedMotion, useScroll, useTransform } from "framer-motion";
import { useRef } from "react";
import { useLocale } from "@/lib/strings";
import { EASE_OUT } from "@/lib/motion";
import { cn } from "@/lib/utils";
import { ReceiptSheet, type ReceiptLine } from "../components/ReceiptSheet";
import { useDesktop } from "../hooks/useMediaQuery";
import { landingStrings } from "../strings";

const AMOUNTS = [850, 400, 2200, 3200, 6800, 1980, 2800, 1500];
const STEPS = ["step1", "step2", "step3"] as const;

/**
 * L2 — "一天的账". The sheet is pinned for 300vh and fills line by line; the three captions cross
 * over at the 1/3 and 2/3 marks. Narrow viewports and reduced motion get the same content as a
 * static column (no pin, no scrub), per §L.1 / §L.2.
 */
export function DayLedger() {
  useLocale();
  const s = landingStrings;
  const reduceMotion = useReducedMotion();
  const desktop = useDesktop();
  const still = Boolean(reduceMotion) || !desktop;

  const ref = useRef<HTMLElement>(null);
  const frozen = useMotionValue(1);
  const { scrollYProgress } = useScroll({ target: ref, offset: ["start start", "end end"] });
  const p = still ? frozen : scrollYProgress;

  const barScale = useTransform(p, (v) => Math.max(0.001, v));
  // Captions swap at 1/3 and 2/3; declared unconditionally so `still` can flip on resize.
  const step1Opacity = useTransform(p, [0, 0.26, 0.32], [1, 1, 0]);
  const step1Y = useTransform(p, [0.26, 0.32], [0, -16]);
  const step2Opacity = useTransform(p, [0.32, 0.38, 0.6, 0.66], [0, 1, 1, 0]);
  const step2Y = useTransform(p, [0.32, 0.38, 0.6, 0.66], [16, 0, 0, -16]);
  const step3Opacity = useTransform(p, [0.66, 0.72], [0, 1]);
  const step3Y = useTransform(p, [0.66, 0.72], [16, 0]);

  const lines: ReceiptLine[] = AMOUNTS.map((cents, index) => ({
    id: `day-${index}`,
    label: s(`day.row${index + 1}` as Parameters<typeof s>[0]),
    cents,
  }));
  const total = AMOUNTS.reduce((sum, cents) => sum + cents, 0);

  const now = new Date();
  const meta = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, "0")}-${String(
    now.getDate(),
  ).padStart(2, "0")} · 21:40`;

  return (
    <section
      id="day"
      ref={ref}
      className={cn("relative bg-d-canvas text-d-ink", !still && "h-[300vh]")}
    >
      <div className={cn(!still && "sticky top-0 flex h-svh items-center overflow-hidden")}>
        <motion.div
          aria-hidden="true"
          style={{ scaleY: still ? 1 : barScale }}
          className="absolute top-0 left-0 hidden h-full w-[2px] origin-top bg-d-ink/25 lg:block"
        />

        <div className="mx-auto grid w-full max-w-[1200px] grid-cols-1 items-center gap-12 px-5 py-24 lg:grid-cols-[minmax(0,420px)_1fr] lg:gap-20 lg:px-10 lg:py-0">
          <div className="mx-auto w-full max-w-[452px] text-[16px]">
            <ReceiptSheet
              progress={p}
              lines={lines}
              title={s("day.sheet.title")}
              meta={meta}
              totalLabel={s("day.sheet.total")}
              totalCents={total}
              stamp={s("day.sheet.stamp")}
              teeth={34}
              toothDepth={9}
              className="rounded-b-[2px]"
            />
          </div>

          <div className="max-w-[520px]">
            <motion.p
              initial={{ opacity: 0 }}
              whileInView={{ opacity: 1 }}
              viewport={{ once: true, amount: 0.6 }}
              transition={{ duration: still ? 0.2 : 0.4 }}
              className="text-[12px] font-semibold tracking-[0.16em] text-d-ink-3 uppercase"
            >
              {s("day.eyebrow")}
            </motion.p>
            <motion.h2
              initial={{ opacity: 0, y: still ? 0 : 16 }}
              whileInView={{ opacity: 1, y: 0 }}
              viewport={{ once: true, amount: 0.6 }}
              transition={{ duration: still ? 0.2 : 0.6, ease: EASE_OUT }}
              className="mt-3 text-[clamp(1.75rem,2.6vw,2.5rem)] leading-[1.15] font-semibold tracking-[-0.02em] text-balance"
            >
              {s("day.title")}
            </motion.h2>

            {still ? (
              <div className="mt-8 flex flex-col gap-6">
                {STEPS.map((step) => (
                  <motion.div
                    key={step}
                    initial={{ opacity: 0, y: 8 }}
                    whileInView={{ opacity: 1, y: 0 }}
                    viewport={{ once: true, amount: 0.5 }}
                    transition={{ duration: 0.2 }}
                  >
                    <StepCopy step={step} />
                  </motion.div>
                ))}
              </div>
            ) : (
              <div className="relative mt-8 h-[210px]">
                <motion.div
                  style={{ opacity: step1Opacity, y: step1Y }}
                  className="absolute inset-x-0 top-0"
                >
                  <StepCopy step="step1" />
                </motion.div>
                <motion.div
                  style={{ opacity: step2Opacity, y: step2Y }}
                  className="absolute inset-x-0 top-0"
                >
                  <StepCopy step="step2" />
                </motion.div>
                <motion.div
                  style={{ opacity: step3Opacity, y: step3Y }}
                  className="absolute inset-x-0 top-0"
                >
                  <StepCopy step="step3" />
                </motion.div>
              </div>
            )}
          </div>
        </div>
      </div>
    </section>
  );
}

function StepCopy({ step }: { step: (typeof STEPS)[number] }) {
  const s = landingStrings;
  const index = STEPS.indexOf(step) + 1;
  return (
    <div className="flex gap-4">
      <span className="mt-1 font-mono text-[13px] text-d-ink-3 tabular-nums">0{index}</span>
      <div>
        <h3 className="text-[19px] font-semibold tracking-[-0.01em]">
          {s(`day.${step}.title` as Parameters<typeof s>[0])}
        </h3>
        <p className="mt-2 text-[15px] leading-relaxed text-d-ink-2">
          {s(`day.${step}.body` as Parameters<typeof s>[0])}
        </p>
      </div>
    </div>
  );
}
