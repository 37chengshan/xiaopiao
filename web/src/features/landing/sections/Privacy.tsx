import {
  motion,
  useInView,
  useMotionValueEvent,
  useReducedMotion,
  useScroll,
  useTransform,
  type MotionValue,
} from "framer-motion";
import { useEffect, useRef } from "react";
import { useLocale } from "@/lib/strings";
import { Shield } from "../illustrations";
import { landingStrings } from "../strings";

const PROMISES = ["c1", "c2", "c3"] as const;

/**
 * L5 — the inverted band. `--inv` goes 0 → 1 over the first third of the section's scroll range and
 * back to 0 on the way out, driving background, text and hairlines through `color-mix` in
 * landing.css (one style write per frame, no color interpolation in JS).
 */
export function Privacy() {
  useLocale();
  const s = landingStrings;
  const reduceMotion = useReducedMotion();
  const ref = useRef<HTMLElement>(null);
  const { scrollYProgress } = useScroll({ target: ref, offset: ["start end", "end start"] });
  const inv = useTransform(scrollYProgress, [0.08, 0.3, 0.68, 0.84], [0, 1, 1, 0]);
  const inView = useInView(ref, { amount: 0.25 });

  useMotionValueEvent(inv, "change", (value) => {
    ref.current?.style.setProperty("--inv", value.toFixed(3));
  });

  useEffect(() => {
    const node = ref.current;
    if (!node) return;
    node.style.setProperty("--inv", reduceMotion ? (inView ? "1" : "0") : inv.get().toFixed(3));
  }, [inView, inv, reduceMotion]);

  return (
    <section
      id="privacy"
      ref={ref}
      className="landing-inv landing-inv-bg relative scroll-mt-[72px] py-24 lg:py-32"
    >
      <div className="mx-auto grid max-w-[1200px] grid-cols-1 gap-14 px-5 lg:grid-cols-[1.15fr_0.85fr] lg:items-center lg:gap-10 lg:px-10">
        <div>
          <h2 className="landing-inv-text max-w-[620px] text-[clamp(1.9rem,3.2vw,2.9rem)] leading-[1.12] font-semibold tracking-[-0.02em] text-balance">
            {s("priv.title")}
          </h2>
          <p className="landing-inv-muted mt-4 max-w-[480px] text-[16px] leading-relaxed">
            {s("priv.sub")}
          </p>

          <div className="mt-12 grid grid-cols-1 gap-3 sm:grid-cols-3">
            {PROMISES.map((key, index) => (
              <PromiseCard
                key={key}
                progress={inv}
                index={index}
                still={Boolean(reduceMotion)}
                title={s(`priv.${key}.title` as Parameters<typeof s>[0])}
                body={s(`priv.${key}.body` as Parameters<typeof s>[0])}
              />
            ))}
          </div>
        </div>

        {/* The shield keeps a fixed dark ink on cream in both states, like a printed sticker:
            inverting its strokes would put light lines on a light paper fill. */}
        <div className="order-first w-full max-w-[240px] justify-self-center text-paper-ink lg:order-last lg:max-w-[380px]">
          <Shield className="h-auto w-full" />
        </div>
      </div>
    </section>
  );
}

function PromiseCard({
  progress,
  index,
  still,
  title,
  body,
}: {
  progress: MotionValue<number>;
  index: number;
  still: boolean;
  title: string;
  body: string;
}) {
  const start = 0.14 + index * 0.08;
  const y = useTransform(progress, [start, start + 0.18], [40, 0]);
  const opacity = useTransform(progress, [start, start + 0.12], [0, 1]);

  return (
    <motion.div
      style={still ? undefined : { y, opacity }}
      className="landing-inv-card rounded-2xl border p-5"
    >
      <span className="landing-inv-muted font-mono text-[12px] tabular-nums">0{index + 1}</span>
      <h3 className="landing-inv-text mt-2 text-[16px] font-semibold tracking-[-0.01em]">
        {title}
      </h3>
      <p className="landing-inv-muted mt-2 text-[13px] leading-relaxed">{body}</p>
    </motion.div>
  );
}
