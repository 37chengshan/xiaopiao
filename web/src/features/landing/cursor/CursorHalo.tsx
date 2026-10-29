import { motion, useMotionValue, useSpring, useTransform } from "framer-motion";
import { useEffect, useState } from "react";

/**
 * L.1 — damped cursor halo.
 *
 * A white disc with `mix-blend-mode: difference`, so text and art behind it invert. It grows to
 * 72px and shows a short uppercase label when the pointer is over a `[data-cursor]` element
 * (`data-cursor-label` holds the copy), and pinches to 0.85 while pressed.
 *
 * Both layers are `position: fixed` and carry the blend mode themselves — wrapping them in a
 * positioned container would create an isolation group and kill the inversion. The label sits
 * above the disc in paint order, so it comes out in the page's own colour.
 * Only the label uses React state; position and size are motion values (no re-render while moving).
 */
const SPRING = { stiffness: 170, damping: 22, mass: 0.6 } as const;
const PRESS_SPRING = { stiffness: 420, damping: 28, mass: 0.4 } as const;
const DIAMETER = 72;
const IDLE_SCALE = 28 / DIAMETER;

export function CursorHalo() {
  const x = useMotionValue(-200);
  const y = useMotionValue(-200);
  const hover = useMotionValue(0);
  const press = useMotionValue(1);
  const [label, setLabel] = useState("");

  const sx = useSpring(x, SPRING);
  const sy = useSpring(y, SPRING);
  const grown = useSpring(hover, SPRING);
  const pressed = useSpring(press, PRESS_SPRING);
  const scale = useTransform(
    [grown, pressed],
    ([g, p]: number[]) => (IDLE_SCALE + (1 - IDLE_SCALE) * g) * p,
  );

  useEffect(() => {
    const onMove = (event: PointerEvent) => {
      x.set(event.clientX);
      y.set(event.clientY);
      const target = event.target instanceof Element ? event.target.closest("[data-cursor]") : null;
      hover.set(target ? 1 : 0);
      setLabel(target?.getAttribute("data-cursor-label") ?? "");
    };
    const onDown = () => press.set(0.85);
    const onUp = () => press.set(1);

    window.addEventListener("pointermove", onMove, { passive: true });
    window.addEventListener("pointerdown", onDown, { passive: true });
    window.addEventListener("pointerup", onUp, { passive: true });
    return () => {
      window.removeEventListener("pointermove", onMove);
      window.removeEventListener("pointerdown", onDown);
      window.removeEventListener("pointerup", onUp);
    };
  }, [x, y, hover, press]);

  return (
    <>
      <motion.div
        aria-hidden="true"
        style={{
          x: sx,
          y: sy,
          scale,
          width: DIAMETER,
          height: DIAMETER,
          marginLeft: -DIAMETER / 2,
          marginTop: -DIAMETER / 2,
        }}
        className="pointer-events-none fixed top-0 left-0 z-[80] rounded-full bg-white mix-blend-difference"
      />
      <motion.div
        aria-hidden="true"
        style={{ x: sx, y: sy, marginLeft: -40, marginTop: -8, width: 80 }}
        animate={{ opacity: label ? 1 : 0 }}
        transition={{ duration: 0.16, ease: [0.23, 1, 0.32, 1] }}
        className="pointer-events-none fixed top-0 left-0 z-[81] text-center text-[11px] leading-4 font-semibold tracking-[0.08em] whitespace-nowrap text-white uppercase mix-blend-difference"
      >
        {label}
      </motion.div>
    </>
  );
}
