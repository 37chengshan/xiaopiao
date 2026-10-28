import { motion, useInView, useReducedMotion } from "framer-motion";
import { CreditCard, Receipt, Sparkles, Users, Zap, type LucideIcon } from "lucide-react";
import { useEffect, useRef, useState, type ComponentType } from "react";
import { useLocale } from "@/lib/strings";
import { cn } from "@/lib/utils";
import { AiChat, Circle, PhoneNotify, ReceiptStamp, Wallet } from "../illustrations";
import { useDesktop } from "../hooks/useMediaQuery";
import { landingStrings } from "../strings";

interface Card {
  key: "c1" | "c2" | "c3" | "c4" | "c5";
  Icon: LucideIcon;
  Art: ComponentType<{ className?: string }>;
}

const CARDS: Card[] = [
  { key: "c1", Icon: Zap, Art: PhoneNotify },
  { key: "c2", Icon: Receipt, Art: ReceiptStamp },
  { key: "c3", Icon: Sparkles, Art: AiChat },
  { key: "c4", Icon: Users, Art: Circle },
  { key: "c5", Icon: CreditCard, Art: Wallet },
];

/**
 * L3 — accordion cards. Hover / focus / click expands one card to ~50% (`flex-grow` 1→4 on a
 * spring). On first entering the viewport it walks through all five (900ms each) and stops back on
 * the first; any interaction ends the walk. Below 1024px the row turns into a vertical accordion.
 */
export function Features() {
  useLocale();
  const s = landingStrings;
  const reduceMotion = useReducedMotion();
  const desktop = useDesktop();
  const [active, setActive] = useState(0);
  const [autoplay, setAutoplay] = useState(true);
  const rowRef = useRef<HTMLDivElement>(null);
  const inView = useInView(rowRef, { once: true, amount: 0.5 });

  useEffect(() => {
    if (!inView || !autoplay || reduceMotion) return;
    let step = 0;
    const id = window.setInterval(() => {
      step += 1;
      setActive(step >= CARDS.length ? 0 : step);
      if (step >= CARDS.length) window.clearInterval(id);
    }, 900);
    return () => window.clearInterval(id);
  }, [inView, autoplay, reduceMotion]);

  const stop = () => {
    if (autoplay) setAutoplay(false);
  };

  return (
    <section id="features" className="scroll-mt-[72px] bg-d-canvas text-d-ink">
      <div className="mx-auto max-w-[1200px] px-5 py-24 lg:px-10 lg:py-28">
        <p className="text-[12px] font-semibold tracking-[0.16em] text-d-ink-3 uppercase">
          {s("feat.eyebrow")}
        </p>
        <h2 className="mt-3 max-w-[620px] text-[clamp(1.9rem,3vw,2.75rem)] leading-[1.12] font-semibold tracking-[-0.02em] text-balance">
          {s("feat.title")}
        </h2>

        <div
          ref={rowRef}
          className={cn("mt-12 flex gap-3", desktop ? "h-[520px] flex-row" : "h-[660px] flex-col")}
        >
          {CARDS.map((card, index) => {
            const expanded = active === index;
            return (
              <motion.div
                key={card.key}
                role="button"
                tabIndex={0}
                aria-expanded={expanded}
                aria-label={s(`feat.${card.key}.title` as Parameters<typeof s>[0])}
                layout
                animate={{ flexGrow: expanded ? 4 : 1 }}
                transition={
                  reduceMotion ? { duration: 0.2 } : { type: "spring", stiffness: 260, damping: 30 }
                }
                style={{ flexBasis: 0, minWidth: 0, minHeight: 0 }}
                onPointerEnter={() => {
                  stop();
                  setActive(index);
                }}
                onFocus={() => {
                  stop();
                  setActive(index);
                }}
                onClick={() => {
                  stop();
                  setActive(index);
                }}
                onKeyDown={(event) => {
                  if (event.key === "Enter" || event.key === " ") {
                    event.preventDefault();
                    stop();
                    setActive(index);
                  }
                }}
                data-cursor="card"
                data-cursor-label={s("feat.cursor")}
                className={cn(
                  "relative cursor-pointer overflow-hidden rounded-2xl border bg-d-surface",
                  expanded ? "border-d-ink/20" : "border-d-hairline",
                )}
              >
                {/* collapsed face: icon + sideways title */}
                <motion.div
                  aria-hidden={expanded}
                  animate={{ opacity: expanded ? 0 : 1 }}
                  transition={{ duration: 0.18 }}
                  className={cn(
                    "absolute inset-0 flex p-5",
                    desktop
                      ? "flex-col items-center justify-end gap-5"
                      : "flex-row items-center justify-between",
                  )}
                >
                  <card.Icon size={18} strokeWidth={1.75} className="text-d-ink-2" />
                  <span
                    className={cn(
                      "text-[13px] font-semibold whitespace-nowrap text-d-ink",
                      desktop ? "landing-vertical-label tracking-[0.08em]" : "tracking-[0.02em]",
                    )}
                  >
                    {s(`feat.${card.key}.title` as Parameters<typeof s>[0])}
                  </span>
                  {desktop ? null : <span className="block h-[18px] w-[18px]" />}
                </motion.div>

                {/* expanded face */}
                <motion.div
                  aria-hidden={!expanded}
                  animate={{ opacity: expanded ? 1 : 0, y: expanded ? 0 : 10 }}
                  transition={{ duration: reduceMotion ? 0.2 : 0.32, ease: [0.23, 1, 0.32, 1] }}
                  className="flex h-full flex-col items-stretch justify-center gap-5 p-6"
                >
                  <card.Art className="h-[150px] w-full text-d-ink lg:h-[250px]" />
                  <div>
                    <h3 className="text-[19px] font-semibold tracking-[-0.01em]">
                      {s(`feat.${card.key}.title` as Parameters<typeof s>[0])}
                    </h3>
                    <p className="mt-2 text-[14px] leading-relaxed text-d-ink-2">
                      {s(`feat.${card.key}.a` as Parameters<typeof s>[0])}
                      <br />
                      {s(`feat.${card.key}.b` as Parameters<typeof s>[0])}
                    </p>
                    <span className="mt-3 inline-flex items-center gap-1 text-[13px] font-medium text-d-accent">
                      {s("feat.link")}
                      <span aria-hidden="true">→</span>
                    </span>
                  </div>
                </motion.div>
              </motion.div>
            );
          })}
        </div>
      </div>
    </section>
  );
}
