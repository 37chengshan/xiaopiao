import { useLedger } from "@/services/LedgerProvider";
import { XButton } from "./kit";

/** A tiny receipt strip: paper, torn edge, a few faint lines. Calm, no colour. */
function PaperMark() {
  return (
    <div className="zigzag-bottom mx-auto w-14 bg-paper px-2.5 pb-4 pt-3.5 shadow-[0_6px_18px_-8px_oklch(0_0_0/30%)]" aria-hidden>
      <div className="mx-auto h-[3px] w-6 rounded-full bg-paper-ink/35" />
      <div className="mt-2.5 space-y-1.5">
        <div className="h-[2px] rounded-full bg-paper-ink/15" />
        <div className="h-[2px] w-4/5 rounded-full bg-paper-ink/15" />
        <div className="h-[2px] w-3/5 rounded-full bg-paper-ink/15" />
      </div>
    </div>
  );
}

export function FirstRun() {
  const { chooseMode } = useLedger();
  return (
    <div className="flex min-h-full flex-col justify-center px-6 pb-[max(2rem,env(safe-area-inset-bottom))] pt-[max(2rem,env(safe-area-inset-top))] animate-in fade-in slide-in-from-bottom-2 duration-300 ease-out">
      <PaperMark />
      <h1 className="mt-8 text-center font-serif text-[36px] font-semibold leading-[44px] tracking-[-0.02em] text-ink">今日小票</h1>
      <p className="mx-auto mt-3 max-w-[17rem] text-center text-[16px] leading-[26px] text-ink-2">不用天天记账，每天只领一张小票。</p>
      <div className="mt-10 space-y-2.5">
        <XButton variant="primary" className="w-full" onClick={() => chooseMode("demo")}>
          看看演示
        </XButton>
        <XButton variant="secondary" className="w-full" onClick={() => chooseMode("empty")}>
          从零开始
        </XButton>
      </div>
      <p className="mx-auto mt-5 max-w-[19rem] text-center text-[13px] leading-[19px] text-ink-2">
        演示带三周虚构数据，立刻体验小票、周报、订阅侦探；从零开始是一张白纸，可自己记，也可导入支付宝、微信账单。
      </p>
      <p className="mx-auto mt-8 max-w-[19rem] text-center text-[12px] leading-[18px] text-ink-2">所有记录都由你确认；小票只做记录、总结和提醒，不提供理财建议。</p>
    </div>
  );
}
