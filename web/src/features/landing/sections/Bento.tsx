import { useMemo, useState } from "react";
import { useLocale } from "@/lib/strings";
import { s } from "../strings";
import { IMG } from "../lib/assets";
import { ClockProvider, Reveal, SplitText, useClock, useInViewOnce } from "../lib/motion";

/** L4 — six cards, each a quiet live fragment of the product, all reading one clock (maya-lind-bento). */
export function Bento() {
  useLocale();
  const { ref, inView } = useInViewOnce(0.12);
  return (
    <section id="features" ref={ref} className="relative mx-auto w-full max-w-[1320px] px-4 py-24 sm:px-6 lg:py-32">
      <div className="max-w-[720px]">
        <p className="lp-card-kicker">{s("bento.eyebrow")}</p>
        <h2 className="mt-3 text-[clamp(32px,4vw,56px)] font-semibold leading-[1.04] tracking-[-0.03em]">
          <SplitText as="span" text={s("bento.title")} active={inView} />
        </h2>
        <p className="mt-4 max-w-[52ch] text-[16px] leading-[1.75] text-[var(--lp-ink)]">{s("bento.body")}</p>
      </div>

      <ClockProvider>
        <div className="lp-bento mt-10">
          <Reveal delay={0} className="col-span-12 md:col-span-5">
            <VoiceCard active={inView} />
          </Reveal>
          <Reveal delay={1} className="col-span-12 md:col-span-4">
            <SubsCard active={inView} />
          </Reveal>
          <Reveal delay={2} className="col-span-12 md:col-span-3">
            <BudgetCard active={inView} />
          </Reveal>
          <Reveal delay={3} className="col-span-12 md:col-span-4">
            <CircleCard active={inView} />
          </Reveal>
          <Reveal delay={4} className="col-span-12 md:col-span-4">
            <RegretCard />
          </Reveal>
          <Reveal delay={5} className="col-span-12 md:col-span-4">
            <ShopCard active={inView} />
          </Reveal>
        </div>
      </ClockProvider>
    </section>
  );
}

function VoiceCard({ active }: { active: boolean }) {
  const t = useClock();
  const phase = t % 9;
  const listening = active && phase < 2.2;
  const bars = useMemo(() => Array.from({ length: 30 }, (_, i) => i), []);
  return (
    <div className="lp-card h-full">
      <p className="lp-card-kicker">{s("bento.voice.title")}</p>
      <div className="mt-4 flex flex-1 flex-col justify-center gap-4">
        <div className="flex items-center gap-3 rounded-2xl bg-[rgba(255,138,0,0.08)] px-3 py-3">
          <span className="grid size-8 shrink-0 place-items-center rounded-full bg-[var(--lp-orange)] text-[13px] text-white">●</span>
          <div className="lp-wave flex-1">
            {bars.map((i) => {
              const wobble = listening ? 0.32 + 0.68 * Math.abs(Math.sin(t * 2.4 + i * 0.55) * Math.cos(t * 1.1 + i * 0.21)) : 0.12;
              return <i key={i} style={{ transform: `scaleY(${wobble})`, opacity: listening ? 0.85 : 0.3 }} />;
            })}
          </div>
        </div>
        <p className="text-[13px] text-[var(--lp-ink-2)]">{listening ? s("bento.voice.listening") : s("bento.voice.said")}</p>
        <div
          className="flex items-center justify-between rounded-xl border border-[rgba(27,23,18,0.08)] bg-white px-3 py-2.5 text-[13px]"
          style={{ opacity: listening ? 0.25 : 1, transform: listening ? "translateY(4px)" : "none", transition: "opacity .4s, transform .4s" }}
        >
          <span>{s("bento.voice.row")}</span>
          <span className="text-[var(--lp-orange-deep)]" aria-hidden>
            ✓
          </span>
        </div>
      </div>
    </div>
  );
}

function SubsCard({ active }: { active: boolean }) {
  const t = useClock();
  const phase = active ? t : 0;
  const subs = [
    { name: "iCloud", amount: "¥6", period: s("bento.perMonth") },
    { name: "视频会员", amount: "¥25", period: s("bento.perMonth") },
    { name: "网易云", amount: "¥15", period: s("bento.perMonth") },
  ];
  return (
    <div className="lp-card h-full">
      <p className="lp-card-kicker">{s("bento.subs.title")}</p>
      <p className="mt-2 text-[13px] text-[var(--lp-ink-2)]">{s("bento.subs.body")}</p>
      <ul className="mt-4 flex flex-1 flex-col gap-2">
        {subs.map((row, i) => (
          <li
            key={row.name}
            className="flex items-center justify-between rounded-lg bg-[rgba(27,23,18,0.04)] px-2.5 py-2 text-[13px]"
            style={{ opacity: phase > 0.4 + i * 0.5 ? 1 : 0, transform: phase > 0.4 + i * 0.5 ? "none" : "translateX(-10px)", transition: "opacity .45s, transform .45s" }}
          >
            <span>{row.name} · {row.amount}{row.period}</span>
            <span className="num text-[var(--lp-ink-3)]">{["72", "300", "180"][i]}{s("bento.perYear")}</span>
          </li>
        ))}
      </ul>
      <div className="mt-3 flex items-baseline justify-between border-t border-[rgba(27,23,18,0.08)] pt-3">
        <span className="text-[13px] text-[var(--lp-ink-3)]">{s("bento.subs.annual")}</span>
        <span className="num text-[20px] font-semibold">¥{Math.round(Math.min(1, Math.max(0, phase / 2.2)) * 552)}</span>
      </div>
    </div>
  );
}

function BudgetCard({ active }: { active: boolean }) {
  const t = useClock();
  const p = active ? Math.min(0.68, (t / 1.6) * 0.68) : 0.68;
  const r = 42;
  const c = 2 * Math.PI * r;
  return (
    <div className="lp-card h-full items-center text-center">
      <p className="lp-card-kicker self-start">{s("bento.budget.title")}</p>
      <div className="relative mt-3 grid flex-1 place-items-center">
        <svg viewBox="0 0 110 110" className="size-[132px]">
          <circle className="lp-ring-track" cx="55" cy="55" r={r} />
          <circle className="lp-ring-arc" cx="55" cy="55" r={r} strokeDasharray={c} strokeDashoffset={c * (1 - p)} />
        </svg>
        <div className="absolute text-center">
          <p className="num text-[22px] font-semibold">{Math.round(p * 100)}%</p>
          <p className="text-[10.5px] text-[var(--lp-ink-3)]">{s("bento.budget.used")}</p>
        </div>
      </div>
      <p className="text-[13px] text-[var(--lp-ink-2)]">{s("bento.budget.left")}</p>
    </div>
  );
}

function CircleCard({ active }: { active: boolean }) {
  const t = useClock();
  const reactions = ["🍵", "❤️", "😂", "👍", "👀"];
  const phase = active ? t % 7 : 7;
  return (
    <div className="lp-card h-full !p-0">
      <div className="relative h-[150px] w-full overflow-hidden">
        <img src={IMG.friendsTable} alt={s("bento.circle.title")} loading="lazy" className="size-full object-cover" />
        <div className="absolute inset-0 bg-[linear-gradient(180deg,transparent_40%,rgba(20,16,12,0.55))]" aria-hidden />
      </div>
      <div className="flex flex-1 flex-col p-5">
        <p className="lp-card-kicker">{s("bento.circle.title")}</p>
        <p className="mt-2 text-[13px] leading-[1.6] text-[var(--lp-ink-2)]">{s("bento.circle.body")}</p>
        <div className="mt-auto flex items-center gap-1.5 pt-3">
          {reactions.map((emoji, i) => (
            <span
              key={emoji}
              className="grid size-7 place-items-center rounded-full bg-[rgba(255,255,255,0.9)] text-[13px] shadow-[0_2px_6px_rgba(0,0,0,0.12)]"
              style={{
                opacity: phase > 0.8 + i * 0.7 ? 1 : 0,
                transform: phase > 0.8 + i * 0.7 ? "translateY(0) scale(1)" : "translateY(6px) scale(.8)",
                transition: "opacity .4s, transform .45s cubic-bezier(.22,1,.36,1)",
              }}
            >
              {emoji}
            </span>
          ))}
        </div>
      </div>
    </div>
  );
}

function RegretCard() {
  const [cups, setCups] = useState(2);
  const saved = cups * 15 * 52;
  return (
    <div className="lp-card h-full">
      <p className="lp-card-kicker">{s("bento.regret.title")}</p>
      <p className="mt-2 text-[13px] text-[var(--lp-ink-2)]">{s("bento.regret.less")}</p>
      <input
        type="range"
        min={0}
        max={6}
        step={1}
        value={cups}
        onChange={(e) => setCups(Number(e.target.value))}
        aria-label={s("bento.regret.less")}
        className="mt-4 h-1.5 w-full appearance-none rounded-full bg-[rgba(27,23,18,0.12)] accent-[var(--lp-orange)]"
      />
      <div className="mt-4 flex items-baseline justify-between">
        <span className="text-[13px] text-[var(--lp-ink-3)]">{s("bento.regret.year")}</span>
        <span className="num text-[26px] font-semibold">¥{saved.toLocaleString("en-US")}</span>
      </div>
      <p className="mt-auto pt-3 text-[13px] text-[var(--lp-ink-3)]">{s("bento.regret.note")}</p>
    </div>
  );
}

function ShopCard({ active }: { active: boolean }) {
  const t = useClock();
  const p = active ? Math.min(1, t / 1.8) : 1;
  const heights = [42, 58, 36, 72, 64, 88];
  return (
    <div className="lp-card h-full !p-0">
      <div className="relative h-[140px] w-full overflow-hidden">
        <img src={IMG.shopCounter} alt={s("bento.shop.title")} loading="lazy" className="size-full object-cover" />
      </div>
      <div className="flex flex-1 flex-col p-5">
        <p className="lp-card-kicker">{s("bento.shop.title")}</p>
        <div className="lp-bars mt-3">
          {heights.map((h, i) => (
            <i key={i} style={{ height: `${h * Math.min(1, Math.max(0, p * 1.6 - i * 0.08))}%` }} />
          ))}
        </div>
        <div className="mt-auto flex items-center justify-between pt-3 text-[13px] text-[var(--lp-ink-3)]">
          <span>{s("bento.shop.body")}</span>
          <span className="rounded-full bg-[rgba(27,23,18,0.06)] px-2 py-0.5">{s("bento.shop.k")}</span>
        </div>
      </div>
    </div>
  );
}
