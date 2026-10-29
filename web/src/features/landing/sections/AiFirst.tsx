import { useEffect, useRef, useState } from "react";
import { useLocale } from "@/lib/strings";
import { s } from "../strings";
import { Reveal, useInViewOnce, usePrefersReducedMotion } from "../lib/motion";

const LINE = {
  zh: "今天你花了 86，比上周同一天少 20。要我打出来吗？",
  en: "You spent 86 today, 20 less than the same day last week. Shall I print it?",
};

/** L5 — the assistant speaks first (mainframe): dark band, blurred lead-in, a typewriter line, three pills. */
export function AiFirst() {
  const { ref, inView } = useInViewOnce(0.3);
  const reduce = usePrefersReducedMotion();
  const locale = useLocale();
  const line = LINE[locale] ?? LINE.zh;
  const [typed, setTyped] = useState(reduce ? line.length : 0);
  const timer = useRef<number | null>(null);

  useEffect(() => {
    if (!inView || reduce) {
      setTyped(line.length);
      return;
    }
    let i = 0;
    setTyped(0);
    const id = window.setInterval(() => {
      i += 1;
      setTyped(i);
      if (i >= line.length) window.clearInterval(id);
    }, 38);
    timer.current = id;
    return () => window.clearInterval(id);
  }, [inView, reduce, line]);

  const go = () => {
    window.location.hash = "/app/ai";
  };

  return (
    <section id="ai" ref={ref} className="lp-ai-band relative overflow-hidden py-24 lg:py-32">
      <div className="mx-auto grid w-full max-w-[1320px] gap-12 px-4 sm:px-6 lg:grid-cols-[minmax(0,1fr)_minmax(0,0.62fr)]">
        <div>
          <p className="text-[13px] font-semibold uppercase tracking-[0.16em] text-[rgba(244,239,230,0.55)]">{s("ai.eyebrow")}</p>
          <div className="lp-ai-blur mt-5 max-w-[30ch] text-[clamp(20px,2.4vw,30px)] leading-[1.3]" aria-hidden>
            <p>{s("ai.intro1")}</p>
            <p>{s("ai.intro2")}</p>
          </div>
          <p className="lp-ai-line mt-5 max-w-[34ch] text-[clamp(26px,3.4vw,46px)] font-semibold leading-[1.18] tracking-[-0.02em]">
            {line.slice(0, typed)}
            {typed < line.length && <span className="lp-cursor" aria-hidden />}
          </p>
          <div className="mt-8 flex flex-wrap gap-2.5">
            {(["ai.chip1", "ai.chip2", "ai.chip3"] as const).map((key) => (
              <button key={key} type="button" onClick={go} className="lp-ai-pill">
                {s(key)}
              </button>
            ))}
          </div>
          <p className="mt-4 text-[13px] text-[rgba(244,239,230,0.5)]">{s("ai.hint")}</p>
        </div>

        <Reveal delay={2} className="self-end">
          <div className="rounded-3xl border border-white/10 bg-white/[0.04] p-6">
            <p className="text-[13px] leading-[1.9] text-[rgba(244,239,230,0.72)]">{s("ai.rule")}</p>
            <div className="mt-5 flex items-center gap-2 text-[13px] text-[rgba(244,239,230,0.4)]">
              <span className="size-1.5 rounded-full bg-[var(--lp-orange)]" aria-hidden />
              {s("ai.engine")}
            </div>
          </div>
        </Reveal>
      </div>

      <div className="pointer-events-none absolute inset-x-0 bottom-0 h-24 bg-[linear-gradient(180deg,transparent,rgba(16,15,19,0.9))]" aria-hidden />
    </section>
  );
}
