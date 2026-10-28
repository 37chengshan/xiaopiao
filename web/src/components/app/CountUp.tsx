import { animate, useReducedMotion } from "framer-motion";
import { useEffect, useRef, useState, type ComponentProps } from "react";
import { DUR, EASE_OUT } from "@/lib/motion";
import { Money } from "./kit";

/**
 * KPI count-up: ≤600ms ease-out, tabular figures via `Money`/`.num`. The first paint shows the real value,
 * so a total never flashes a zero; only later changes roll (e.g. data arriving after the skeleton).
 */
export function useCountUp(target: number, seconds: number = DUR.count): number {
  const reduce = useReducedMotion();
  const [value, setValue] = useState(target);
  const shown = useRef(target);

  useEffect(() => {
    if (reduce || shown.current === target) {
      shown.current = target;
      setValue(target);
      return;
    }
    const controls = animate(shown.current, target, {
      duration: seconds,
      ease: EASE_OUT,
      onUpdate: (v) => {
        shown.current = Math.round(v);
        setValue(shown.current);
      },
    });
    return () => controls.stop();
  }, [target, seconds, reduce]);

  return value;
}

/** `Money` with a count-up on change. */
export function CountUpMoney({ cents, ...rest }: { cents: number } & Omit<ComponentProps<typeof Money>, "cents">) {
  return <Money cents={useCountUp(cents)} {...rest} />;
}
