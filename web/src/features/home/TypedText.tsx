import { useReducedMotion } from "framer-motion";
import { useEffect, useState } from "react";

/** Streams text in quietly (skipped for reduced motion or already-seen messages). */
export function TypedText({ text, skip, onDone }: { text: string; skip: boolean; onDone?: () => void }) {
  const reduce = useReducedMotion();
  const instant = skip || reduce;
  const [n, setN] = useState(instant ? text.length : 0);
  useEffect(() => {
    if (instant) {
      onDone?.();
      return;
    }
    let i = 0;
    const id = setInterval(() => {
      i += 1;
      setN(i);
      if (i >= text.length) {
        clearInterval(id);
        onDone?.();
      }
    }, 18);
    return () => clearInterval(id);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [text, instant]);
  return (
    <span>
      {text.slice(0, n)}
      {n < text.length && <span className="ml-px inline-block h-[1.05em] w-px translate-y-[0.2em] bg-ink-3" aria-hidden />}
    </span>
  );
}
