import { motion, useReducedMotion } from "framer-motion";
import { QrCode } from "lucide-react";
import { EASE_OUT } from "@/lib/motion";
import { useLocale } from "@/lib/strings";
import { Pill } from "../components/Pill";
import { landingToast } from "../components/LandingToast";
import { landingStrings } from "../strings";

/**
 * L6 — closing. The headline rises character by character out of its own mask, once, when the
 * block enters (not scrubbed). Reduced motion fades instead of rising.
 */
export function Outro() {
  useLocale();
  const s = landingStrings;
  const reduceMotion = useReducedMotion();
  const chars = Array.from(s("outro.title"));

  return (
    <section id="download" className="scroll-mt-[72px] bg-d-canvas pt-24 pb-20 text-d-ink lg:pt-32">
      <div className="mx-auto max-w-[1000px] px-5 text-center lg:px-10">
        <motion.h2
          className="flex flex-wrap justify-center text-[clamp(2.1rem,6vw,4.6rem)] leading-[1.06] font-semibold tracking-[-0.03em]"
          initial="hidden"
          whileInView="shown"
          viewport={{ once: true, amount: 0.5 }}
          variants={{ shown: { transition: { staggerChildren: reduceMotion ? 0 : 0.045 } } }}
        >
          {chars.map((char, index) => (
            <span key={`${char}-${index}`} className="inline-block overflow-hidden py-[0.06em]">
              <motion.span
                className="inline-block"
                variants={{
                  hidden: reduceMotion ? { opacity: 0 } : { y: "110%" },
                  shown: reduceMotion ? { opacity: 1 } : { y: "0%" },
                }}
                transition={{ duration: reduceMotion ? 0.2 : 0.66, ease: EASE_OUT }}
              >
                {char === " " ? "\u00A0" : char}
              </motion.span>
            </span>
          ))}
        </motion.h2>

        <motion.div
          initial={{ opacity: 0, y: reduceMotion ? 0 : 12 }}
          whileInView={{ opacity: 1, y: 0 }}
          viewport={{ once: true, amount: 0.6 }}
          transition={{
            duration: reduceMotion ? 0.2 : 0.5,
            delay: reduceMotion ? 0 : 0.3,
            ease: EASE_OUT,
          }}
          className="mt-12 flex flex-col items-center gap-6"
        >
          <div className="flex flex-wrap items-center justify-center gap-3">
            <Pill href="#/app" cursorLabel={s("hero.cursor")}>
              {s("outro.cta")}
            </Pill>
            <Pill variant="grey" onClick={() => landingToast(s("soon.download"))}>
              {s("outro.download")}
            </Pill>
          </div>

          <div className="flex flex-col items-center gap-3">
            <div className="flex h-[92px] w-[92px] items-center justify-center rounded-xl border border-dashed border-d-ink-3/70 text-d-ink-3">
              <QrCode size={34} strokeWidth={1.5} aria-hidden="true" />
            </div>
            <span className="text-[12px] text-d-ink-3">{s("outro.qr")}</span>
          </div>

          <span className="text-[12px] text-d-ink-3">{s("outro.downloadNote")}</span>
        </motion.div>
      </div>
    </section>
  );
}
