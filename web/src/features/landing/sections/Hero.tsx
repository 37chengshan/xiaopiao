import {
  animate,
  motion,
  useMotionValue,
  useReducedMotion,
  useScroll,
  useTransform,
} from "framer-motion";
import { useEffect, useRef } from "react";
import { cn } from "@/lib/utils";
import { useLocale } from "@/lib/strings";
import { EASE_OUT } from "@/lib/motion";
import { Pill } from "../components/Pill";
import { ReceiptSheet, type ReceiptLine } from "../components/ReceiptSheet";
import { Printer } from "../illustrations";
import { landingStrings } from "../strings";

/**
 * L1 — hero. Headline lines rise out of a blur, the printer prints to 40% on load, then scrolling
 * finishes the sheet and floats it away while the headline scales down and fades (§L.2 L1).
 */
export function Hero() {
  useLocale();
  const s = landingStrings;
  const reduceMotion = useReducedMotion();
  const ref = useRef<HTMLElement>(null);
  const frozen = useMotionValue(0);

  const { scrollYProgress } = useScroll({ target: ref, offset: ["start start", "end end"] });
  const p = reduceMotion ? frozen : scrollYProgress;

  const titleScale = useTransform(p, [0, 1], [1, 0.92]);
  const titleOpacity = useTransform(p, [0, 0.85], [1, 0]);
  const titleBlur = useTransform(p, [0, 1], ["blur(0px)", "blur(6px)"]);
  const hintOpacity = useTransform(p, [0, 0.18], [1, 0]);

  const loaded = useMotionValue(reduceMotion ? 1 : 0);
  useEffect(() => {
    if (reduceMotion) {
      loaded.set(1);
      return;
    }
    const controls = animate(loaded, 0.4, { delay: 0.6, duration: 1.2, ease: EASE_OUT });
    return () => controls.stop();
  }, [loaded, reduceMotion]);

  const scrolled = useTransform(p, [0, 0.8], [0, 0.6]);
  const printed = useTransform([loaded, scrolled], ([a, b]: number[]) => Math.min(1, a + b));
  const paperY = useTransform(p, [0.72, 1], [0, -760]);
  const paperRotate = useTransform(p, [0.72, 1], [0, -3]);
  const paperOpacity = useTransform(p, [0.8, 0.98], [1, 0]);

  const lines: ReceiptLine[] = [
    { id: "hero-1", label: s("day.row3"), cents: 2200 },
    { id: "hero-2", label: s("day.row4"), cents: 3200 },
    { id: "hero-3", label: s("day.row5"), cents: 6800 },
  ];
  const total = lines.reduce((sum, line) => sum + line.cents, 0);

  return (
    <section
      ref={ref}
      id="top"
      className={cn("relative bg-d-canvas text-d-ink", !reduceMotion && "h-[200vh]")}
    >
      <div
        className={cn(
          "relative mx-auto flex w-full max-w-[1320px] flex-col justify-center px-5 pt-24 pb-16",
          !reduceMotion && "sticky top-0 h-svh overflow-hidden",
        )}
      >
        <div className="grid grid-cols-1 items-center gap-8 lg:grid-cols-[1.04fr_0.96fr] lg:gap-4">
          <motion.div
            style={{ scale: titleScale, opacity: titleOpacity, filter: titleBlur }}
            className="max-w-[600px] origin-left"
          >
            <motion.h1 className="text-[clamp(2.55rem,5.2vw,5rem)] leading-[1.04] font-semibold tracking-[-0.02em]">
              {[s("hero.line1"), s("hero.line2")].map((line, index) => (
                <motion.span
                  key={line}
                  className="block"
                  initial={
                    reduceMotion ? { opacity: 0 } : { opacity: 0, y: 24, filter: "blur(8px)" }
                  }
                  animate={
                    reduceMotion ? { opacity: 1 } : { opacity: 1, y: 0, filter: "blur(0px)" }
                  }
                  transition={{
                    duration: reduceMotion ? 0.2 : 0.72,
                    delay: index * 0.08,
                    ease: EASE_OUT,
                  }}
                >
                  {line}
                </motion.span>
              ))}
            </motion.h1>

            <motion.p
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              transition={{ duration: 0.5, delay: 0.24, ease: EASE_OUT }}
              className="mt-6 max-w-[470px] text-[17px] leading-relaxed text-balance text-d-ink-2"
            >
              {s("hero.sub")}
            </motion.p>

            <motion.div
              initial={{ opacity: 0, y: reduceMotion ? 0 : 12 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.5, delay: 0.32, ease: EASE_OUT }}
              className="mt-9 flex flex-wrap items-center gap-3"
            >
              <Pill href="#/app" cursorLabel={s("hero.cursor")}>
                {s("hero.cta")}
              </Pill>
              <Pill href="#download" variant="grey">
                {s("hero.secondary")}
              </Pill>
            </motion.div>
          </motion.div>

          <div className="relative mx-auto w-full max-w-[600px]">
            <div className="relative aspect-[600/520] w-full">
              <Printer className="h-full w-full text-d-ink" label={s("hero.printer")} />
              <div className="receipt-hero absolute top-[58.1%] left-[26.67%] w-[46.67%]">
                <motion.div style={{ y: paperY, opacity: paperOpacity, rotate: paperRotate }}>
                  <ReceiptSheet
                    progress={printed}
                    lines={lines}
                    title={s("hero.sheet.title")}
                    meta={s("hero.sheet.meta")}
                    totalLabel={s("hero.sheet.total")}
                    totalCents={total}
                    teeth={18}
                    className="rounded-b-[2px]"
                  />
                </motion.div>
              </div>
            </div>
          </div>
        </div>

        <motion.div
          style={{ opacity: hintOpacity }}
          className="pointer-events-none absolute bottom-7 left-1/2 hidden -translate-x-1/2 items-center gap-2 text-[11px] font-medium tracking-[0.18em] text-d-ink-3 uppercase lg:flex"
        >
          <span>{s("hero.hint")}</span>
          <span className="block h-8 w-px bg-d-ink-3" />
        </motion.div>
      </div>
    </section>
  );
}
