import { motion, useMotionValue, useSpring, useTransform } from "framer-motion";
import { useEffect } from "react";

/**
 * L.1 — damped glow halo (tt 2026-10-28: no colour inversion, just a soft trailing glow).
 *
 * A large warm radial glow trails the system pointer on a soft spring, so it lags and settles
 * instead of snapping. Over a `[data-cursor]` element it swells and brightens a little; while
 * pressed it pinches. The system pointer stays visible. Position, size and strength are motion
 * values only (transform + opacity, no re-render while moving).
 */
const SPRING = { stiffness: 90, damping: 18, mass: 0.9 } as const;
const HOVER_SPRING = { stiffness: 200, damping: 24, mass: 0.6 } as const;
const PRESS_SPRING = { stiffness: 420, damping: 28, mass: 0.4 } as const;
const DIAMETER = 280;

export function CursorHalo() {
  const x = useMotionValue(-DIAMETER);
  const y = useMotionValue(-DIAMETER);
  const hover = useMotionValue(0);
  const press = useMotionValue(1);
  const seen = useMotionValue(0);

  const sx = useSpring(x, SPRING);
  const sy = useSpring(y, SPRING);
  const grown = useSpring(hover, HOVER_SPRING);
  const pressed = useSpring(press, PRESS_SPRING);
  const shown = useSpring(seen, HOVER_SPRING);
  const scale = useTransform([grown, pressed], ([g, p]: number[]) => (0.8 + 0.35 * g) * p);
  const opacity = useTransform([shown, grown], ([s, g]: number[]) => s * (0.7 + 0.3 * g));

  useEffect(() => {
    const onMove = (event: PointerEvent) => {
      x.set(event.clientX);
      y.set(event.clientY);
      seen.set(1);
      const target = event.target instanceof Element ? event.target.closest("[data-cursor]") : null;
      hover.set(target ? 1 : 0);
    };
    const onLeave = () => seen.set(0);
    const onDown = () => press.set(0.88);
    const onUp = () => press.set(1);

    window.addEventListener("pointermove", onMove, { passive: true });
    document.documentElement.addEventListener("pointerleave", onLeave);
    window.addEventListener("pointerdown", onDown, { passive: true });
    window.addEventListener("pointerup", onUp, { passive: true });
    return () => {
      window.removeEventListener("pointermove", onMove);
      document.documentElement.removeEventListener("pointerleave", onLeave);
      window.removeEventListener("pointerdown", onDown);
      window.removeEventListener("pointerup", onUp);
    };
  }, [x, y, hover, press, seen]);

  return (
    <motion.div
      aria-hidden="true"
      style={{
        x: sx,
        y: sy,
        scale,
        opacity,
        width: DIAMETER,
        height: DIAMETER,
        marginLeft: -DIAMETER / 2,
        marginTop: -DIAMETER / 2,
        // The soft trailing halo is tt's own requirement (tt#63, revised 2026-10-28).
        background:
          "radial-gradient(circle, rgb(255 138 0 / 0.18) 0%, rgb(255 170 60 / 0.09) 34%, rgb(255 200 120 / 0) 68%)", // taste-skill-ignore
      }}
      className="pointer-events-none fixed top-0 left-0 z-[80] rounded-full"
    />
  );
}
