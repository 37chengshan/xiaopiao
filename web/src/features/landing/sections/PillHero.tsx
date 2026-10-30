import { Link } from "@tanstack/react-router";
import {
  motion,
  useMotionValueEvent,
  useScroll,
  useTransform,
  type MotionValue,
} from "framer-motion";
import { useEffect, useRef, useState } from "react";
import { ChevronDown, Github } from "lucide-react";
import { useMediaQuery } from "../hooks/useMediaQuery";
import { IMG } from "../lib/assets";
import { s, opening } from "../strings";
import "./opening-context.css";

const SHOTS = [
  { src: IMG.nightDesk, alt: "hero.photo.desk" },
  { src: IMG.handReceipt, alt: "hero.photo.hand" },
  { src: IMG.friendsTable, alt: "hero.photo.friends" },
  { src: IMG.takeoutNight, alt: "hero.photo.takeout" },
] as const;
const STOPS = [0, 0.14, 0.3, 0.58, 1];

/** Keeps the original scroll-open capsule. Context sits at the foot, below the headline and cue. */
export function PillHero({ progress }: { progress: MotionValue<number> }) {
  const track = useRef<HTMLElement>(null);
  const narrow = useMediaQuery("(max-width: 720px)");
  const reduce = useMediaQuery("(prefers-reduced-motion: reduce)");
  const [introVisible, setIntroVisible] = useState(true);
  const { scrollYProgress } = useScroll({
    target: track,
    offset: ["start start", "end end"],
  });
  useEffect(() => {
    progress.set(scrollYProgress.get());
  }, [progress, scrollYProgress]);
  useMotionValueEvent(scrollYProgress, "change", (v) => {
    progress.set(v);
    queueMicrotask(() => setIntroVisible(v < 0.29));
  });
  const width = useTransform(scrollYProgress, STOPS, [
    "0.024em",
    "0.16em",
    "1.78em",
    "1.78em",
    "3.62em",
  ]);
  const height = useTransform(scrollYProgress, STOPS, [
    "0.024em",
    "0.16em",
    "0.62em",
    "1.1em",
    "1.46em",
  ]);
  const gap = useTransform(scrollYProgress, STOPS, [
    "0.16em",
    "0.18em",
    "0.22em",
    "0.22em",
    "0.3em",
  ]);
  const gapNarrow = useTransform(scrollYProgress, STOPS, [
    "0.06em",
    "0.12em",
    "0.16em",
    "0.16em",
    "0.2em",
  ]);
  const clip = useTransform(
    scrollYProgress,
    [0, 0.14],
    ["inset(50% 0% 50% 0%)", "inset(0% 0% 0% 0%)"],
  );
  const hair = useTransform(scrollYProgress, [0.08, 0.16, 0.3], [1, 1, 0]);
  const hintOpacity = useTransform(scrollYProgress, [0, 0.1], [1, 0]);
  const introOpacity = useTransform(scrollYProgress, [0, 0.1, 0.29], [1, 1, 0]);
  const geometryOpacity = useTransform(
    scrollYProgress,
    [0, 0.55, 0.85],
    [0.9, 0.45, 0],
  );
  const leftShape = useTransform(
    scrollYProgress,
    (p) => `translate3d(${-p * 50}px,${p * 70}px,0) rotate(${-24 + p * 16}deg)`,
  );
  const rightShape = useTransform(
    scrollYProgress,
    (p) => `translate3d(${p * 60}px,${-p * 55}px,0) rotate(${24 - p * 20}deg)`,
  );
  const bandY = useTransform(scrollYProgress, [0.4, 0.62], [140, 0]);
  const bandOpacity = useTransform(scrollYProgress, [0.38, 0.6], [0, 1]);
  const rule = useTransform(scrollYProgress, [0.4, 0.72], [0, 1]);
  function explore() {
    const el = track.current;
    if (!el) return;
    const top = el.getBoundingClientRect().top + scrollY;
    window.scrollTo({
      top: top + (el.offsetHeight - innerHeight) * 0.55,
      behavior: reduce ? "instant" : "smooth",
    });
  }
  return (
    <section
      id="today"
      className="lx-hero"
      ref={track}
      aria-label={opening("title")}
    >
      <div className="lx-hero__stage">
        <motion.div
          className="lx-opening-geometry"
          aria-hidden="true"
          style={{ opacity: reduce ? 0.8 : geometryOpacity }}
        >
          <span className="lx-opening-orb lx-opening-orb--amber" />
          <motion.span
            className="lx-opening-glass lx-opening-glass--plate"
            style={{ transform: reduce ? "rotate(-24deg)" : leftShape }}
          />
          <span className="lx-opening-orb lx-opening-orb--gold" />
          <motion.span
            className="lx-opening-glass lx-opening-glass--lens"
            style={{ transform: reduce ? "rotate(24deg)" : rightShape }}
          />
          <span className="lx-opening-wire" />
        </motion.div>
        <div className="lx-hero__center">
          <motion.h1
            lang="en"
            aria-label={opening("title")}
            className="lx-hero__row lx-hero__row--english"
            style={{
              gap: reduce ? undefined : narrow ? gapNarrow : gap,
              fontSize: narrow ? "min(28vw,15svh)" : "min(13svh,9.6vw)",
            }}
            data-open={reduce ? "1" : undefined}
          >
            <span className="lx-hero__word">{opening("wordL")}</span>
            <motion.span
              className="lx-hero__pill"
              aria-hidden="true"
              style={
                reduce
                  ? { width: narrow ? "100%" : "3.62em", height: "1.46em" }
                  : narrow
                    ? { width: "100%", height }
                    : { width, height: "1.78em" }
              }
            >
              <motion.span
                className="lx-hero__media"
                style={{ clipPath: reduce ? "inset(0%)" : clip }}
              >
                {SHOTS.map((shot, i) => (
                  <img
                    key={shot.src}
                    src={shot.src}
                    alt={s(shot.alt)}
                    className="lx-hero__shot"
                    style={{ ["--i" as string]: i }}
                    decoding="async"
                  />
                ))}
              </motion.span>
              <motion.span
                className="lx-hero__hair"
                style={{ opacity: reduce ? 0 : hair }}
              />
            </motion.span>
            <span className="lx-hero__word">{opening("wordR")}</span>
          </motion.h1>
          <motion.button
            type="button"
            className="lx-scroll-cue"
            style={{ opacity: reduce ? 1 : hintOpacity }}
            onClick={explore}
            tabIndex={!reduce && !introVisible ? -1 : 0}
            aria-label={opening("down")}
          >
            <span className="lx-scroll-cue__arrows" aria-hidden="true">
              <ChevronDown />
              <ChevronDown />
              <ChevronDown />
            </span>
            <span className="lx-scroll-cue__label">{opening("down")}</span>
          </motion.button>
        </div>
        <motion.div
          className="lx-opening-context"
          style={{ opacity: reduce ? 1 : introOpacity }}
          inert={!reduce && !introVisible}
          aria-hidden={!reduce && !introVisible}
        >
          <div className="lx-opening-context__intro">
            <strong>{opening("product")}</strong>
            <p>{opening("intro")}</p>
          </div>
          <div className="lx-opening-context__meta">
            <span>{opening("competition")}</span>
            <a
              href="https://github.com/37chengshan/xiaopiao"
              target="_blank"
              rel="noopener noreferrer"
            >
              <Github size={17} />
              {opening("github")}
            </a>
          </div>
        </motion.div>
        <motion.div
          className="lx-hero__band"
          style={reduce ? undefined : { y: bandY, opacity: bandOpacity }}
        >
          <p className="lx-hero__lead">{s("hero.lead")}</p>
          <motion.span
            className="lx-hero__rule"
            style={{ scaleX: reduce ? 1 : rule }}
            aria-hidden="true"
          />
          <Link to="/app" className="lx-pillbtn lx-pillbtn--ink lx-pillbtn--lg">
            {s("nav.cta")}
          </Link>
        </motion.div>
      </div>
    </section>
  );
}
