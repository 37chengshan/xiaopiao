import { motion, useMotionValueEvent, useReducedMotion, useScroll, useTransform, type MotionValue } from "framer-motion";
import { useRef, useState } from "react";
import { useLocale } from "@/lib/strings";
import { s } from "../strings";

/**
 * The launch section: one CSS-3D phone, pinned for three viewports. The phone turns from tilted to square
 * to slightly past square while six real app screens slide through it and the copy steps beside it.
 * Everything is opacity/transform driven by a single scroll progress.
 */
const STEPS = [
  { shot: "/img/app/ledger.webp", title: "launch.1.title", line: "launch.1.line", num: "launch.1.num", numLabel: "launch.1.numLabel" },
  { shot: "/img/app/receipt.webp", title: "launch.2.title", line: "launch.2.line", num: "launch.2.num", numLabel: "launch.2.numLabel" },
  { shot: "/img/app/stats.webp", title: "launch.3.title", line: "launch.3.line", num: "launch.3.num", numLabel: "launch.3.numLabel" },
  { shot: "/img/app/record.webp", title: "launch.4.title", line: "launch.4.line", num: "launch.4.num", numLabel: "launch.4.numLabel" },
  { shot: "/img/app/circle.webp", shotZh: "/img/app/circle.zh.webp", title: "launch.5.title", line: "launch.5.line", num: "launch.5.num", numLabel: "launch.5.numLabel" },
  { shot: "/img/app/games.webp", shotZh: "/img/app/games.zh.webp", title: "launch.6.title", line: "launch.6.line", num: "launch.6.num", numLabel: "launch.6.numLabel" },
] as const;

const N = STEPS.length;

/**
 * One step's window. Steps stack instead of cross-fading: the incoming block or screen rides up from just
 * below and covers the one before it, so two app screens are never double-exposed at once.
 */
function stepWindow(i: number) {
  const start = i / N;
  return { start, end: (i + 1) / N };
}

function StepCopy({ i, progress, reduce }: { i: number; progress: MotionValue<number>; reduce: boolean }) {
  const { start } = stepWindow(i);
  const from = Math.max(0, start - 0.05);
  const y = useTransform(progress, from === start ? [0, 1] : [from, start], from === start ? ["0%", "0%"] : ["104%", "0%"]);
  const step = STEPS[i];

  return (
    <motion.div className="lx-launch__step" style={reduce ? { opacity: i === 0 ? 1 : 0 } : { y }}>
      <h3 className="lx-launch__title">{s(step.title)}</h3>
      <p className="lx-launch__line">{s(step.line)}</p>
      <p className="lx-launch__num">
        <span className="lx-launch__digits">{s(step.num)}</span>
        <span className="lx-launch__numlabel">{s(step.numLabel)}</span>
      </p>
    </motion.div>
  );
}

function Screen({ i, progress, reduce }: { i: number; progress: MotionValue<number>; reduce: boolean }) {
  const locale = useLocale();
  const step = STEPS[i];
  const { start } = stepWindow(i);
  const from = Math.max(0, start - 0.045);
  const y = useTransform(progress, from === start ? [0, 1] : [from, start], from === start ? ["0%", "0%"] : ["101%", "0%"]);

  return (
    <motion.img
      src={locale === "zh" && "shotZh" in step && step.shotZh ? step.shotZh : step.shot}
      alt=""
      className="lx-phone__shot"
      style={reduce ? { opacity: i === 0 ? 1 : 0 } : { y }}
      decoding="async"
      loading={i < 2 ? "eager" : "lazy"}
    />
  );
}

export function PhoneLaunch() {
  const track = useRef<HTMLElement>(null);
  const reduce = useReducedMotion() ?? false;
  const { scrollYProgress } = useScroll({ target: track, offset: ["start start", "end end"] });
  const [stepIndex, setStepIndex] = useState(0);
  useMotionValueEvent(scrollYProgress, "change", (v) => {
    const next = Math.min(N - 1, Math.max(0, Math.floor(v * N + 0.0001)));
    setStepIndex((prev) => (prev === next ? prev : next));
  });

  const rotateY = useTransform(scrollYProgress, [0, 0.34, 1], [-26, 0, 12]);
  const rotateX = useTransform(scrollYProgress, [0, 0.34, 1], [9, 0, -2]);
  const rotateZ = useTransform(scrollYProgress, [0, 0.34, 1], [-4, 0, 1.5]);
  const lift = useTransform(scrollYProgress, [0, 0.34, 1], [24, 0, -12]);
  const glow = useTransform(scrollYProgress, [0, 0.34, 1], [0.35, 0.6, 0.4]);

  const jump = (i: number) => {
    const el = track.current;
    if (!el) return;
    const total = el.offsetHeight - window.innerHeight;
    const top = window.scrollY + el.getBoundingClientRect().top;
    window.scrollTo({ top: top + total * (i / N) + 8, behavior: "smooth" });
  };

  return (
    <section id="launch" className="lx-launch" ref={track} aria-label={s("launch.kicker")}>
      <div className="lx-launch__stage">
        <div className="lx-launch__grid">
          <div className="lx-launch__copy">
            <p className="lx-eyebrow">{s("launch.kicker")}</p>
            <div className="lx-launch__steps">
              {STEPS.map((step, i) => (
                <StepCopy key={step.title} i={i} progress={scrollYProgress} reduce={reduce} />
              ))}
            </div>
            <div className="lx-launch__dots">
              {STEPS.map((step, i) => (
                <button
                  key={step.title}
                  type="button"
                  aria-label={s(step.title)}
                  className={`lx-dot${i === stepIndex ? " is-on" : ""}`}
                  onClick={() => jump(i)}
                />
              ))}
            </div>
          </div>

          <div className="lx-launch__phonewrap" aria-label={s("launch.aria")} role="img">
            <motion.div className="lx-phone__glow" style={{ opacity: reduce ? 0.5 : glow }} aria-hidden="true" />
            <motion.div
              className="lx-phone"
              style={
                reduce
                  ? undefined
                  : {
                      rotateY,
                      rotateX,
                      rotateZ,
                      y: lift,
                      transformPerspective: 1800,
                    }
              }
            >
              <div className="lx-phone__body">
                <span className="lx-phone__island" aria-hidden="true" />
                <div className="lx-phone__screen">
                  {STEPS.map((step, i) => (
                    <Screen key={step.title} i={i} progress={scrollYProgress} reduce={reduce} />
                  ))}
                  <span className="lx-phone__sheen" aria-hidden="true" />
                </div>
              </div>
            </motion.div>
          </div>
        </div>
      </div>
    </section>
  );
}
