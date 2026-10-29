import { useEffect, useRef, useState } from "react";
import { useLocale } from "@/lib/strings";
import { s } from "../strings";
import { IMG } from "../lib/assets";
import { SplitText, useInViewOnce, usePrefersReducedMotion } from "../lib/motion";

const REPLY = {
  zh: "记好了，午饭 32。这周吃饭 186，比上周少 24。",
  en: "Logged lunch, 32. Food this week: 186, 24 less than last week.",
};

/**
 * L2 — live demo (velia hero-card): a real photo of a night desk with a thermal printer, and a
 * frosted panel floating on it. Left: payment notifications become entries; right: the receipt
 * prints line by line and lands a stamp. The panel keeps one height while text types.
 */
export function Demo() {
  const { ref, inView } = useInViewOnce(0.2);
  const reduce = usePrefersReducedMotion();
  const locale = useLocale();
  const [step, setStep] = useState(reduce ? 9 : 0);
  const [typed, setTyped] = useState(reduce ? REPLY[locale].length : 0);
  const timers = useRef<number[]>([]);

  const reply = REPLY[locale] ?? REPLY.zh;

  useEffect(() => {
    setTyped((n) => (reduce ? reply.length : Math.min(n, reply.length)));
  }, [locale, reduce, reply.length]);

  useEffect(() => {
    if (!inView || reduce) return;
    const at = (ms: number, fn: () => void) => timers.current.push(window.setTimeout(fn, ms));
    at(200, () => setStep(1));
    at(700, () => setStep(2));
    at(900, () => {
      let i = 0;
      const id = window.setInterval(() => {
        i += 2;
        setTyped(Math.min(i, reply.length));
        if (i >= reply.length) window.clearInterval(id);
      }, 26);
      timers.current.push(id);
    });
    at(1400, () => setStep(3));
    at(1700, () => setStep(4));
    at(2000, () => setStep(5));
    at(2300, () => setStep(6));
    at(2600, () => setStep(7));
    at(2900, () => setStep(8));
    return () => {
      timers.current.forEach((t) => {
        window.clearTimeout(t);
        window.clearInterval(t);
      });
      timers.current = [];
    };
  }, [inView, reduce, reply.length]);

  const rows = [
    { label: s("demo.receipt.r1"), amount: "32.00" },
    { label: s("demo.receipt.r2"), amount: "11.50" },
    { label: s("demo.receipt.r3"), amount: "4.00" },
    { label: s("demo.receipt.r4"), amount: "18.00" },
  ];

  return (
    <section id="demo" ref={ref} className="relative mx-auto w-full max-w-[1320px] px-4 py-24 sm:px-6 lg:py-32">
      <div className="grid gap-10 lg:grid-cols-[minmax(0,0.46fr)_minmax(0,1fr)] lg:items-start">
        <div className="lg:sticky lg:top-28">
          <p className="lp-card-kicker">{s("demo.eyebrow")}</p>
          <h2 className="mt-3 text-[clamp(24px,3.1vw,44px)] font-semibold leading-[1.1] tracking-[-0.03em]">
            <SplitText as="span" text={s("demo.title")} active={inView} />
          </h2>
          <p className="mt-5 max-w-[42ch] text-[16px] leading-[1.75] text-[var(--lp-ink)]">{s("demo.body")}</p>
        </div>

        <div className="lp-demo-card" role="img" aria-label={s("demo.panel")}>
          <img src={IMG.nightDesk} alt="" aria-hidden />
          <div className="absolute inset-0 bg-[linear-gradient(180deg,rgba(20,16,12,0.18),rgba(20,16,12,0.42))]" aria-hidden />
          <div className="lp-glass">
            <div className="lp-chat">
              <p className="lp-card-kicker !text-[rgba(42,37,29,0.6)]">{s("demo.chat.title")}</p>
              <div className="mt-3 flex flex-col gap-3">
                {step >= 1 && (
                  <div className="lp-bubble" style={{ animation: "lp-rise .5s cubic-bezier(.22,1,.36,1) both" }}>
                    {s("demo.msg1")}
                  </div>
                )}
                {step >= 2 && (
                  <div className="lp-ai">
                    <span className="lp-ai-mark" aria-hidden>
                      票
                    </span>
                    <p className={typed < reply.length ? "lp-typing" : undefined}>
                      {reply.slice(0, reduce ? reply.length : typed)}
                    </p>
                  </div>
                )}
                {step >= 3 && (
                  <div className="lp-bubble" style={{ animation: "lp-rise .5s cubic-bezier(.22,1,.36,1) both" }}>
                    {s("demo.msg3")}
                  </div>
                )}
                <p className="mt-1 text-[13px] text-[rgba(42,37,29,0.55)]">
                  {step >= 7 ? s("demo.done") : s("demo.printing")}
                </p>
              </div>
            </div>

            <div className="lp-ticket">
              <div className="lp-mini">
                <p className="text-center text-[10px] font-semibold tracking-[0.28em]">今日小票</p>
                <p className="mt-1 text-center text-[8px] opacity-60">{s("demo.receipt.date")}</p>
                <span className="lp-mini-rule" />
                {rows.map((row, i) => (
                  <div
                    key={row.label}
                    className="lp-mini-row"
                    style={{ opacity: step >= 4 + i ? 1 : 0, transform: step >= 4 + i ? "none" : "translateY(4px)", transition: "opacity .35s, transform .35s" }}
                  >
                    <span className="truncate opacity-80">{row.label}</span>
                    <span className="num shrink-0">{row.amount}</span>
                  </div>
                ))}
                <span className="lp-mini-rule" />
                <div className="lp-mini-row !text-[13px] font-semibold">
                  <span>{s("demo.receipt.total")}</span>
                  <span className="num">{step >= 8 ? "65.50" : "—"}</span>
                </div>
                <div className="mt-2 flex justify-end">
                  <span className="lp-stamp" style={{ opacity: step >= 8 ? 1 : 0, transform: step >= 8 ? "rotate(-6deg) scale(1)" : "rotate(-6deg) scale(1.5)", transition: "opacity .25s, transform .35s cubic-bezier(.22,1,.36,1)" }}>
                    {s("demo.receipt.stamp")}
                  </span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}
