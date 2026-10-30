import {
  AnimatePresence,
  motion,
  useMotionValue,
  useMotionValueEvent,
  useReducedMotion,
  useSpring,
  useTransform,
  type MotionValue,
} from "framer-motion";
import { useCallback, useEffect, useLayoutEffect, useRef, useState } from "react";
import { useLocale } from "@/lib/strings";
import { SHELF_DAYS, dayDate, dayFacts, receiptAmount } from "../lib/days";
import { ReceiptPaper } from "../parts/Receipt";
import { s } from "../strings";

/**
 * The shelf (tt#63's second showpiece): a year of receipts standing spine-out on one endless rail.
 * The active one is pulled out and turned in 3D so its printed face meets you; clicking it grows a sheet
 * of paper out of the cover into a full-screen day. One rAF spring drives every box's transform.
 */
const N = SHELF_DAYS.length;
const HALF = N / 2;

const smoothstep = (x: number) => x * x * (3 - 2 * x);

/** Position of the nearest copy of index `i` relative to the shelf position `p`, inside [-N/2, N/2). */
function relative(i: number, p: number) {
  let r = (((i - p) % N) + N) % N;
  if (r >= HALF) r -= N;
  return r;
}

/** Half-open spin geometry: spines step by their width, the open cover pushes neighbours half its excess. */
function slot(r: number, ws: number, d: number, gap: number) {
  const step = ws + gap;
  const push = d - ws + 1.6 * gap;
  const b = Math.abs(r) < 1 ? (r + Math.sin(Math.PI * r) / Math.PI) / 2 : Math.sign(r) * 0.5;
  return r * step + push * b;
}

interface Metrics {
  h: number;
  ws: number;
  d: number;
  gap: number;
}

export function Shelf() {
  const locale = useLocale();
  const reduce = useReducedMotion() ?? false;
  const stage = useRef<HTMLDivElement>(null);
  const probe = useRef<HTMLSpanElement>(null);
  const [metrics, setMetrics] = useState<Metrics>({ h: 560, ws: 95, d: 269, gap: 17 });
  const [active, setActive] = useState(0);
  const [direction, setDirection] = useState(1);
  const [openAt, setOpenAt] = useState<number | null>(null);

  const pos = useMotionValue(0);
  const settledAt = useRef(0);
  const target = useRef(0);
  const frame = useRef(0);
  const dragging = useRef(false);
  const dragOrigin = useRef({ x: 0, p: 0, moved: false });

  useLayoutEffect(() => {
    const measure = () => {
      const h = probe.current?.offsetHeight ?? 0;
      if (!h) return;
      setMetrics({ h, ws: h * 0.169, d: h * 0.48, gap: h * 0.03 });
    };
    measure();
    const ro = typeof ResizeObserver === "undefined" ? null : new ResizeObserver(measure);
    if (probe.current) ro?.observe(probe.current);
    window.addEventListener("resize", measure);
    return () => {
      ro?.disconnect();
      window.removeEventListener("resize", measure);
    };
  }, []);

  const run = useCallback(() => {
    cancelAnimationFrame(frame.current);
    let last = performance.now();
    const tick = (now: number) => {
      const dt = Math.min(50, now - last) / 1000;
      last = now;
      const k = reduce ? 60 : dragging.current ? 20 : 7.6;
      const cur = pos.get();
      const next = cur + (target.current - cur) * (1 - Math.exp(-k * dt));
      if (Math.abs(target.current - next) < 0.0006) {
        pos.set(target.current);
        return;
      }
      pos.set(next);
      frame.current = requestAnimationFrame(tick);
    };
    frame.current = requestAnimationFrame(tick);
  }, [pos, reduce]);

  const goTo = useCallback(
    (index: number) => {
      // Shortest way round the loop, so clicking a spine three to the left never spins the whole shelf.
      const from = Math.round(target.current);
      const delta = relative(index, from);
      target.current = from + delta;
      run();
    },
    [run],
  );

  const step = useCallback(
    (dir: number) => {
      target.current = Math.round(target.current) + dir;
      run();
    },
    [run],
  );

  useMotionValueEvent(pos, "change", (v) => {
    const next = ((Math.round(v) % N) + N) % N;
    if (next === settledAt.current) return;
    setDirection(relative(next, settledAt.current) >= 0 ? 1 : -1);
    settledAt.current = next;
    setActive(next);
  });

  useEffect(() => () => cancelAnimationFrame(frame.current), []);

  // Horizontal wheel / shift-wheel scrubs the shelf; vertical scroolling is never hijacked.
  useEffect(() => {
    const el = stage.current;
    if (!el) return;
    const onWheel = (e: WheelEvent) => {
      const horizontal = Math.abs(e.deltaX) > Math.abs(e.deltaY);
      if (!horizontal && !e.shiftKey) return;
      e.preventDefault();
      target.current = Math.round(target.current) + (horizontal ? Math.sign(e.deltaX) : Math.sign(e.deltaY)) * 1;
      run();
    };
    el.addEventListener("wheel", onWheel, { passive: false });
    return () => el.removeEventListener("wheel", onWheel);
  }, [run]);

  const onPointerDown = (e: React.PointerEvent) => {
    if (e.pointerType === "mouse" && e.button !== 0) return;
    dragging.current = true;
    dragOrigin.current = { x: e.clientX, p: pos.get(), moved: false };
    (e.currentTarget as HTMLElement).setPointerCapture(e.pointerId);
  };
  const onPointerMove = (e: React.PointerEvent) => {
    if (!dragging.current) return;
    const dx = e.clientX - dragOrigin.current.x;
    if (!dragOrigin.current.moved && Math.abs(dx) < 6) return;
    dragOrigin.current.moved = true;
    const p = dragOrigin.current.p - dx / (metrics.ws + metrics.gap);
    pos.set(p);
    target.current = p;
  };
  const onPointerUp = () => {
    if (!dragging.current) return;
    dragging.current = false;
    target.current = Math.round(target.current);
    run();
  };

  const openDay = (i: number) => setOpenAt(i);
  const day = SHELF_DAYS[active];
  const facts = dayFacts(day);
  const date = dayDate(day, locale);

  return (
    <section id="shelf" className="lx-shelf" aria-label={s("shelf.aria")}>
      <header className="lx-sechead lx-sechead--row">
        <div>
          <p className="lx-eyebrow">05</p>
          <h2 className="lx-h2">{s("shelf.heading")}</h2>
        </div>
        <p className="lx-lead lx-lead--tight">{s("shelf.lead")}</p>
      </header>

      <span className="lx-shelf__probe" ref={probe} aria-hidden="true" />

      <div
        className="lx-shelf__stage"
        ref={stage}
        tabIndex={0}
        role="group"
        aria-roledescription="shelf"
        aria-label={s("shelf.aria")}
        onKeyDown={(e) => {
          if (e.key === "ArrowLeft") {
            e.preventDefault();
            step(-1);
          } else if (e.key === "ArrowRight") {
            e.preventDefault();
            step(1);
          } else if (e.key === "Enter") {
            openDay(active);
          }
        }}
        onPointerDown={onPointerDown}
        onPointerMove={onPointerMove}
        onPointerUp={onPointerUp}
        onPointerCancel={onPointerUp}
      >
        <div className="lx-shelf__boxes">
          {SHELF_DAYS.map((d, i) => (
            <Box
              key={d.id}
              index={i}
              pos={pos}
              metrics={metrics}
              active={active}
              onPick={() => openDay(i)}
              onFocusIndex={() => goTo(i)}
            />
          ))}
        </div>
      </div>

      <div className="lx-shelf__cap" aria-live="polite">
        <div className="lx-shelf__mask">
          <span key={`${day.id}-d`} className={`lx-shelf__capDate lx-slide-in${direction < 0 ? " is-down" : ""}`}>
            {date.full}
          </span>
        </div>
        <div className="lx-shelf__mask">
          <span key={`${day.id}-t`} className={`lx-shelf__capTitle lx-slide-in${direction < 0 ? " is-down" : ""}`}>
            {s(`title.${facts.title}` as Parameters<typeof s>[0])}
          </span>
        </div>
        <div className="lx-shelf__mask">
          <span key={`${day.id}-a`} className={`lx-shelf__capAmt lx-slide-in${direction < 0 ? " is-down" : ""}`}>
            ¥{receiptAmount(facts.total)} · {facts.count} {locale === "en" ? "" : "笔"}
          </span>
        </div>
      </div>

      <div className="lx-shelf__controls">
        <button type="button" className="lx-roundbtn" aria-label={s("shelf.prev")} onClick={() => step(-1)} data-cursor="link">
          <svg width="18" height="10" viewBox="0 0 18 10" aria-hidden="true">
            <path d="M5 1 1 5l4 4M1 5h16" fill="none" stroke="currentColor" strokeWidth="1.3" strokeLinecap="round" />
          </svg>
        </button>
        <p className="lx-hint">{s("shelf.hint")}</p>
        <button type="button" className="lx-roundbtn" aria-label={s("shelf.next")} onClick={() => step(1)} data-cursor="link">
          <svg width="18" height="10" viewBox="0 0 18 10" aria-hidden="true">
            <path d="M13 1l4 4-4 4M17 5H1" fill="none" stroke="currentColor" strokeWidth="1.3" strokeLinecap="round" />
          </svg>
        </button>
      </div>

      <button type="button" className="lx-shelf__open" onClick={() => openDay(active)} data-cursor="cta">
        {s("shelf.open")}
      </button>

      <AnimatePresence>
        {openAt !== null ? (
          <DaySheet key="sheet" index={openAt} pos={pos} metrics={metrics} onClose={() => setOpenAt(null)} />
        ) : null}
      </AnimatePresence>
    </section>
  );
}

function Box({
  index,
  pos,
  metrics,
  active,
  onPick,
  onFocusIndex,
}: {
  index: number;
  pos: MotionValue<number>;
  metrics: Metrics;
  active: number;
  onPick: () => void;
  onFocusIndex: () => void;
}) {
  const locale = useLocale();
  const lift = useSpring(0, { stiffness: 220, damping: 26, mass: 0.4 });
  const { h, ws, d, gap } = metrics;
  const day = SHELF_DAYS[index];
  const facts = dayFacts(day);
  const date = dayDate(day, locale);

  const r = useTransform(pos, (p) => relative(index, p));
  const o = useTransform(r, (v) => smoothstep(Math.max(0, 1 - Math.abs(v))));
  const transform = useTransform([r, o, lift], ([rv, ov, lv]: number[]) => {
    const x = slot(rv, ws, d, gap);
    const y = -ov * 0.025 * h - lv * 0.045 * h;
    const z = ov * 0.2 * h;
    return `translate3d(${x.toFixed(2)}px, ${y.toFixed(2)}px, ${z.toFixed(2)}px) rotateZ(${(-4.5 * ov).toFixed(3)}deg) rotateX(0deg) rotateY(${(-90 * ov).toFixed(3)}deg)`;
  });
  const zIndex = useTransform(r, (v) => Math.round(200 - Math.abs(v) * 6));
  const spineShade = useTransform(o, (v) => v * 0.55);
  const faceShade = useTransform(o, (v) => (1 - v) * 0.5);

  return (
    <motion.div
      className="lx-box"
      style={{ transform, zIndex, ["--o" as string]: o, ["--ws" as string]: `${ws}px`, ["--d" as string]: `${d}px`, ["--h" as string]: `${h}px` }}
      onPointerEnter={() => lift.set(1)}
      onPointerLeave={() => lift.set(0)}
      onClick={(e) => {
        e.stopPropagation();
        if (index === active) onPick();
        else onFocusIndex();
      }}
    >
      <div className="lx-box__spine" data-cursor="card">
        <motion.span className="lx-box__shade" style={{ opacity: spineShade }} aria-hidden="true" />
        <span className="lx-box__spineDate">{date.short}</span>
        <span className="lx-box__spineTitle">{s(`title.${facts.title}` as Parameters<typeof s>[0])}</span>
        <span className="lx-box__spineAmt">¥{receiptAmount(facts.total)}</span>
        <span className="lx-box__spineWeek">{date.weekday}</span>
      </div>
      <div className={`lx-box__face${index === active ? " is-front" : ""}`} data-cursor={index === active ? "cta" : "card"}>
        <ReceiptPaper day={day} />
        <motion.span className="lx-box__shade" style={{ opacity: faceShade }} aria-hidden="true" />
      </div>
    </motion.div>
  );
}

/** The day as a full sheet: it grows out of the shelf's cover rect, then the photo and lines arrive. */
function DaySheet({ index, pos, metrics, onClose }: { index: number; pos: MotionValue<number>; metrics: Metrics; onClose: () => void }) {
  const locale = useLocale();
  const day = SHELF_DAYS[index];
  const facts = dayFacts(day);
  const date = dayDate(day, locale);
  const [from, setFrom] = useState<{ x: number; y: number; w: number; h: number } | null>(null);
  const sheet = useRef<HTMLDivElement>(null);

  useLayoutEffect(() => {
    const r = relative(index, pos.get());
    const x = slot(r, metrics.ws, metrics.d, metrics.gap);
    const cx = window.innerWidth / 2 + x;
    setFrom({ x: cx - metrics.d / 2, y: window.innerHeight / 2 - metrics.h / 2, w: metrics.d, h: metrics.h });
  }, [index, metrics, pos]);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") onClose();
    };
    window.addEventListener("keydown", onKey);
    const prev = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    return () => {
      window.removeEventListener("keydown", onKey);
      document.body.style.overflow = prev;
    };
  }, [onClose]);

  const inset = from
    ? `inset(${from.y}px ${Math.max(0, window.innerWidth - from.x - from.w)}px ${Math.max(0, window.innerHeight - from.y - from.h)}px ${Math.max(0, from.x)}px round 3px)`
    : "inset(45% 42% 45% 42% round 3px)";

  return (
    <motion.div
      className="lx-sheet"
      ref={sheet}
      role="dialog"
      aria-modal="true"
      aria-label={`${date.full} · ${s(`title.${facts.title}` as Parameters<typeof s>[0])}`}
      initial={{ clipPath: inset, rotate: -4.5 }}
      animate={{ clipPath: "inset(0px 0px 0px 0px round 0px)", rotate: 0 }}
      exit={{ clipPath: inset, rotate: -4.5 }}
      transition={{ duration: 0.66, ease: [0.76, 0, 0.18, 1] }}
    >
      <button type="button" className="lx-textbtn lx-sheet__close" onClick={onClose} data-cursor="link">
        {s("shelf.close")}
      </button>
      <div className="lx-sheet__grid">
        <div className="lx-sheet__text">
          <p className="lx-eyebrow">{date.full}</p>
          <h3 className="lx-sheet__title">{s(`title.${facts.title}` as Parameters<typeof s>[0])}</h3>
          <p className="lx-sheet__total">¥{receiptAmount(facts.total)}</p>
          <p className="lx-sheet__label">{s("shelf.detail.spends")}</p>
          <ul className="lx-sheet__lines">
            {day.lines.length === 0 ? <li>{s("remark.zero")}</li> : null}
            {day.lines.map((line, i) => (
              <li key={`${line.label}-${i}`}>
                <span>{line.time}</span>
                <span>{s(line.label as Parameters<typeof s>[0])}</span>
                <span>¥{receiptAmount(line.cents)}</span>
              </li>
            ))}
          </ul>
          <p className="lx-sheet__label">{s("shelf.detail.remark")}</p>
          <p className="lx-sheet__remark">{s(facts.remark)}</p>
        </div>
        <figure className="lx-sheet__photo">
          <img src={day.photo} alt="" />
        </figure>
      </div>
    </motion.div>
  );
}
