import { motion, useReducedMotion } from "framer-motion";
import { useMemo, useState } from "react";
import { Confetti } from "@/components/app/Confetti";
import { cn } from "@/lib/utils";
import { CATEGORIES, type Badge, type Txn, yuan } from "../../domain/types";
import { addDays, weekStartOf } from "../../logic/time";
import { buildWeeklyReport } from "../../logic/weeklyReport";

const DAYS = ["一", "二", "三", "四", "五", "六", "日"];

function Medal({ badge, index }: { badge: Badge; index: number }) {
  const reduce = useReducedMotion();
  const [flipped, setFlipped] = useState(false);
  const shown = flipped;
  return (
    <motion.button
      onClick={() => setFlipped((f) => !f)}
      aria-pressed={flipped}
      aria-label={`${badge.title}：${badge.reason}（点击翻面）`}
      initial={reduce ? false : { opacity: 0, scale: 0.6, rotateY: 180 }}
      animate={{ opacity: 1, scale: 1, rotateY: shown ? 180 : 0 }}
      transition={{ type: "spring", stiffness: 160, damping: 14, delay: reduce ? 0 : 0.5 + index * 0.18 }}
      style={{ transformStyle: "preserve-3d" }}
      className="relative aspect-square w-full"
    >
      <span className="absolute inset-0 flex flex-col items-center justify-center gap-1 rounded-3xl bg-card p-2 shadow-sm ring-1 ring-hairline" style={{ backfaceVisibility: "hidden" }}>
        <span className="text-4xl" aria-hidden>
          {badge.emoji}
        </span>
        <span className="text-[13px] font-extrabold text-ink">{badge.title}</span>
      </span>
      <span
        className="absolute inset-0 flex items-center justify-center rounded-3xl bg-coral-soft p-3 text-center text-[12px] font-semibold leading-snug text-ink"
        style={{ backfaceVisibility: "hidden", transform: "rotateY(180deg)" }}
      >
        {badge.reason}
      </span>
    </motion.button>
  );
}

export function WeeklyAwards({ txns, now }: { txns: readonly Txn[]; now: number }) {
  const reduce = useReducedMotion();
  const thisWeek = weekStartOf(now);
  const [week, setWeek] = useState(thisWeek);
  const report = useMemo(() => buildWeeklyReport(txns, week, now), [txns, week, now]);
  const max = Math.max(...report.byDay, 1);
  const todayIdx = week === thisWeek ? Math.floor((now - thisWeek) / 86400000) : -1;
  const hasData = report.txnCount > 0;

  return (
    <section className="relative space-y-3" key={week}>
      {hasData && report.badges.length > 0 && <Confetti seed={week % 997} />}
      <div className="flex rounded-full bg-secondary p-0.5" role="tablist">
        {[
          ["上周", addDays(thisWeek, -7)],
          ["本周", thisWeek],
        ].map(([label, w]) => (
          <button key={label as string} role="tab" aria-selected={week === w} onClick={() => setWeek(w as number)} className={cn("flex-1 rounded-full py-1.5 text-sm font-semibold", week === w ? "bg-card text-ink shadow-sm" : "text-ink-2")}>
            {label as string}
          </button>
        ))}
      </div>

      <div className="rounded-2xl bg-card p-4">
        <p className="text-[13px] font-semibold text-ink-2">🏆 {week === thisWeek ? "本周" : "上周"}颁奖典礼</p>
        <div className="mt-1 flex items-baseline gap-2">
          <span className="thermal text-[34px] font-extrabold leading-none text-ink">¥{yuan(report.totalCents)}</span>
          {report.deltaPct !== null && (
            <span className={cn("rounded-full px-2 py-0.5 text-xs font-bold", report.deltaPct <= 0 ? "bg-good/15 text-good" : "bg-coral-soft text-coral")}>
              较前一周 {report.deltaPct > 0 ? "+" : ""}
              {report.deltaPct}%
            </span>
          )}
        </div>
        <p className="mt-2 text-[13px] text-ink-2">{hasData ? report.comment : "这一周还没有记录，轻轻松松～"}</p>

        <div className="mt-4 flex h-28 items-end gap-2" role="img" aria-label={`每日花费：${report.byDay.map((v, i) => `周${DAYS[i]} ${yuan(v)} 元`).join("，")}`}>
          {report.byDay.map((v, i) => (
            <div key={i} className="flex h-full flex-1 flex-col items-center justify-end gap-1">
              <motion.div
                initial={reduce ? false : { height: 0 }}
                animate={{ height: `${Math.max((v / max) * 100, v > 0 ? 6 : 2)}%` }}
                transition={{ type: "spring", stiffness: 120, damping: 16, delay: reduce ? 0 : i * 0.06 }}
                className={cn("w-full rounded-t-lg", v === Math.max(...report.byDay) && v > 0 ? "bg-coral" : "bg-secondary")}
              />
              <span className={cn("text-[11px]", i === todayIdx ? "font-bold text-coral" : "text-ink-2")}>{DAYS[i]}</span>
            </div>
          ))}
        </div>

        {report.topCategories.length > 0 && (
          <div className="mt-3 flex flex-wrap gap-1.5">
            {report.topCategories.map((c) => (
              <span key={c.category} className="rounded-full bg-secondary px-2.5 py-1 text-[12px] font-semibold text-ink">
                {CATEGORIES[c.category].emoji} {CATEGORIES[c.category].label} ¥{yuan(c.cents)}
              </span>
            ))}
          </div>
        )}
      </div>

      <div>
        <h3 className="mb-2 px-1 text-sm font-extrabold text-ink">获得的头衔（点一点翻面看理由）</h3>
        {report.badges.length === 0 ? (
          <p className="rounded-2xl bg-card p-4 text-center text-sm text-ink-2">{hasData ? "这周暂时没有头衔，下周继续加油～" : "记几笔之后，这里会颁发头衔 🎖️"}</p>
        ) : (
          <div className="grid grid-cols-3 gap-2" style={{ perspective: 800 }}>
            {report.badges.map((b, i) => (
              <Medal key={b.code} badge={b} index={i} />
            ))}
          </div>
        )}
      </div>
    </section>
  );
}
