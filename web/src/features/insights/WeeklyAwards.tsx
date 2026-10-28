import { motion, useReducedMotion } from "framer-motion";
import { useEffect, useMemo, useState } from "react";
import { CATEGORY_ICONS, CATEGORY_TONES } from "@/components/app/categoryIcons";
import { Confetti } from "@/components/app/Confetti";
import { CountUpMoney } from "@/components/app/CountUp";
import { Group, IconTile, Money, Row, SectionHeader, Segmented } from "@/components/app/kit";
import { cn } from "@/lib/utils";
import { CATEGORIES, type Badge, type Txn } from "../../domain/types";
import { addDays, weekStartOf } from "../../logic/time";
import { buildWeeklyReport } from "../../logic/weeklyReport";

const DAYS = ["一", "二", "三", "四", "五", "六", "日"];

/** Confetti plays once per visit (page load), not on every tab or week switch. */
let confettiPlayed = false;

/** A badge medal: emoji is allowed here (docs/DESIGN.md §1.3). Tap to flip to the reason. */
function Medal({ badge, index }: { badge: Badge; index: number }) {
  const reduce = useReducedMotion();
  const [flipped, setFlipped] = useState(false);
  return (
    <motion.div
      initial={reduce ? false : { opacity: 0, y: 8 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.25, ease: [0.23, 1, 0.32, 1], delay: reduce ? 0 : Math.min(index, 5) * 0.04 }}
      style={{ perspective: 800 }}
    >
      <motion.button
        type="button"
        onClick={() => setFlipped((f) => !f)}
        aria-pressed={flipped}
        aria-label={`${badge.title}：${badge.reason}（点击翻面）`}
        animate={{ rotateY: flipped ? 180 : 0 }}
        transition={reduce ? { duration: 0.15 } : { type: "spring", duration: 0.5, bounce: 0.2 }}
        style={{ transformStyle: "preserve-3d" }}
        className="relative block aspect-square w-full"
      >
        <span className="absolute inset-0 flex flex-col items-center justify-center gap-1.5 rounded-[14px] bg-surface p-2" style={{ backfaceVisibility: "hidden" }}>
          <span className="text-[34px] leading-none" aria-hidden>
            {badge.emoji}
          </span>
          <span className="text-[13px] font-medium text-ink">{badge.title}</span>
        </span>
        <span
          className="absolute inset-0 flex items-center justify-center rounded-[14px] bg-surface-2 p-3 text-center text-[12px] leading-[17px] text-ink"
          style={{ backfaceVisibility: "hidden", transform: "rotateY(180deg)" }}
        >
          {badge.reason}
        </span>
      </motion.button>
    </motion.div>
  );
}

function DayBars({ byDay, todayIdx }: { byDay: number[]; todayIdx: number }) {
  const reduce = useReducedMotion();
  const peak = Math.max(...byDay, 0);
  const label = `每日花费：${byDay.map((v, i) => `周${DAYS[i]} ${v / 100} 元`).join("，")}`;
  return (
    <div className="mt-5 flex h-28 items-end gap-2" role="img" aria-label={label}>
      {byDay.map((v, i) => {
        const isPeak = v > 0 && v === peak;
        return (
          <div key={i} className="flex h-full flex-1 flex-col items-center justify-end gap-1.5">
            <div className="flex w-full flex-1 flex-col justify-end">
              {isPeak && <span className="num mb-1 text-center text-[11px] leading-[14px] text-ink-2">{Math.round(v / 100)}</span>}
              <motion.div
                initial={reduce ? false : { scaleY: 0.1, opacity: 0 }}
                animate={{ scaleY: 1, opacity: 1 }}
                transition={{ duration: 0.28, ease: [0.23, 1, 0.32, 1], delay: reduce ? 0 : i * 0.03 }}
                style={{ height: `${Math.max(peak ? (v / peak) * 78 : 0, v > 0 ? 5 : 2)}%`, transformOrigin: "bottom" }}
                className={cn("w-full rounded-t-[6px]", isPeak ? "bg-clay" : "bg-ink-3/40")}
              />
            </div>
            <span className={cn("text-[11px] leading-[14px]", i === todayIdx ? "font-semibold text-ink" : "text-ink-2")}>{DAYS[i]}</span>
          </div>
        );
      })}
    </div>
  );
}

export function WeeklyAwards({ txns, now }: { txns: readonly Txn[]; now: number }) {
  const thisWeek = weekStartOf(now);
  // early in the week there is little to celebrate yet, so open on last week
  const [week, setWeek] = useState(now - thisWeek < 3 * 86400000 ? addDays(thisWeek, -7) : thisWeek);
  const report = useMemo(() => buildWeeklyReport(txns, week, now), [txns, week, now]);
  const todayIdx = week === thisWeek ? Math.floor((now - thisWeek) / 86400000) : -1;
  const hasData = report.txnCount > 0;
  const [celebrate, setCelebrate] = useState(false);

  useEffect(() => {
    if (!confettiPlayed && hasData && report.badges.length > 0) {
      confettiPlayed = true;
      setCelebrate(true);
    }
  }, [hasData, report.badges.length]);

  return (
    <div className="relative">
      {celebrate && <Confetti seed={week % 997} />}
      <section key={week}>
      <div className="mx-4 mt-3 rounded-[14px] bg-surface p-4">
        <div className="flex items-center justify-between gap-3">
          <p className="text-[13px] leading-[18px] text-ink-2">{week === thisWeek ? "本周" : "上周"}花费</p>
          <Segmented
            className="w-[7.5rem]"
            value={week === thisWeek ? "this" : "last"}
            onChange={(v) => setWeek(v === "this" ? thisWeek : addDays(thisWeek, -7))}
            options={[
              { value: "last", label: "上周" },
              { value: "this", label: "本周" },
            ]}
          />
        </div>
        <CountUpMoney cents={report.totalCents} className="mt-2 block text-[48px] font-semibold leading-[52px] tracking-[-0.03em] text-ink" />
        <p className="mt-2 text-[14px] leading-[20px] text-ink-2">
          {report.deltaPct !== null && (
            <>
              较前一周 <span className={report.deltaPct <= 0 ? "text-positive" : "text-negative"}>{report.deltaPct > 0 ? "+" : ""}{report.deltaPct}%</span> ·{" "}
            </>
          )}
          {hasData ? report.comment : "这一周还没有记录，轻轻松松～"}
        </p>
        <DayBars byDay={report.byDay} todayIdx={todayIdx} />
      </div>

      {report.topCategories.length > 0 && (
        <>
          <SectionHeader>花得最多的</SectionHeader>
          <Group inset="icon">
            {report.topCategories.map((c) => (
              <Row key={c.category} leading={<IconTile icon={CATEGORY_ICONS[c.category]} tone={CATEGORY_TONES[c.category]} />} title={CATEGORIES[c.category].label} trailing={<Money cents={-c.cents} />} />
            ))}
          </Group>
        </>
      )}

      <SectionHeader action={report.badges.length > 0 ? <span className="text-[12px] text-ink-2">点一下翻面看理由</span> : undefined}>获得的头衔</SectionHeader>
      {report.badges.length === 0 ? (
        <p className="mx-4 rounded-[14px] bg-surface p-4 text-center text-[14px] text-ink-2">{hasData ? "这周暂时没有头衔，下周继续加油～" : "记几笔之后，这里会颁发头衔"}</p>
      ) : (
        <div className="mx-4 grid grid-cols-3 gap-2">
          {report.badges.map((b, i) => (
            <Medal key={b.code} badge={b} index={i} />
          ))}
        </div>
      )}
      </section>
    </div>
  );
}
