import {
  motion,
  useMotionValue,
  useSpring,
  useMotionValueEvent,
  useScroll,
  useTransform,
  type MotionValue,
} from "framer-motion";
import { useEffect, useRef, useState } from "react";
import { useLocale } from "@/lib/strings";
import { s, cs } from "../strings";
import { CHAPTERS, ConceptScreen } from "./PhoneConcept";
import { useLayoutEffect } from "react";
import { useMediaQuery } from "../hooks/useMediaQuery";
import "./phone-concepts.css";
import { cameraPath, cameraSmooth } from "./cameraPath";

/**
 * The launch section: one CSS-3D phone, pinned for three viewports. The phone turns from tilted to square
 * to slightly past square while six conceptual interface studies move through it and the copy steps beside it.
 * Everything is opacity/transform driven by a single scroll progress.
 */
const STEPS = CHAPTERS;

const N = STEPS.length;

/**
 * One step's window. Steps stack instead of cross-fading: the incoming block or screen rides up from just
 * below and covers the one before it, so two app screens are never double-exposed at once.
 */
function stepWindow(i: number) {
  const start = i / N;
  return { start, end: (i + 1) / N };
}

function StepCopy({
  i,
  progress,
  reduce,
}: {
  i: number;
  progress: MotionValue<number>;
  reduce: boolean;
}) {
  const { start, end } = stepWindow(i);
  // The copy is its own cue: it rises into the window, holds while the camera is on the detail, then
  // leaves upward as the next line arrives. It carries no background plate, so nothing paints over the page.
  const inputs: number[] = [];
  const outputs: string[] = [];
  if (i > 0) {
    inputs.push(Math.max(0, start - 0.06));
    outputs.push("104%");
  }
  inputs.push(start);
  outputs.push("0%");
  if (i < STEPS.length - 1) {
    inputs.push(Math.min(1, end - 0.05));
    outputs.push("0%");
    inputs.push(Math.min(1, end));
    outputs.push("-104%");
  }
  const y = useTransform(progress, inputs, outputs);
  const step = STEPS[i];

  return (
    <motion.div
      className="lx-launch__step"
      style={reduce ? { opacity: i === 0 ? 1 : 0 } : { y }}
    >
      <h3 className="lx-launch__title">{cs(`${step}Title`)}</h3>
      <p className="lx-launch__line">{cs(`${step}Body`)}</p>
      <p className="lx-launch__detail-hint">{cs("cameraHint")}</p>
    </motion.div>
  );
}

function ConceptArt({
  chapter,
  emphasis,
  sceneProgress,
}: {
  chapter: (typeof CHAPTERS)[number];
  emphasis: MotionValue<number>;
  sceneProgress: MotionValue<number>;
}) {
  const ref = useRef<HTMLDivElement>(null);
  const [scale, setScale] = useState({ x: 1, y: 1 });
  useLayoutEffect(() => {
    const el = ref.current;
    if (!el) return;
    const update = () =>
      setScale({ x: el.clientWidth / 260, y: el.clientHeight / 560 });
    const observer = new ResizeObserver(update);
    observer.observe(el);
    update();
    return () => observer.disconnect();
  }, []);
  return (
    <div className="lx-phone__concept-window" ref={ref}>
      <div
        className={`pc-art cp-mode-${chapter}`}
        style={{ transform: `scale(${scale.x}, ${scale.y})` }}
      >
        <ConceptScreen
          chapter={chapter}
          emphasis={emphasis}
          progress={sceneProgress}
        />
      </div>
    </div>
  );
}

export function PhoneLaunch() {
  useLocale();
  const track = useRef<HTMLElement>(null);
  const stage = useRef<HTMLDivElement>(null);
  const phone = useRef<HTMLDivElement>(null);
  const viewW = useMotionValue(1440),
    viewH = useMotionValue(900);
  const phoneW = useMotionValue(310),
    phoneH = useMotionValue(668);
  const finalScene = useMotionValue(1);
  const [copyRight, setCopyRight] = useState(false);
  useLayoutEffect(() => {
    const update = () => {
      if (!stage.current || !phone.current) return;
      viewW.set(stage.current.clientWidth);
      viewH.set(stage.current.clientHeight);
      phoneW.set(phone.current.offsetWidth);
      phoneH.set(phone.current.offsetHeight);
    };
    const observer = new ResizeObserver(update);
    if (stage.current) observer.observe(stage.current);
    if (phone.current) observer.observe(phone.current);
    update();
    return () => observer.disconnect();
  }, [viewW, viewH, phoneW, phoneH]);
  const reduce = useMediaQuery("(prefers-reduced-motion: reduce)");
  const { scrollYProgress } = useScroll({
    target: track,
    offset: ["start start", "end end"],
  });
  const cameraProgress = useSpring(scrollYProgress, { duration: 0.4, bounce: 0 });
  const [visible, setVisible] = useState(false);
  useEffect(() => {
    if (!stage.current) return;
    const observer = new IntersectionObserver(
      ([e]) => setVisible(e.isIntersecting),
      { threshold: 0.02 },
    );
    observer.observe(stage.current);
    return () => observer.disconnect();
  }, []);
  const [stepIndex, setStepIndex] = useState(0);
  useMotionValueEvent(cameraProgress, "change", (v) => {
    const next = Math.min(N - 1, Math.max(0, Math.floor(v * N + 0.0001)));
    const right = cameraPath(v, viewW.get(), viewH.get(), phoneW.get(), phoneH.get()).x < 0;
    // Derived spring values may synchronise during a React render (for example on locale change).
    // Queue the discrete chapter/copy updates outside that render; frame transforms stay on MotionValues.
    queueMicrotask(() => {
      setStepIndex(previous => previous === next ? previous : next);
      setCopyRight(previous => previous === right ? previous : right);
    });
  });

  const pose = useTransform(() =>
    cameraPath(
      cameraProgress.get(),
      viewW.get(),
      viewH.get(),
      phoneW.get(),
      phoneH.get(),
    ),
  );
  const camera = useTransform(
    pose,
    (p) =>
      `perspective(1800px) translate3d(${p.x}px,${p.y}px,0) rotateX(${p.rx}deg) rotateY(${p.ry}deg) rotateZ(${p.rz}deg) scale(${p.scale})`,
  );
  const copyOpacity = useTransform(
    pose,
    (p) => 1 - cameraSmooth((p.focus - 0.15) / 0.4),
  );
  const copyTransform = useTransform(
    pose,
    (p) => `translate3d(${p.focus * (p.x < 0 ? 45 : -45)}px,-50%,0)`,
  );
  const sceneProgress = useTransform(
    cameraProgress,
    (p) => Math.min(N - 1e-5, p * N) % 1,
  );
  const emphasis = useTransform(cameraProgress, (p) => {
    const t = p * N;
    if (t >= 1) return 0;
    const smooth = (v: number) => {
      const x = Math.max(0, Math.min(1, v));
      return x * x * (3 - 2 * x);
    };
    return smooth((t - 0.12) / 0.27) * (1 - smooth((t - 0.7) / 0.27));
  });
  const glow = useTransform(cameraProgress, [0, 0.5, 1], [0.35, 0.6, 0.4]);

  const jump = (i: number) => {
    const el = track.current;
    if (!el) return;
    const total = el.offsetHeight - window.innerHeight;
    const top = window.scrollY + el.getBoundingClientRect().top;
    window.scrollTo({ top: top + total * (i / N) + 8, behavior: "smooth" });
  };

  return (
    <section
      id="launch"
      className={`lx-launch${visible ? " is-live" : ""}`}
      ref={track}
      aria-label={s("launch.kicker")}
    >
      <div
        className={`lx-launch__stage${reduce ? " is-reduced" : ""}`}
        ref={stage}
      >
        <div className="lx-launch__grid">
          <motion.div
            className={`lx-launch__copy${copyRight ? " is-camera-right" : ""}`}
            style={
              reduce
                ? undefined
                : { opacity: copyOpacity, transform: copyTransform }
            }
          >
            <p className="lx-eyebrow">{s("launch.kicker")}</p>
            <div className="lx-launch__steps">
              {STEPS.map((step, i) => (
                <StepCopy
                  key={step}
                  i={i}
                  progress={cameraProgress}
                  reduce={reduce}
                />
              ))}
            </div>
          </motion.div>

          <div
            className="lx-launch__phonewrap"
            aria-label={s("launch.aria")}
            role="group"
          >
            <motion.div
              className="lx-phone__glow"
              style={{ opacity: reduce ? 0.5 : glow }}
              aria-hidden="true"
            />
            <motion.div
              ref={phone}
              className="lx-phone"
              style={reduce ? undefined : { transform: camera }}
            >
              <div className="lx-phone__body">
                <span className="lx-phone__island" aria-hidden="true" />
                <div className="lx-phone__screen">
                  <ConceptArt
                    chapter={STEPS[stepIndex]}
                    emphasis={emphasis}
                    sceneProgress={reduce ? finalScene : sceneProgress}
                  />
                  <span className="lx-phone__sheen" aria-hidden="true" />
                </div>
              </div>
            </motion.div>
          </div>
          <div className="lx-launch__dots">
            {STEPS.map((step, i) => (
              <button
                key={step}
                type="button"
                aria-label={cs(`${step}Title`)}
                className={`lx-dot${i === stepIndex ? " is-on" : ""}`}
                onClick={() => jump(i)}
              />
            ))}
          </div>
          {reduce ? (
            <div className="lx-launch__concept-summaries">
              {STEPS.slice(1).map((step) => (
                <article key={step}>
                  <h3>{cs(`${step}Title`)}</h3>
                  <p>{cs(`${step}Body`)}</p>
                </article>
              ))}
            </div>
          ) : null}
        </div>
      </div>
    </section>
  );
}
