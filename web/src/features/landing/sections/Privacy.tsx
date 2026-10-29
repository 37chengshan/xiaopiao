import { useLocale } from "@/lib/strings";
import { s } from "../strings";
import { IMG } from "../lib/assets";
import { Reveal, SplitText, useInViewOnce } from "../lib/motion";

/** L6 — privacy, fully inverted: one photograph of a held receipt and four promises. */
export function Privacy() {
  const { ref, inView } = useInViewOnce(0.2);
  useLocale();

  const points = [
    { title: "privacy.p1.title", body: "privacy.p1.body" },
    { title: "privacy.p2.title", body: "privacy.p2.body" },
    { title: "privacy.p3.title", body: "privacy.p3.body" },
    { title: "privacy.p4.title", body: "privacy.p4.body" },
  ] as const;

  return (
    <section id="privacy" ref={ref} className="lp-dark-band relative overflow-hidden py-24 lg:py-32">
      <div className="mx-auto grid w-full max-w-[1320px] gap-12 px-4 sm:px-6 lg:grid-cols-[minmax(0,0.8fr)_minmax(0,1.06fr)] lg:items-center">
        <Reveal delay={0}>
          <figure className="relative overflow-hidden rounded-[28px] border border-white/10">
            <img src={IMG.handReceipt} alt={s("privacy.title")} loading="lazy" className="aspect-[4/5] w-full object-cover" />
            <figcaption className="absolute inset-x-0 bottom-0 bg-[linear-gradient(180deg,transparent,rgba(9,9,11,0.82))] p-4 text-[13px] text-[rgba(244,239,230,0.72)]">
              {s("privacy.body")}
            </figcaption>
          </figure>
        </Reveal>

        <div>
          <p className="text-[13px] font-semibold uppercase tracking-[0.16em] text-[rgba(244,239,230,0.5)]">{s("privacy.eyebrow")}</p>
          <h2 className="mt-3 max-w-[15ch] text-[clamp(28px,3.2vw,46px)] font-semibold leading-[1.08] tracking-[-0.03em]">
            <SplitText as="span" text={s("privacy.title")} active={inView} />
          </h2>
          <ul className="mt-8 space-y-6">
            {points.map((point, i) => (
              <Reveal key={point.title} delay={i + 1} as="li" className="flex gap-4">
                <span aria-hidden className="mt-[7px] size-2 shrink-0 rounded-full bg-[var(--lp-orange)]" />
                <div>
                  <h3 className="text-[16px] font-semibold">{s(point.title)}</h3>
                  <p className="mt-1.5 max-w-[46ch] text-[14px] leading-[1.75] text-[rgba(244,239,230,0.66)]">{s(point.body)}</p>
                </div>
              </Reveal>
            ))}
          </ul>
        </div>
      </div>
    </section>
  );
}
