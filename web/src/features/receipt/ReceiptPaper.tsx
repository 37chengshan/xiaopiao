import { motion } from "framer-motion";
import type { Ref } from "react";
import { EASE_OUT, SPRING_LAND } from "@/lib/motion";
import { NumberTicker } from "./NumberTicker";
import { DISCLAIMER, fmtMoney, STUB, TAGLINE, type DocGroup, type ReceiptDoc } from "./receiptModel";
import { PAPER_W, paperClipPath } from "./zigzag";

export type StampState = "hidden" | "slam" | "shown";

const FIBRE = `url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='160' height='160'%3E%3Cfilter id='n'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='.9 .05' numOctaves='2' seed='7'/%3E%3CfeColorMatrix values='0 0 0 0 .35 0 0 0 0 .3 0 0 0 0 .25 0 0 0 .55 0'/%3E%3C/filter%3E%3Crect width='160' height='160' filter='url(%23n)'/%3E%3C/svg%3E")`;

function Rule() {
  return <hr className="my-3 h-0 border-0 border-t border-dashed border-paper-ink/25" />;
}

function Stamp({ text, state }: { text: string; state: StampState }) {
  const shown = state !== "hidden";
  return (
    <div className="pointer-events-none relative grid h-[88px] place-items-center" data-row aria-hidden>
      <motion.div
        initial={false}
        animate={
          state === "slam"
            ? { scale: 1, rotate: -8, opacity: 0.92 }
            : shown
              ? { scale: 1, rotate: -8, opacity: 0.92 }
              : { scale: 1.5, rotate: -8, opacity: 0 }
        }
        transition={state === "slam" ? SPRING_LAND : { duration: 0 }}
        className="relative rounded-[6px] border-2 border-stamp px-3.5 py-1.5 font-serif text-[19px] font-semibold tracking-[0.14em] text-stamp mix-blend-multiply [box-shadow:inset_0_0_0_1.5px_var(--paper),inset_0_0_0_2.5px_var(--stamp)]"
      >
        {text}
        {state === "slam" && (
          <>
            <motion.span
              className="absolute -inset-1 rounded-[9px] border-[1.5px] border-stamp"
              initial={{ scale: 1, opacity: 0.55 }}
              animate={{ scale: 1.32, opacity: 0 }}
              transition={{ duration: 0.55, ease: EASE_OUT, delay: 0.08 }}
            />
            <motion.span
              className="absolute -inset-0.5 rounded-[8px] bg-stamp/15"
              initial={{ scale: 0.96, opacity: 0.9 }}
              animate={{ scale: 1.12, opacity: 0 }}
              transition={{ duration: 0.7, ease: EASE_OUT, delay: 0.05 }}
            />
          </>
        )}
      </motion.div>
    </div>
  );
}

function Line({ left, right, strong, dim }: { left: string; right: string; strong?: boolean; dim?: boolean }) {
  return (
    <div className={`flex items-baseline justify-between gap-3 text-[12.5px] leading-[20px] ${strong ? "font-medium" : ""} ${dim ? "text-paper-ink/70" : ""}`} data-row>
      <span className="min-w-0 truncate">{left}</span>
      <span className="num shrink-0">{right}</span>
    </div>
  );
}

function Group({ g, sign = "" }: { g: DocGroup; sign?: string }) {
  return (
    <>
      <Line left={`${g.emoji} ${g.label}`} right={`${sign}${fmtMoney(g.subtotalCents)}`} strong />
      {g.lines.map((l, i) => (
        <div key={i} className="pl-[22px]">
          <Line left={l.merchant} right={`${sign}${fmtMoney(l.cents)}`} dim />
        </div>
      ))}
      {g.hidden > 0 && (
        <div className="pl-[22px] text-[11.5px] leading-[18px] text-paper-ink/70" data-row>
          … 另 {g.hidden} 笔
        </div>
      )}
    </>
  );
}

/**
 * The receipt itself (docs/DESIGN.md §7 paper). Pure presentation: print timing lives in the overlay.
 * `[data-row]` marks the lines the printer uncovers one by one; `[data-total]` is where the total ticker starts.
 */
export function ReceiptPaper({
  doc,
  stamp = "shown",
  tickerRun = true,
  instant = true,
  ref,
}: {
  doc: ReceiptDoc;
  stamp?: StampState;
  tickerRun?: boolean;
  instant?: boolean;
  ref?: Ref<HTMLDivElement>;
}) {
  const full = doc.variant === "full";
  return (
    <div ref={ref} className="relative text-paper-ink" style={{ width: PAPER_W }} aria-label={`${doc.dateLabel} 的小票`}>
      <div className="relative bg-paper px-5 pb-[26px] pt-[30px] font-mono" style={{ clipPath: paperClipPath() }}>
        <div aria-hidden className="pointer-events-none absolute inset-0 opacity-[0.07] mix-blend-multiply" style={{ backgroundImage: FIBRE }} />
        <div aria-hidden className="pointer-events-none absolute inset-x-0 top-0 h-10 bg-gradient-to-b from-paper-ink/[0.06] to-transparent" />
        <div aria-hidden className="pointer-events-none absolute inset-0 bg-[linear-gradient(90deg,rgb(60_40_20/0.07),transparent_9%,transparent_91%,rgb(60_40_20/0.07))]" />
        <div aria-hidden className="pointer-events-none absolute inset-x-0 bottom-0 h-8 bg-gradient-to-t from-paper-ink/[0.05] to-transparent" />

        <div className="text-center" data-row>
          <h2 className="pl-[0.5em] font-serif text-[23px] font-semibold leading-[30px] tracking-[0.5em]">今日小票</h2>
          <p className="mt-0.5 font-serif text-[12px] text-paper-ink/70">{TAGLINE}</p>
        </div>
        <Rule />
        <Line left="日期" right={doc.dateLabel} />
        {full && <Line left="出票" right={doc.printedAt} />}
        <Rule />

        <div className="py-1 text-center" data-row data-total>
          <p className="text-[11.5px] tracking-[0.3em] text-paper-ink/70">{doc.isToday ? "今日支出" : "当日支出"}</p>
          <NumberTicker cents={doc.totalCents} run={tickerRun} instant={instant} className="text-[44px] font-semibold tracking-[-0.03em]" />
          <p className="text-[10.5px] tracking-[0.06em] text-paper-ink/70">{DISCLAIMER}</p>
        </div>
        <Rule />

        {!full ? null : doc.zero ? (
          <div className="py-5 text-center" data-row>
            <p className="font-serif text-[22px] font-semibold">今日无消费</p>
            <p className="mt-1 text-[11.5px] text-paper-ink/70">钱包今天睡着了 🌙</p>
          </div>
        ) : (
          <>
            <div className="flex justify-between pb-1 text-[10.5px] tracking-[0.08em] text-paper-ink/70" data-row>
              <span>分类 / 明细</span>
              <span>金额</span>
            </div>
            <div className="space-y-1.5">
              {doc.groups.map((g) => (
                <div key={g.key}>
                  <Group g={g} />
                </div>
              ))}
              {doc.folded && <Line left={`… 其他 ${doc.folded.count} 类`} right={fmtMoney(doc.folded.cents)} dim />}
              {doc.income && (
                <div>
                  <Group g={doc.income} sign="+" />
                </div>
              )}
            </div>
          </>
        )}
        {full && (
          <>
            <Rule />
            <div className="flex justify-between text-[11px] text-paper-ink/70" data-row>
              <span>{doc.stats.count}</span>
              <span>{doc.stats.delta}</span>
              <span>{doc.stats.weekDay}</span>
            </div>
            <Rule />
          </>
        )}
        <p className="px-1 text-center font-serif text-[12.5px] leading-[1.75]" data-row>
          「{doc.remark}」
        </p>

        <Stamp text={doc.stampText} state={stamp} />
        {full && doc.badgeReason && (
          <p className="-mt-3 mb-2 text-center text-[11px] text-paper-ink/70" data-row>
            {doc.badgeReason}
          </p>
        )}
        <p className="text-center text-[10.5px] tracking-[0.12em] text-paper-ink/70" data-row>
          {STUB}
        </p>
        {doc.demo && <p className="mt-1 text-center text-[10px] text-paper-ink/70" data-row>演示数据</p>}
      </div>
    </div>
  );
}

/** Bottom of every `[data-row]` inside the paper, in unscaled px from the paper's top (for the print timeline). */
export function measureRows(paper: HTMLElement): { height: number; rowBottoms: number[]; totalBottom: number } {
  const rows = [...paper.querySelectorAll<HTMLElement>("[data-row]")];
  const bottom = (el: HTMLElement) => {
    let y = 0;
    for (let n: HTMLElement | null = el; n && n !== paper; n = n.offsetParent as HTMLElement | null) y += n.offsetTop;
    return y + el.offsetHeight;
  };
  const total = paper.querySelector<HTMLElement>("[data-total]");
  return { height: paper.offsetHeight, rowBottoms: rows.map(bottom), totalBottom: total ? bottom(total) : 0 };
}
