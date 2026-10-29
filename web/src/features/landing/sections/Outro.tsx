import { useLocale } from "@/lib/strings";
import { s } from "../strings";
import { SplitText, useInViewOnce } from "../lib/motion";

/** L7 — the close: one big sentence, two actions, and the reminder that the pictures are generated. */
export function Outro() {
  const { ref, inView } = useInViewOnce(0.25);
  useLocale();
  return (
    <section ref={ref} className="relative mx-auto w-full max-w-[1320px] px-4 pb-24 pt-24 sm:px-6 lg:pb-32 lg:pt-32">
      <h2 className="lp-outro-title max-w-[16ch]">
        <SplitText as="span" text={s("outro.title.a")} active={inView} />
        <SplitText as="span" text={s("outro.title.b")} active={inView} base={110} index={3} className="text-[var(--lp-ink-2)]" />
      </h2>
      <p className="mt-6 max-w-[46ch] text-[16px] leading-[1.75] text-[var(--lp-ink)]">{s("outro.body")}</p>
      <div className="mt-8 flex flex-wrap items-center gap-3">
        <a
          href="#/app"
          className="inline-flex h-12 items-center rounded-full bg-[var(--lp-ink)] px-6 text-[16px] font-medium text-[var(--lp-paper)] transition-transform duration-200 hover:-translate-y-0.5 active:scale-[0.98]"
        >
          {s("outro.cta")}
        </a>
        <button
          type="button"
          disabled
          title={s("outro.soon")}
          className="inline-flex h-12 cursor-not-allowed items-center rounded-full border border-[var(--lp-line)] px-5 text-[14px] font-medium text-[var(--lp-ink-3)]"
        >
          {s("outro.download")}
        </button>
        <span className="text-[13px] text-[var(--lp-ink-3)]">{s("outro.soon")}</span>
      </div>
      <p className="mt-10 text-[13px] text-[var(--lp-ink-3)]">{s("outro.credit")}</p>
    </section>
  );
}
