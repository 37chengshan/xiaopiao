import { animate, motion, useDragControls, useMotionValue, useReducedMotion } from "framer-motion";
import { Scissors } from "lucide-react";
import { useEffect, useRef, useState } from "react";
import { toast } from "sonner";
import { Drawer, DrawerContent, DrawerDescription, DrawerTitle } from "@/components/ui/drawer";
import { useLedger } from "@/services/LedgerProvider";
import { CATEGORIES, type DailyReceipt, yuan } from "../../domain/types";
import { dayLabel, hhmm } from "../../logic/time";
import { renderReceiptPng, shareReceiptFile } from "./receiptImage";

function useRollingNumber(target: number, delay: number, enabled: boolean): number {
  const [value, setValue] = useState(enabled ? 0 : target);
  useEffect(() => {
    if (!enabled) {
      setValue(target);
      return;
    }
    const controls = animate(0, target, { duration: 0.9, delay, ease: "easeOut", onUpdate: (v) => setValue(Math.round(v)) });
    return () => controls.stop();
  }, [target, delay, enabled]);
  return value;
}

/** The signature moment: paper feeds out of the printer slot line by line, total rolls, stamp slams, drag up to tear. */
export function Receipt({ receipt, playKey = 0 }: { receipt: DailyReceipt; playKey?: number }) {
  const reduce = useReducedMotion() ?? false;
  const { mode } = useLedger();
  const items = receipt.items;
  const zero = items.length === 0;
  const step = reduce ? 0 : Math.min(0.16, 2.4 / Math.max(items.length, 1));
  const start = reduce ? 0 : 0.7;
  const tEnd = start + (zero ? 0.3 : items.length * step);
  const rolled = useRollingNumber(receipt.totalOutCents, tEnd + 0.15, !reduce);
  const stampDelay = reduce ? 0 : tEnd + 1.5;
  const stampText = receipt.badge?.title ?? (zero ? "钱包休眠者" : "今日小票");

  const y = useMotionValue(0);
  const controls = useDragControls();
  const [tearing, setTearing] = useState(false);
  const [sheet, setSheet] = useState<{ url: string } | null>(null);
  const urlRef = useRef<string | null>(null);

  useEffect(() => () => {
    if (urlRef.current) URL.revokeObjectURL(urlRef.current);
  }, []);

  const tear = async () => {
    if (tearing) return;
    setTearing(true);
    try {
      const blob = await renderReceiptPng(receipt, { demo: mode === "demo" });
      await animate(y, -380, { duration: reduce ? 0 : 0.35, ease: "easeIn" });
      const outcome = await shareReceiptFile(blob, "我的今日小票");
      if (outcome === "fallback") {
        if (urlRef.current) URL.revokeObjectURL(urlRef.current);
        urlRef.current = URL.createObjectURL(blob);
        setSheet({ url: urlRef.current });
      } else if (outcome === "shared") toast.success("小票已撕下，分享出去啦 🎉");
    } catch {
      toast.error("撕票失败了，再拉一次试试");
    } finally {
      await animate(y, 0, { type: "spring", stiffness: 160, damping: 18 });
      setTearing(false);
    }
  };

  return (
    <div className="px-5 pb-4 pt-2" key={playKey}>
      {/* printer */}
      <div className="relative z-10 mx-auto h-5 w-full rounded-full bg-ink shadow-md" aria-hidden>
        <div className="absolute inset-x-3 top-1/2 h-1.5 -translate-y-1/2 rounded-full bg-black/70" />
        <motion.div
          className="absolute inset-x-6 top-1/2 h-px -translate-y-1/2 bg-coral"
          animate={reduce ? undefined : { opacity: [0.2, 1, 0.2] }}
          transition={{ duration: 0.5, repeat: Math.ceil((tEnd + 0.4) / 0.5), ease: "linear" }}
        />
      </div>

      <motion.div
        drag="y"
        dragControls={controls}
        dragListener={false}
        dragConstraints={{ top: -200, bottom: 0 }}
        dragElastic={0.15}
        style={{ y }}
        onDragEnd={(_, info) => {
          if (info.offset.y < -90) void tear();
          else animate(y, 0, { type: "spring", stiffness: 300, damping: 26 });
        }}
        className="relative mx-3 -mt-1"
      >
        <div className="zigzag-bottom thermal relative bg-paper px-5 pb-4 pt-5 text-ink shadow-sm" aria-label={`${dayLabel(receipt.dayStart)} 的小票`}>
          <motion.div initial={reduce ? false : { opacity: 0, y: -14 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: reduce ? 0 : 0.3, duration: 0.2 }} className="text-center">
            <h2 className="text-[22px] font-extrabold tracking-[0.3em]">今日小票</h2>
            <p className="text-xs text-ink-2">{dayLabel(receipt.dayStart)}</p>
          </motion.div>
          <div className="my-3 border-t-2 border-dashed border-hairline" />

          {zero ? (
            <motion.p initial={reduce ? false : { opacity: 0, y: -14 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: start }} className="py-6 text-center text-xl font-bold">
              今日无消费 🎉
            </motion.p>
          ) : (
            <ul className="space-y-1.5 text-[13px]">
              {items.map((t, i) => (
                <motion.li
                  key={t.id}
                  initial={reduce ? false : { opacity: 0, y: -16 }}
                  animate={{ opacity: 1, y: 0 }}
                  transition={{ delay: start + i * step, duration: 0.18 }}
                  className="flex items-baseline gap-2"
                >
                  <span className="shrink-0 text-ink-2">{hhmm(t.occurredAt)}</span>
                  <span className="min-w-0 flex-1 truncate">
                    <span aria-hidden>{CATEGORIES[t.category].emoji} </span>
                    {t.merchant}
                  </span>
                  <span className="shrink-0 font-semibold">
                    {t.direction === "IN" ? "+" : ""}
                    {yuan(t.amountCents)}
                  </span>
                </motion.li>
              ))}
            </ul>
          )}

          <div className="my-3 border-t-2 border-dashed border-hairline" />
          <div className="flex items-baseline justify-between">
            <span className="text-base font-bold">合计</span>
            <span className="text-[34px] font-extrabold leading-none tabular-nums" aria-label={`合计 ${yuan(receipt.totalOutCents)} 元`}>
              ¥{yuan(rolled)}
            </span>
          </div>

          <motion.p
            initial={reduce ? false : { opacity: 0 }}
            animate={{ opacity: 1 }}
            transition={{ delay: reduce ? 0 : tEnd + 1.1, duration: 0.4 }}
            className="mt-3 rounded-lg bg-secondary px-3 py-2 text-[13px] leading-relaxed text-ink-2"
          >
            {receipt.aiComment}
          </motion.p>
          {receipt.badge && (
            <p className="mt-2 text-center text-[11px] text-ink-2">
              {receipt.badge.emoji} {receipt.badge.reason}
            </p>
          )}
          <p className="mt-3 text-center text-[11px] tracking-widest text-ink-2">THANK YOU · 明天见</p>

          {/* stamp */}
          <div className="pointer-events-none absolute bottom-7 right-3" aria-hidden>
            <motion.div
              initial={reduce ? false : { scale: 2.6, rotate: -8, opacity: 0 }}
              animate={{ scale: 1, rotate: -8, opacity: 0.92 }}
              transition={{ type: "spring", stiffness: 420, damping: 11, mass: 0.8, delay: stampDelay }}
              className="relative rounded-xl border-4 border-stamp px-3 py-1 text-xl font-extrabold text-stamp"
              style={{ boxShadow: "inset 0 0 0 2px var(--paper), inset 0 0 0 3.5px var(--stamp)" }}
            >
              {stampText}
              {!reduce && (
                <motion.span
                  className="absolute -inset-1 rounded-2xl border-2 border-stamp"
                  initial={{ scale: 1, opacity: 0 }}
                  animate={{ scale: 1.5, opacity: [0, 0.5, 0] }}
                  transition={{ delay: stampDelay + 0.12, duration: 0.7, ease: "easeOut" }}
                />
              )}
            </motion.div>
          </div>
        </div>

        {/* tear handle */}
        <div className="mt-1 flex flex-col items-center">
          <button
            onPointerDown={(e) => controls.start(e)}
            onClick={() => void tear()}
            disabled={tearing}
            style={{ touchAction: "none" }}
            className="flex items-center gap-1.5 rounded-full bg-ink px-4 py-2 text-[13px] font-semibold text-white shadow-md active:scale-95 disabled:opacity-60"
            aria-label="撕下来分享：点击，或向上拖动"
          >
            <Scissors className="size-4" aria-hidden />
            撕下来分享
          </button>
          <span className="mt-1 text-[11px] text-ink-2">向上拖动 ↑ 把小票撕下来</span>
        </div>
      </motion.div>

      <Drawer open={sheet !== null} onOpenChange={(o) => !o && setSheet(null)}>
        <DrawerContent className="mx-auto max-h-[92dvh] max-w-[430px] bg-canvas outline-none">
          <DrawerTitle className="sr-only">小票图片</DrawerTitle>
          <DrawerDescription className="sr-only">长按图片保存到相册</DrawerDescription>
          <div className="overflow-y-auto px-5 pb-[max(1.5rem,env(safe-area-inset-bottom))] pt-3 text-center">
            <p className="mb-3 text-sm font-semibold text-ink">长按图片，保存到相册 📸</p>
            {sheet && <img src={sheet.url} alt="今日小票图片" className="mx-auto w-full max-w-[320px] rounded-lg shadow" />}
          </div>
        </DrawerContent>
      </Drawer>
    </div>
  );
}
