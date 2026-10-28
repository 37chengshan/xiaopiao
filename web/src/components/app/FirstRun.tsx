import { AnimatePresence, motion } from "framer-motion";
import { BellRing, Check, EyeOff, Lock, ShieldCheck, Sparkles, type LucideIcon } from "lucide-react";
import { useState } from "react";
import { type ThemePref, useTheme } from "@/lib/theme";
import { t, type StringKey } from "@/lib/strings";
import { cn } from "@/lib/utils";
import { useReceiptSound } from "@/features/receipt/soundPref";
import { useLedger } from "@/services/LedgerProvider";
import { Segmented, Switch, XButton, type Tone, TONE_CLASS } from "./kit";

/** A tiny receipt strip: paper, torn edge, a few faint lines. Calm, no colour. */
function PaperMark() {
  return (
    <div className="zigzag-bottom mx-auto w-20 bg-paper px-3.5 pb-5 pt-5 shadow-[0_8px_22px_-8px_oklch(0_0_0/30%)]" aria-hidden>
      <div className="mx-auto h-[4px] w-8 rounded-full bg-paper-ink/35" />
      <div className="mt-3.5 space-y-2">
        <div className="h-[3px] rounded-full bg-paper-ink/15" />
        <div className="h-[3px] w-4/5 rounded-full bg-paper-ink/15" />
        <div className="h-[3px] w-3/5 rounded-full bg-paper-ink/15" />
      </div>
    </div>
  );
}

function BigIcon({ icon: Icon, tone }: { icon: LucideIcon; tone: Tone }) {
  return (
    <span className={cn("mx-auto grid size-24 place-items-center rounded-[28px] shadow-[inset_0_1px_0_oklch(1_0_0/40%)]", TONE_CLASS[tone])} aria-hidden>
      <Icon className="size-11" strokeWidth={1.6} />
    </span>
  );
}

const THEMES: readonly { value: ThemePref; label: string }[] = [
  { value: "system", label: "跟随系统" },
  { value: "light", label: "浅色" },
  { value: "dark", label: "深色" },
];

const BULLETS: { icon: LucideIcon; key: StringKey }[] = [
  { icon: Lock, key: "ob.3.b1" },
  { icon: EyeOff, key: "ob.3.b2" },
  { icon: Check, key: "ob.3.b3" },
];

const TOTAL = 4;

/**
 * Intro tour: value -> auto logging -> data floor -> look and sound. Skippable.
 * First run (`onFinish` omitted) ends with the two mode choices; a replay from 我的 ends with 完成.
 */
export function Onboarding({ onFinish }: { onFinish?: () => void }) {
  const { chooseMode } = useLedger();
  const { pref, setPref } = useTheme();
  const [sound, setSound] = useReceiptSound();
  const [i, setI] = useState(0);
  const [dir, setDir] = useState(1);
  const last = i === TOTAL - 1;
  const go = (n: number) => {
    setDir(n > i ? 1 : -1);
    setI(n);
  };

  return (
    <div className="flex min-h-full flex-col px-6 pb-[max(1.5rem,env(safe-area-inset-bottom))] pt-[max(1rem,env(safe-area-inset-top))]">
      <div className="flex h-10 items-center justify-end">
        {!last && (
          <button type="button" onClick={() => (onFinish ? onFinish() : go(TOTAL - 1))} className="-mr-2 px-2 py-2 text-[15px] text-ink-2 active:opacity-60">
            {t("ob.skip")}
          </button>
        )}
      </div>

      <div className="relative flex flex-1 flex-col items-center justify-center overflow-hidden">
        <AnimatePresence mode="wait" initial={false} custom={dir}>
          <motion.section
            key={i}
            custom={dir}
            initial={{ opacity: 0, x: 28 * dir }}
            animate={{ opacity: 1, x: 0 }}
            exit={{ opacity: 0, x: -28 * dir }}
            transition={{ duration: 0.2, ease: [0.23, 1, 0.32, 1] }}
            className="w-full text-center"
          >
            {i === 0 && <PaperMark />}
            {i === 1 && <BigIcon icon={BellRing} tone="blue" />}
            {i === 2 && <BigIcon icon={ShieldCheck} tone="green" />}
            {i === 3 && <BigIcon icon={Sparkles} tone="amber" />}
            <h1 className="mt-9 font-serif text-[32px] font-semibold leading-[40px] tracking-[-0.02em] text-ink">{t(`ob.${i + 1}.title` as StringKey)}</h1>
            <p className="mx-auto mt-2.5 max-w-[17rem] text-[16px] leading-[24px] text-ink-2">{t(`ob.${i + 1}.body` as StringKey)}</p>
            {i === 2 && (
              <ul className="mx-auto mt-7 w-fit space-y-3 text-left">
                {BULLETS.map(({ icon: Icon, key }) => (
                  <li key={key} className="flex items-center gap-3 text-[15px] text-ink">
                    <span className="grid size-8 place-items-center rounded-[10px] bg-green-soft text-green" aria-hidden>
                      <Icon className="size-4" strokeWidth={1.9} />
                    </span>
                    {t(key)}
                  </li>
                ))}
              </ul>
            )}
            {i === 3 && (
              <div className="mx-auto mt-7 w-full max-w-[20rem] space-y-3 text-left">
                <Segmented value={pref} onChange={setPref} options={THEMES} />
                <div className="card-tint flex items-center justify-between rounded-[14px] bg-surface px-4 py-3">
                  <span className="text-[16px] text-ink">{t("me.sound")}</span>
                  <Switch checked={sound} onCheckedChange={setSound} label={t("me.sound")} />
                </div>
              </div>
            )}
          </motion.section>
        </AnimatePresence>
      </div>

      <div className="flex justify-center gap-1.5 pb-5" aria-hidden>
        {Array.from({ length: TOTAL }, (_, n) => (
          <span key={n} className={cn("h-1.5 rounded-full transition-all duration-200 ease-[var(--ease-out)]", n === i ? "w-5 bg-ink" : "w-1.5 bg-fill")} />
        ))}
      </div>

      {!last ? (
        <XButton variant="primary" className="w-full" onClick={() => go(i + 1)}>
          {t("ob.next")}
        </XButton>
      ) : onFinish ? (
        <XButton variant="primary" className="w-full" onClick={onFinish}>
          {t("ob.done")}
        </XButton>
      ) : (
        <div className="space-y-2.5">
          <XButton variant="primary" className="w-full" onClick={() => chooseMode("demo")}>
            {t("ob.start.demo")}
          </XButton>
          <XButton variant="secondary" className="w-full" onClick={() => chooseMode("empty")}>
            {t("ob.start.empty")}
          </XButton>
        </div>
      )}
    </div>
  );
}

export function FirstRun() {
  return <Onboarding />;
}
