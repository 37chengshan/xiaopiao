import { useEffect, useState } from "react";
import { fmtMoney } from "./receiptModel";

const LINE = 1.12; // em; every digit cell is one line tall

function Digit({ digit, rolled, delay, instant }: { digit: number; rolled: boolean; delay: number; instant: boolean }) {
  return (
    <span className="relative block overflow-hidden" style={{ height: `${LINE}em`, lineHeight: `${LINE}em` }} aria-hidden>
      <span
        className="block"
        style={{
          transform: `translateY(${rolled ? -digit * 10 : 0}%)`,
          transition: instant ? "none" : `transform 760ms var(--ease-out) ${delay}ms`,
        }}
      >
        {Array.from({ length: 10 }, (_, i) => (
          <span key={i} className="block" style={{ height: `${LINE}em` }}>
            {i}
          </span>
        ))}
      </span>
    </span>
  );
}

/** Odometer-style total: each digit column rolls from 0 to its value when `run` flips true. */
export function NumberTicker({ cents, run, instant, className }: { cents: number; run: boolean; instant: boolean; className?: string }) {
  const text = fmtMoney(cents);
  const [rolled, setRolled] = useState(instant);
  useEffect(() => {
    if (instant || !run) {
      setRolled(instant || run);
      return;
    }
    const id = requestAnimationFrame(() => requestAnimationFrame(() => setRolled(true)));
    return () => cancelAnimationFrame(id);
  }, [run, instant]);

  const chars = [...text];
  return (
    <span className={`${className ?? ""} flex items-baseline justify-center`} role="img" aria-label={`合计 ${text} 元`}>
      <span className="mr-[0.1em] text-[0.55em] text-paper-ink/70" aria-hidden>
        ¥
      </span>
      {chars.map((ch, i) =>
        /\d/.test(ch) ? (
          <Digit key={i} digit={Number(ch)} rolled={rolled} delay={(chars.length - 1 - i) * 45} instant={instant} />
        ) : (
          <span key={i} aria-hidden style={{ lineHeight: `${LINE}em` }}>
            {ch}
          </span>
        ),
      )}
    </span>
  );
}
