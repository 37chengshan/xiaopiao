import { useReducedMotion } from "framer-motion";
import { useEffect, useState } from "react";

/** Streams text in character by character (skipped for reduced motion or already-seen messages). */
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
    }, 28);
    return () => clearInterval(id);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [text, instant]);
  return (
    <span>
      {text.slice(0, n)}
      {n < text.length && <span className="ml-0.5 inline-block h-4 w-0.5 translate-y-0.5 animate-pulse bg-ink-2" aria-hidden />}
    </span>
  );
}
