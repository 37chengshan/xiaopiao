import { useEffect } from "react";

const EASE = "cubic-bezier(0.23, 1, 0.32, 1)";

/**
 * While a modal moment is on, the page behind it steps back (scale 0.97, docs/DESIGN.md §7.1) and goes inert.
 * `column` is the phone column; its `main` and tab bar are the layers pushed back.
 */
export function usePushBack(column: HTMLElement | null, active: boolean, reduce: boolean) {
  useEffect(() => {
    if (!column || !active) return;
    const layers = [...column.children].filter((c): c is HTMLElement => c.tagName === "MAIN" || c.tagName === "NAV");
    layers.forEach((el) => {
      el.inert = true;
    });
    const ins = reduce ? [] : layers.map((el) => el.animate([{ transform: "scale(1)" }, { transform: "scale(0.97)" }], { duration: 280, easing: EASE, fill: "forwards" }));
    return () => {
      ins.forEach((a) => a.cancel());
      layers.forEach((el) => {
        el.inert = false;
        if (!reduce) el.animate([{ transform: "scale(0.97)" }, { transform: "scale(1)" }], { duration: 200, easing: EASE });
      });
    };
  }, [column, active, reduce]);
}

/** The phone column that hosts `from` (the app shell's `main` sits directly inside it), or null outside the shell. */
export function phoneColumnOf(from: Element | null): HTMLElement | null {
  return from?.closest("main")?.parentElement ?? null;
}
