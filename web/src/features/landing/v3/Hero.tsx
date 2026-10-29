import { motion, useReducedMotion, useScroll, useTransform } from "framer-motion";
import { useEffect, useRef, useState } from "react";
import { IMG } from "../lib/assets";
import { s } from "../strings";

const REEL = [IMG.nightDesk, IMG.metroPhone, IMG.takeoutNight, IMG.friendsTable, IMG.printerSlot];
const TIMES = ["21:30", "08:24", "12:30", "19:20", "21:30"];
const GAP = 24;

/**
 * Sentence split by a frame: 「你的一天，」 left, 「今晚打印出来」 right, and between them a reel of the day
 * passing through the frame as you scroll (the stage is sticky; nothing hijacks the wheel).
 */
export function Hero() {
  const wrap = useRef<HTMLElement>(null);
  const frame = useRef<HTMLDivElement>(null);
  const [h, setH] = useState(0);
  const reduce = useReducedMotion();
  const { scrollYProgress } = useScroll({ target: wrap, offset: ["start start", "end end"] });

  useEffect(() => {
    const el = frame.current;
    if (!el) return;
    const ro = new ResizeObserver(() => setH(el.getBoundingClientRect().height));
    ro.observe(el);
    return () => ro.disconnect();
  }, []);

  const steps = REEL.length - 1;
  const y = useTransform(scrollYProgress, (p) => -h / 2 - (reduce ? 0 : p) * steps * (h + GAP));
  const index = useTransform(scrollYProgress, (p) => Math.min(steps, Math.round(p * steps)));
  const [i, setI] = useState(0);
  useEffect(() => index.on("change", (v) => setI(v)), [index]);

  return (
    <header ref={wrap} className="lp3-hero" id="top">
      <div className="lp3-hero__stage">
        <h1 className="lp3-hero__h1">
          <span className="lp3-hero__half lp3-hero__half--a">{s("hero.a")}</span>{" "}
          <span className="lp3-hero__half lp3-hero__half--b">{s("hero.b")}</span>
        </h1>
        <div className="lp3-reel" aria-label={s("hero.frame")} role="img">
            <motion.div className="lp3-reel__track" style={{ y }}>
              {REEL.map((src, k) => (
                <div key={src + k} ref={k === 0 ? frame : undefined} className="lp3-reel__frame">
                  <img src={src} alt="" width={1600} height={900} loading={k === 0 ? "eager" : "lazy"} />
                </div>
              ))}
            </motion.div>
        </div>
        <p className="lp3-hero__lead" data-reveal>
          {s("hero.lead")}
        </p>
        <p className="lp3-hero__time" aria-hidden="true">
          {TIMES[i]}
        </p>
      </div>
    </header>
  );
}
