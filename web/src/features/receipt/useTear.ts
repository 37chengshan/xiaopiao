import { animate, type MotionValue } from "framer-motion";
import { useRef, useState, type PointerEvent } from "react";
import { releaseVelocity, rubberband, shouldTear, TEAR_DISTANCE } from "./physics";
import { tearRip } from "./sound";
import { haptic } from "./haptics";

interface Values {
  x: MotionValue<number>;
  y: MotionValue<number>;
  rot: MotionValue<number>;
  opacity: MotionValue<number>;
  /** scaleY from the perforation: the paper stretches a hair before the fibres give */
  stretch: MotionValue<number>;
}

const clamp = (v: number, lo: number, hi: number) => Math.min(hi, Math.max(lo, v));

/**
 * Pull the printed paper down to tear it off (docs/DESIGN.md §7.5): 1:1 tracking from where the finger grabbed,
 * rubber-band when pulled up, release velocity projected to decide, then handed to a spring to fly out.
 * Grabbing a settling paper picks it up from its live position (apple-design §3).
 */
export function useTear(
  { x, y, rot, opacity, stretch }: Values,
  opts: { enabled: boolean; reduce: boolean; onTear: () => void; onTorn: () => void; /** where the torn sheet comes to rest, px below the slot (defaults to flying off screen) */ restY?: number },
) {
  const grab = useRef<{ id: number; ox: number; oy: number; samples: { t: number; y: number }[] } | null>(null);
  const armedRef = useRef(false);
  const [armed, setArmed] = useState(false);
  const [dragging, setDragging] = useState(false);

  const stopAll = () => [x, y, rot].forEach((v) => v.stop());

  const tear = (velocity: number) => {
    if (!opts.enabled) return;
    opts.onTear();
    tearRip();
    haptic("tear");
    if (opts.reduce) {
      void animate(opacity, 0, { duration: 0.15, ease: "easeOut" }).then(opts.onTorn);
      return;
    }
    const dir = x.get() < 0 ? -1 : 1;
    const spring = { type: "spring" as const, stiffness: 240, damping: 26, restDelta: 4 };
    const restY = opts.restY ?? window.innerHeight + 240;
    // 1) stretch along the perforation, 2) fibres tear: snap back, 3) the sheet leaves the roll
    void animate(stretch, 1.04, { duration: 0.09, ease: [0.23, 1, 0.32, 1] }).then(() => {
      void animate(stretch, 1, { type: "spring", stiffness: 700, damping: 18 });
      void animate(rot, rot.get() + dir * (opts.restY ? 1.2 : 6), spring);
      if (!opts.restY) void animate(x, x.get() + dir * 24, spring);
      void animate(y, restY, { ...spring, velocity: Math.max(velocity, 500) }).then(opts.onTorn);
    });
  };

  const back = (velocity: number) => {
    void animate(y, 0, { type: "spring", bounce: 0.2, duration: 0.42, velocity });
    void animate(x, 0, { type: "spring", bounce: 0, duration: 0.35 });
    void animate(rot, 0, { type: "spring", bounce: 0.15, duration: 0.42 });
  };

  const handlers = {
    onPointerDown: (e: PointerEvent<HTMLElement>) => {
      if (!opts.enabled || opts.reduce || grab.current) return;
      stopAll();
      e.currentTarget.setPointerCapture(e.pointerId);
      grab.current = { id: e.pointerId, ox: e.clientX - x.get(), oy: e.clientY - y.get(), samples: [{ t: e.timeStamp, y: e.clientY }] };
      armedRef.current = false;
      setDragging(true);
    },
    onPointerMove: (e: PointerEvent<HTMLElement>) => {
      const g = grab.current;
      if (!g || g.id !== e.pointerId) return;
      g.samples.push({ t: e.timeStamp, y: e.clientY });
      if (g.samples.length > 12) g.samples.shift();
      const raw = e.clientY - g.oy;
      const yy = raw >= 0 ? raw : rubberband(raw, 140);
      const dx = clamp((e.clientX - g.ox) * 0.45, -60, 60);
      y.set(yy);
      x.set(dx);
      rot.set(clamp(dx * 0.05 + yy * 0.008, -8, 8));
      const now = yy >= TEAR_DISTANCE;
      if (now !== armedRef.current) {
        armedRef.current = now;
        setArmed(now);
        if (now) haptic("arm");
      }
    },
    onPointerUp: (e: PointerEvent<HTMLElement>) => {
      const g = grab.current;
      if (!g || g.id !== e.pointerId) return;
      grab.current = null;
      setDragging(false);
      setArmed(false);
      const v = releaseVelocity(g.samples);
      if (shouldTear(y.get(), v)) tear(v);
      else back(v);
    },
    onPointerCancel: (e: PointerEvent<HTMLElement>) => {
      if (grab.current?.id !== e.pointerId) return;
      grab.current = null;
      setDragging(false);
      setArmed(false);
      back(0);
    },
  };

  return { handlers, armed, dragging, tear };
}
