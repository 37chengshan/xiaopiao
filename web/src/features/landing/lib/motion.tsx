import { createContext, useContext, useEffect, useRef, useState, type ElementType, type ReactNode } from "react";
import { cn } from "@/lib/utils";

/** Landing motion primitives (WEB-SPEC §L). Everything is transform/opacity only. */

export function usePrefersReducedMotion(): boolean {
  // Read the query during the first render: starting at `false` and correcting in an effect made a
  // scroll-driven hero flash its closed state before settling.
  const [reduce, setReduce] = useState(() =>
    typeof window === "undefined" ? false : window.matchMedia("(prefers-reduced-motion: reduce)").matches,
  );
  useEffect(() => {
    const mq = window.matchMedia("(prefers-reduced-motion: reduce)");
    const on = () => setReduce(mq.matches);
    on();
    mq.addEventListener("change", on);
    return () => mq.removeEventListener("change", on);
  }, []);
  return reduce;
}

/** One-shot in-view flag (threshold 0.18 + a safety sweep, like the reference pages). */
export function useInViewOnce<T extends HTMLElement = HTMLElement>(threshold = 0.18) {
  const ref = useRef<T | null>(null);
  const [inView, setInView] = useState(false);

  useEffect(() => {
    const el = ref.current;
    if (!el) return;
    const reveal = () => setInView(true);
    if (typeof IntersectionObserver === "undefined") {
      reveal();
      return;
    }
    const io = new IntersectionObserver(
      (entries) => {
        for (const entry of entries) {
          if (entry.isIntersecting) {
            reveal();
            io.disconnect();
          }
        }
      },
      { threshold, rootMargin: "0px 0px -6% 0px" },
    );
    io.observe(el);
    // Safety: anything already on screen reveals even if the observer is late.
    const raf = requestAnimationFrame(() => {
      const rect = el.getBoundingClientRect();
      if (rect.top < window.innerHeight * 0.94 && rect.bottom > 0) reveal();
    });
    return () => {
      io.disconnect();
      cancelAnimationFrame(raf);
    };
  }, [threshold]);

  return { ref, inView };
}

/**
 * Word-by-word mask reveal (velia). Each word rises out of its own overflow-hidden line;
 * `--i` staggers by 70ms and `--base` shifts the whole block.
 */
export function SplitText({
  text,
  as: Tag = "span",
  className,
  active = true,
  base = 0,
  index = 0,
}: {
  text: string;
  as?: ElementType;
  className?: string;
  active?: boolean;
  base?: number;
  index?: number;
}) {
  const words = text.split(/(\s+)/).filter((w) => w !== "");
  let wordIndex = index;
  return (
    <Tag className={cn("lp-split", active && "is-in", className)} style={{ "--base": `${base}ms` } as React.CSSProperties}>
      {words.map((word, i) => {
        if (/^\s+$/.test(word)) return <span key={`s${i}`}> </span>;
        const idx = wordIndex++;
        return (
          <span key={`w${i}`} className="w">
            <span style={{ "--i": idx } as React.CSSProperties}>{word}</span>
          </span>
        );
      })}
    </Tag>
  );
}

/** Fade + 18px rise, revealed once when it enters the viewport (`--d` staggers). */
export function Reveal({
  children,
  delay = 0,
  className,
  as: Tag = "div",
}: {
  children: ReactNode;
  delay?: number;
  className?: string;
  as?: ElementType;
}) {
  const { ref, inView } = useInViewOnce();
  return (
    <Tag ref={ref as never} className={cn("lp-reveal", inView && "is-in", className)} style={{ "--d": delay } as React.CSSProperties}>
      {children}
    </Tag>
  );
}

/**
 * One clock for every live bento card (maya-lind-bento). 30fps is plenty for these micro UIs and
 * keeps six cards from re-rendering at 60.
 */
const ClockContext = createContext(0);

export function ClockProvider({ children }: { children: ReactNode }) {
  const [now, setNow] = useState(0);
  useEffect(() => {
    let raf = 0;
    let last = 0;
    const start = performance.now();
    const tick = (t: number) => {
      if (t - last > 33) {
        last = t;
        setNow((t - start) / 1000);
      }
      raf = requestAnimationFrame(tick);
    };
    raf = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(raf);
  }, []);
  return <ClockContext.Provider value={now}>{children}</ClockContext.Provider>;
}

export function useClock(): number {
  return useContext(ClockContext);
}
