import { motion, useReducedMotion } from "framer-motion";
import { useLedger } from "@/services/LedgerProvider";

export function FirstRun() {
  const { chooseMode } = useLedger();
  const reduce = useReducedMotion();
  return (
    <div className="flex h-full flex-col justify-center px-6 py-10">
      <motion.div
        initial={reduce ? false : { y: -30, opacity: 0, rotate: -6 }}
        animate={{ y: 0, opacity: 1, rotate: -3 }}
        transition={{ type: "spring", stiffness: 180, damping: 14 }}
        className="mx-auto mb-6 grid size-24 place-items-center rounded-3xl bg-coral text-5xl shadow-lg"
        aria-hidden
      >
        🧾
      </motion.div>
      <h1 className="text-center text-[32px] font-extrabold tracking-tight text-ink">今日小票</h1>
      <p className="mx-auto mt-2 max-w-[18rem] text-center text-[15px] leading-relaxed text-ink-2">
        不用天天记账，每天只领一张小票。先挑一种方式开始吧～
      </p>
      <div className="mt-8 space-y-3">
        <button
          onClick={() => chooseMode("demo")}
          className="w-full rounded-2xl bg-coral px-5 py-4 text-left text-white shadow-sm transition active:scale-[0.98]"
        >
          <span className="block text-lg font-bold">看看演示</span>
          <span className="block text-[13px] opacity-90">带三周虚构数据，立刻体验小票、周报、订阅侦探</span>
        </button>
        <button
          onClick={() => chooseMode("empty")}
          className="w-full rounded-2xl border border-hairline bg-card px-5 py-4 text-left text-ink transition active:scale-[0.98]"
        >
          <span className="block text-lg font-bold">从零开始</span>
          <span className="block text-[13px] text-ink-2">一张白纸，自己记或导入支付宝/微信账单</span>
        </button>
      </div>
      <p className="mt-6 text-center text-[12px] text-ink-2">所有记录都由你确认；小票只做记录、总结和提醒，不提供理财建议。</p>
    </div>
  );
}
