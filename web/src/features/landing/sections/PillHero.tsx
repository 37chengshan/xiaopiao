import { Link } from "@tanstack/react-router";
import { motion, useMotionValueEvent, useScroll, useTransform, useReducedMotion, type MotionValue } from "framer-motion";
import { useEffect, useRef } from "react";
import { useMediaQuery } from "../hooks/useMediaQuery";
import { IMG } from "../lib/assets";
import { s } from "../strings";

/**
 * Hero: a hairline between 今日 and 小票 opens into a slit, a circle, then a stadium pill holding the day's
 * photographs. The opening is scroll-driven (tt: it must follow the wheel, never autoplay), so the section
 * pins for one viewport and the whole reveal is one progress value. Nav and the bottom band ride on it.
 */
const SHOTS = [
  { src: IMG.nightDesk, alt: "hero.photo.desk" },
  { src: IMG.handReceipt, alt: "hero.photo.hand" },
  { src: IMG.friendsTable, alt: "hero.photo.friends" },
  { src: IMG.takeoutNight, alt: "hero.photo.takeout" },
] as const;

/** The same five stops for every property, so width, clip and shade all open together. */
const STOPS = [0, 0.14, 0.3, 0.58, 1] as const;

export function PillHero({ progress }: { progress: MotionValue<number> }) {
  const track = useRef<HTMLElement>(null);
  const narrow = useMediaQuery("(max-width: 720px)");
  const reduce = useReducedMotion();
  const { scrollYProgress } = useScroll({ target: track, offset: ["start start", "end end"] });

  useEffect(() => {
    progress.set(scrollYProgress.get());
  }, [progress, scrollYProgress]);
  useMotionValueEvent(scrollYProgress, "change", (v) => progress.set(v));

  const width = useTransform(scrollYProgress, [...STOPS], ["0.024em", "0.16em", "1.78em", "1.78em", "3.62em"]);
  const height = useTransform(scrollYProgress, [...STOPS], ["0.024em", "0.16em", "0.62em", "1.1em", "1.46em"]);
  const gap = useTransform(scrollYProgress, [...STOPS], ["0.16em", "0.18em", "0.22em", "0.22em", "0.3em"]);
  const gapNarrow = useTransform(scrollYProgress, [...STOPS], ["0.06em", "0.12em", "0.16em", "0.16em", "0.2em"]);
  const clip = useTransform(scrollYProgress, [0, 0.14], ["inset(50% 0% 50% 0%)", "inset(0% 0% 0% 0%)"]);
  const hair = useTransform(scrollYProgress, [0.08, 0.16, 0.3], [1, 1, 0]);
  const hintOpacity = useTransform(scrollYProgress, [0, 0.1], [1, 0]);
  const bandY = useTransform(scrollYProgress, [0.4, 0.62], [140, 0]);
  const bandOpacity = useTransform(scrollYProgress, [0.38, 0.6], [0, 1]);
  const lines = useTransform(scrollYProgress, [0.42, 0.8], [0, 0.85]);
  const tagline = useTransform(scrollYProgress, [0, 0.22, 0.55], [1, 1, 0]);
  const rule = useTransform(scrollYProgress, [0.4, 0.72], [0, 1]);

  return (
    <section id="today" className="lx-hero" ref={track} aria-label={s("brand")}>
      <div className="lx-hero__stage">
        <motion.svg className="lx-hero__lines" style={{ opacity: reduce ? 1 : lines }} viewBox="0 0 1440 900" aria-hidden="true">
          <g fill="none" stroke="#fff" strokeLinecap="round">
            <path d="M-60 760C300 700 520 520 760 300s420-330 760-360" strokeWidth="2.4" />
            <path d="M-40 830C360 780 600 600 830 380s400-300 700-330" strokeWidth="1.4" />
            <path d="M-80 660C280 620 470 470 690 250s460-300 840-330" strokeWidth="3.2" />
            <path d="M120 930C420 830 640 640 900 430s420-330 640-350" strokeWidth="1.8" />
            <path d="M-60 1010C420 940 680 760 960 520s380-360 620-390" strokeWidth="2.6" />
            <path d="M420 1010C640 900 900 720 1160 520s340-300 420-330" strokeWidth="1.2" />
          </g>
        </motion.svg>

        <div className="lx-hero__center">
          <motion.div
            className="lx-hero__row"
            style={{ gap: reduce ? undefined : narrow ? gapNarrow : gap }}
            data-open={reduce ? "1" : undefined}
          >
            <span className="lx-hero__word">{s("hero.wordL")}</span>
            <motion.div
              className="lx-hero__pill"
              role="img"
              aria-label={s("hero.pillAria")}
              style={
                reduce
                  ? { width: narrow ? "100%" : "3.62em", height: narrow ? "1.46em" : "1.78em" }
                  : narrow
                    ? { width: "100%", height }
                    : { width, height: "1.78em" }
              }
            >
              <motion.div className="lx-hero__media" style={{ clipPath: reduce ? "inset(0%)" : clip }}>
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
              </motion.div>
              <motion.span className="lx-hero__hair" style={{ opacity: reduce ? 0 : hair }} aria-hidden="true" />
            </motion.div>
            <span className="lx-hero__word">{s("hero.wordR")}</span>
          </motion.div>

          <motion.p className="lx-hero__hint" style={{ opacity: reduce ? 0 : hintOpacity }}>
            {s("hero.open")}
          </motion.p>
        </div>

        <motion.div
          className="lx-hero__band"
          style={reduce ? undefined : { y: bandY, opacity: bandOpacity }}
        >
          <p className="lx-hero__lead">{s("hero.lead")}</p>
          <motion.span className="lx-hero__rule" style={{ scaleX: reduce ? 1 : rule }} aria-hidden="true" />
          <Link to="/app" className="lx-pillbtn lx-pillbtn--ink lx-pillbtn--lg" data-cursor="cta">
            {s("nav.cta")}
          </Link>
        </motion.div>

        <motion.span className="lx-hero__tagline" style={{ opacity: reduce ? 0 : tagline }} aria-hidden="true">
          {s("brand")} · 2026
        </motion.span>
      </div>
    </section>
  );
}
