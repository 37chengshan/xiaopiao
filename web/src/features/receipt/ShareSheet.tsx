import { Check, Copy } from "lucide-react";
import { useState } from "react";
import { toast } from "sonner";
import { Segmented, XButton } from "@/components/app/kit";
import { Drawer, DrawerContent, DrawerDescription, DrawerTitle } from "@/components/ui/drawer";
import { cn } from "@/lib/utils";
import type { DailyReceipt } from "../../domain/types";
import { cssGradient, DEFAULT_GRADIENT, GRADIENTS } from "./gradients";
import { receiptPlainText, type Variant } from "./receiptModel";
import { renderReceiptPng, saveBlob, shareReceiptFile } from "./receiptImage";
import { ReceiptPaper } from "./ReceiptPaper";
import { useReceiptDoc } from "./useReceiptDoc";

const VARIANTS = [
  { value: "full", label: "完整" },
  { value: "brief", label: "简版" },
] as const;

/** Bottom sheet after the tear (docs/DESIGN.md §7.6): the receipt on a chosen gradient, full or brief, then share / save / copy. */
export function ShareSheet({ receipt, open, onClose }: { receipt: DailyReceipt; open: boolean; onClose: () => void }) {
  const [variant, setVariant] = useState<Variant>("full");
  const [gradient, setGradient] = useState(DEFAULT_GRADIENT);
  const [busy, setBusy] = useState<null | "share" | "save">(null);
  const doc = useReceiptDoc(receipt, variant);

  const copy = async () => {
    try {
      await navigator.clipboard.writeText(receiptPlainText(doc));
      toast.success("已复制文字");
    } catch {
      toast.error("复制失败，请换个方式试试");
    }
  };

  const run = async (kind: "share" | "save") => {
    if (busy) return;
    setBusy(kind);
    try {
      const blob = await renderReceiptPng(doc, gradient);
      if (kind === "share") {
        const outcome = await shareReceiptFile(blob, "我的今日小票");
        if (outcome === "shared") toast.success("小票已分享");
        else if (outcome === "fallback") {
          saveBlob(blob);
          toast("此浏览器不支持直接分享，已保存图片");
        }
      } else {
        saveBlob(blob);
        toast.success("图片已保存");
      }
    } catch {
      toast.error("生成图片失败，再试一次");
    } finally {
      setBusy(null);
    }
  };

  return (
    <Drawer open={open} onOpenChange={(o) => !o && onClose()}>
      <DrawerContent className="mx-auto max-h-[94dvh] max-w-[430px] rounded-t-[20px] border-0 bg-bg outline-none">
        <div className="overflow-y-auto px-4 pb-[max(1rem,env(safe-area-inset-bottom))] pt-1">
          <div className="flex h-12 items-center justify-between">
            <DrawerTitle className="font-serif text-[20px] font-semibold leading-none text-ink">分享小票</DrawerTitle>
            <button type="button" onClick={copy} className="-mr-2 flex h-11 items-center gap-1.5 rounded-[10px] px-2 text-[15px] text-clay active:opacity-60">
              <Copy className="size-4" strokeWidth={1.75} aria-hidden />
              复制文字
            </button>
          </div>
          <DrawerDescription className="sr-only">选择背景和版式，然后分享或保存图片</DrawerDescription>

          <div className="relative grid place-items-center overflow-hidden rounded-[16px] px-4 py-5" style={{ backgroundImage: cssGradient(gradient) }}>
            <div className="[filter:drop-shadow(0_8px_14px_rgb(0_0_0/0.22))]" style={{ zoom: variant === "full" ? 0.62 : 0.7 }}>
              <ReceiptPaper doc={doc} stamp="shown" tickerRun instant />
            </div>
          </div>

          <div role="radiogroup" aria-label="背景" className="-mx-4 mt-3 flex gap-1 overflow-x-auto px-4 pb-1 [scrollbar-width:none]">
            {GRADIENTS.map((g) => {
              const on = g.id === gradient.id;
              return (
                <button
                  key={g.id}
                  type="button"
                  role="radio"
                  aria-checked={on}
                  aria-label={g.name}
                  onClick={() => setGradient(g)}
                  className="grid size-11 shrink-0 place-items-center"
                >
                  <span
                    className={cn(
                      "grid size-8 place-items-center rounded-full ring-offset-2 ring-offset-bg transition-[box-shadow] duration-150",
                      on ? "ring-2 ring-ink" : "ring-1 ring-hairline",
                    )}
                    style={{ backgroundImage: cssGradient(g) }}
                  >
                    {on && <Check className={cn("size-4", g.dark ? "text-white" : "text-ink")} strokeWidth={2.5} aria-hidden />}
                  </span>
                </button>
              );
            })}
          </div>

          <Segmented className="mt-2" value={variant} options={VARIANTS} onChange={setVariant} />

          <div className="mt-4 flex gap-2.5">
            <XButton variant="primary" className="flex-1" onClick={() => void run("share")} disabled={busy !== null}>
              {busy === "share" ? "生成中…" : "分享"}
            </XButton>
            <XButton variant="secondary" className="flex-1" onClick={() => void run("save")} disabled={busy !== null}>
              {busy === "save" ? "保存中…" : "保存图片"}
            </XButton>
          </div>
        </div>
      </DrawerContent>
    </Drawer>
  );
}
