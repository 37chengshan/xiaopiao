import {
  ArrowUpRight,
  AudioLines,
  Check,
  Coffee,
  CreditCard,
  Heart,
  Plus,
  Receipt,
  ShieldCheck,
  ShoppingBag,
  TrainFront,
  Users,
} from "lucide-react";
import {
  motion,
  useTransform,
  useMotionValueEvent,
  type MotionValue,
} from "framer-motion";
import { useEffect, useState } from "react";
import { BrandMark } from "@/components/brand/BrandMark";
import { yuan } from "@/domain/types";
import { cs, s, opening } from "../strings";
import { scenePose } from "./scenePose";
import { GlassFilter } from "../parts/GlassFilter";
import { IMG } from "../lib/assets";
export const CHAPTERS = ["glass", "record", "stats", "circle"] as const;
export type Chapter = (typeof CHAPTERS)[number];
export function ConceptScreen({
  chapter,
  emphasis,
  progress,
}: {
  chapter: Chapter;
  emphasis: MotionValue<number>;
  progress: MotionValue<number>;
}) {
  const dockTransform = useTransform(
    emphasis,
    (v) => `translate3d(0, ${-22 * v}px, 0) scale(${1 + 0.08 * v})`,
  );
  const [selected, setSelected] = useState(0);
  const [pressed, setPressed] = useState(false);

  const [phase, setPhase] = useState(0);
  const [cents, setCents] = useState(0);
  const clamp = (n: number) => Math.max(0, Math.min(1, n));
  useMotionValueEvent(progress, "change", (v) => {
    queueMicrotask(() => {
    setPhase(Math.min(3, Math.floor(v * 4)));
    if (chapter === "glass" && !pressed)
      setSelected(Math.min(3, Math.floor(v * 4)));
    // The week's total is already on screen when the bars are, so it counts up from the start;
    // the new entry counts up as it is parsed.
    if (chapter === "record")
      setCents(Math.round(clamp((v - 0.24) / 0.32) * 2800));
    if (chapter === "stats")
      setCents(Math.round(clamp((v + 0.06) / 0.46) * 26800));
    });
  });
  useEffect(() => {
    const v = progress.get();
    setPhase(Math.min(3, Math.floor(v * 4)));
    setCents(
      Math.round(
        Math.max(0, Math.min(1, chapter === "stats" ? (v + 0.06) / 0.46 : (v - 0.24) / 0.32)) *
          (chapter === "record" ? 2800 : 26800),
      ),
    );
  }, [chapter, progress]);
  const [whole, decimal = "00"] = yuan(cents).split(".");
  const speechTransform = useTransform(progress, scenePose.speech.input, scenePose.speech.output);
  const speechOpacity = useTransform(
    progress,
    [0, 0.08, 0.28],
    [0.15, 0.35, 1],
  );
  const entryTransform = useTransform(progress, scenePose.entry.input, scenePose.entry.output);
  const entryOpacity = useTransform(progress, [0, 0.22, 0.4], [0, 0, 1]);
  const doneOpacity = useTransform(progress, [0, 0.52, 0.68], [0, 0, 1]);
  const orbitTransform = useTransform(progress, scenePose.orbit.input, scenePose.orbit.output);
  const recapTransform = useTransform(progress, scenePose.recap.input, scenePose.recap.output);
  const liquidCard = useTransform(progress, scenePose.liquid.input, scenePose.liquid.output);
  const liquidOpacity = useTransform(progress, [0, 0.1], [0.5, 1]);
  const tagLift = useTransform(progress, scenePose.tag.input, scenePose.tag.output);
  const tagOpacity = useTransform(progress, [0, 0.55, 0.68], [0, 0, 1]);
  const buttonPress = useTransform(progress, scenePose.buttonPress.input, scenePose.buttonPress.output);
  const peakLift = useTransform(progress, scenePose.peak.input, scenePose.peak.output);
  const peakOpacity = useTransform(progress, [0, 0.62, 0.74], [0, 0, 1]);
  const recapOpacity = useTransform(progress, [0, 0.35, 0.62], [0, 0, 1]);
  const shopOpacity = useTransform(progress, [0, 0.48, 0.7], [0, 0, 1]);

  switch (chapter) {
    case "glass":
      return (
        <div className="cp-glass" style={{ "--lx-dock-filter": "url(#cp-dock-glass)" } as React.CSSProperties}>
          <img
            className="cp-glass__photo"
            src={IMG.friendsTable}
            alt=""
            aria-hidden="true"
          />
          <div className="cp-glass__greeting">
            <BrandMark />
            <h4>{cs("today")}</h4>
            <strong>{s("brand")}</strong>
          </div>
          <motion.div
            className="cp-liquid"
            style={{ transform: liquidCard, opacity: liquidOpacity }}
          >
            <div>
              <Coffee size={23} />
              <ArrowUpRight size={17} />
            </div>
            <p>{cs("coffee")}</p>
            <strong>
              ¥16<span>.00</span>
            </strong>
            <small>
              15:40 <span>✓</span> {cs("privacy")}
            </small>
          </motion.div>
          <motion.div
            className={`cp-glass__dock${pressed ? " is-pressed" : ""}`}
            style={{ transform: dockTransform }}
            role="group"
            aria-label={cs("glass")}
            onPointerDown={(e) => {
              e.currentTarget.setPointerCapture(e.pointerId);
              setPressed(true);
              const r = e.currentTarget.getBoundingClientRect();
              setSelected(
                Math.min(
                  3,
                  Math.max(0, Math.floor(((e.clientX - r.left) / r.width) * 4)),
                ),
              );
            }}
            onPointerMove={(e) => {
              if (!pressed) return;
              const r = e.currentTarget.getBoundingClientRect();
              setSelected(
                Math.min(
                  3,
                  Math.max(0, Math.floor(((e.clientX - r.left) / r.width) * 4)),
                ),
              );
            }}
            onPointerUp={() => setPressed(false)}
            onPointerCancel={() => setPressed(false)}
          >
            <GlassFilter
              id="cp-dock-glass"
              width={222}
              height={62}
              radius={31}
              band={12}
              strength={32}
            />
            <span className="cp-glass__dock-tint" aria-hidden="true" />
            <span className="cp-glass__dock-specular" aria-hidden="true" />
            <span
              className="cp-glass__capsule"
              aria-hidden="true"
              style={{ transform: `translate3d(${selected * 53}px, 0, 0) scale(${pressed ? 0.94 : 1})` }}
            />
            {[
              { Icon: Receipt, label: "today" },
              { Icon: Plus, label: "record" },
              { Icon: Users, label: "circle" },
              { Icon: CreditCard, label: "cards" },
            ].map(({ Icon, label }, i) => (
              <button
                type="button"
                key={label}
                aria-label={cs(
                  label as "today" | "record" | "circle" | "cards",
                )}
                aria-pressed={selected === i}
                onClick={() => setSelected(i)}
              >
                <Icon size={19} strokeWidth={1.65} />
              </button>
            ))}
          </motion.div>
        </div>
      );
    case "record":
      return (
        <div className="cp-record">
          <span className="cp-appname">
            <BrandMark />
            {s("brand")}
          </span>
          <motion.div
            className="cp-record__bubble"
            style={{ transform: speechTransform, opacity: speechOpacity }}
          >
            <AudioLines size={20} />
            {phase === 0
              ? opening("recordSpeech").slice(0, 3) + "…"
              : opening("recordSpeech")}
          </motion.div>
          <motion.div
            className="cp-entry"
            style={{ transform: entryTransform, opacity: entryOpacity }}
          >
            <span>
              <ShoppingBag size={20} />
              {cs("food")}
            </span>
            <strong>
              ¥{whole}
              <span>.{decimal.padEnd(2, "0")}</span>
            </strong>
            <motion.p style={{ opacity: doneOpacity }}>
              <Check size={14} />
              {cs("saved")}
            </motion.p>
            <motion.span className="cp-tag" style={{ opacity: tagOpacity, transform: tagLift }}>
              {cs("parsed")}
            </motion.span>
          </motion.div>
          <div className="cp-wave">
            {Array.from({ length: 19 }, (_, i) => (
              <i
                key={i}
                style={{
                  height: `${12 + ((i * 17) % 35)}px`,
                  animationDelay: `${i * 30}ms`,
                }}
              />
            ))}
          </div>
          <motion.div
            className={`cp-fauxbutton${phase >= 3 ? " is-down" : ""}`}
            style={{ opacity: doneOpacity, transform: buttonPress }}
          >
            {phase >= 2 ? opening("recordDone") : cs("confirm")}
            <Check size={17} />
          </motion.div>
        </div>
      );
    case "stats":
      return (
        <div className="cp-stats">
          <span className="cp-appname">
            <BrandMark />
            {cs("stats")}
          </span>
          <p>{cs("total")}</p>
          <strong>
            ¥{whole}
            <span>.{decimal.padEnd(2, "0")}</span>
          </strong>
          <div className="cp-chart">
            {[36, 62, 43, 80, 51, 96, 69].map((h, i) => (
              <SceneBar key={i} index={i} height={h} progress={progress} start={-0.2} />
            ))}
            <motion.span
              className="cp-chart__peak"
              style={{ opacity: peakOpacity, transform: peakLift }}
            >
              {cs("peak")}
            </motion.span>
          </div>
          <div className="cp-category">
            <span>
              <ShoppingBag size={16} />
              {cs("food")}
            </span>
            <b>62%</b>
            <i style={{ width: "62%" }} />
          </div>
          <div className="cp-category">
            <span>
              <TrainFront size={16} />
              {cs("transport")}
            </span>
            <b>23%</b>
            <i style={{ width: "23%" }} />
          </div>
          <div className="cp-category">
            <span>
              <Coffee size={16} />
              {cs("drink")}
            </span>
            <b>15%</b>
            <i style={{ width: "15%" }} />
          </div>
          <small>{cs("noAdvice")}</small>
        </div>
      );
    case "circle":
      return (
        <div className="cp-circle">
          <span className="cp-appname">
            <BrandMark />
            {cs("circle")}
          </span>
          <motion.div
            className="cp-orbit"
            style={{ transform: orbitTransform, opacity: speechOpacity }}
          >
            <span className="cp-avatar cp-avatar--one">{cs("you")}</span>
            <span className="cp-avatar cp-avatar--two">{cs("family")}</span>
            <span className="cp-avatar cp-avatar--three">{cs("roommate")}</span>
            <span className="cp-orbit__heart">
              <Heart size={28} />
            </span>
          </motion.div>
          <motion.div
            className="cp-recap"
            style={{ transform: recapTransform, opacity: recapOpacity }}
          >
            <span>{phase >= 2 ? opening("circleUpdate") : cs("weekly")}</span>
            <h4>{cs("together")}</h4>
            <div>
              {[65, 42, 90, 54, 100, 72, 47].map((h, i) => (
                <SceneBar
                  key={i}
                  index={i}
                  height={h}
                  progress={progress}
                  start={0.4}
                />
              ))}
            </div>
          </motion.div>
          <small>
            <ShieldCheck size={14} />
            {cs("sharing")}
          </small>
        </div>
      );
  }
}

function SceneBar({
  progress,
  index,
  height,
  start = 0.12,
}: {
  progress: MotionValue<number>;
  index: number;
  height: number;
  start?: number;
}) {
  const transform = useTransform(progress, (p) => {
    const x = Math.max(0, Math.min(1, (p - start - index * 0.035) / 0.35));
    return `scaleY(${x * x * (3 - 2 * x)})`;
  });
  return (
    <motion.i
      style={{ height: `${height}%`, transform, transformOrigin: "50% 100%" }}
    />
  );
}
