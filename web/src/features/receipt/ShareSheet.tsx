import { animate, motion, useMotionValue, useReducedMotion } from "framer-motion";
import { Check, Copy } from "lucide-react";
import { useLayoutEffect, useRef, useState } from "react";
import { toast } from "sonner";
import { Segmented, XButton } from "@/components/app/kit";
import { Drawer, DrawerContent, DrawerDescription, DrawerTitle } from "@/components/ui/drawer";
import { cn } from "@/lib/utils";
import type { DailyReceipt } from "../../domain/types";
import { cssGradient, DEFAULT_GRADIENT, GRADIENTS, gradientName } from "./gradients";
import { receiptPlainText, type Variant } from "./receiptModel";
import { renderReceiptPng, saveBlob, shareReceiptFile } from "./receiptImage";
import { ReceiptPaper } from "./ReceiptPaper";
import { useReceiptDoc } from "./useReceiptDoc";
import { useReceiptStrings } from "./strings";

const ZOOM = 0.62;
const CARD_PAD = 44;

/** Bottom sheet after the tear (docs/DESIGN.md §7.6): the receipt on a chosen gradient, full or brief, then share / save / copy. */
export function ShareSheet({ receipt, open, onClose }: { receipt: DailyReceipt; open: boolean; onClose: () => void }) {
  const t = useReceiptStrings();
  const [variant, setVariant] = useState<Variant>("full");
  const [gradient, setGradient] = useState(DEFAULT_GRADIENT);
  const [busy, setBusy] = useState<null | "share" | "save">(null);
  const doc = useReceiptDoc(receipt, variant);
  const variants = [
    { value: "full" as const, label: t("layout.full") },
    { value: "brief" as const, label: t("layout.brief") },
  ];
  const reduce = useReducedMotion() ?? false;
  const [paperBox, setPaperBox] = useState<HTMLDivElement | null>(null);
  const cardH = useMotionValue(0);
  const seen = useRef(false);

  // the card follows the paper's height with a spring when switching full / brief
  useLayoutEffect(() => {
    const h = paperBox?.offsetHeight;
    if (!h) return;
    const target = h * ZOOM + CARD_PAD;
    if (!seen.current || reduce) cardH.set(target);
    else void animate(cardH, target, { type: "spring", bounce: 0.15, duration: 0.5 });
    seen.current = true;
  }, [doc, paperBox, reduce, cardH]);

  const copy = async () => {
    try {
      await navigator.clipboard.writeText(receiptPlainText(doc));
      toast.success(t("share.copied"));
    } catch {
      toast.error(t("share.copyFailed"));
    }
  };

  const run = async (kind: "share" | "save") => {
    if (busy) return;
    setBusy(kind);
    try {
      const blob = await renderReceiptPng(doc, gradient);
      if (kind === "share") {
        const outcome = await shareReceiptFile(blob, t("share.fileTitle"));
        if (outcome === "shared") toast.success(t("share.shared"));
        else if (outcome === "fallback") {
          saveBlob(blob);
          toast(t("share.fallback"));
        }
      } else {
        saveBlob(blob);
        toast.success(t("share.saved"));
      }
    } catch {
      toast.error(t("share.failed"));
    } finally {
      setBusy(null);
    }
  };

  return (
    <Drawer open={open} onOpenChange={(o) => !o && onClose()}>
      <DrawerContent className="mx-auto max-h-[94dvh] max-w-[430px] rounded-t-[20px] border-0 bg-bg outline-none">
        <div className="overflow-y-auto px-4 pb-[max(1rem,env(safe-area-inset-bottom))] pt-1">
          <div className="flex h-12 items-center justify-between">
            <DrawerTitle className="font-serif text-[20px] font-semibold leading-none text-ink">{t("share.title")}</DrawerTitle>
            <button type="button" onClick={copy} className="-mr-2 flex h-11 items-center gap-1.5 rounded-[10px] px-2 text-[15px] text-clay active:opacity-60">
              <Copy className="size-4" strokeWidth={1.75} aria-hidden />
              {t("share.copy")}
            </button>
          </div>
          <DrawerDescription className="sr-only">{t("share.intro")}</DrawerDescription>

          <motion.div className="relative flex justify-center overflow-hidden rounded-[16px] pt-[22px]" style={{ backgroundImage: cssGradient(gradient), height: cardH }}>
            <div ref={setPaperBox} className="h-fit [filter:drop-shadow(0_8px_14px_rgb(0_0_0/0.22))]" style={{ transform: `scale(${ZOOM})`, transformOrigin: "50% 0" }}>
              <ReceiptPaper doc={doc} stamp="shown" tickerRun instant />
            </div>
          </motion.div>

          <div role="radiogroup" aria-label={t("share.backdrop")} className="-mx-4 mt-3 flex gap-1 overflow-x-auto px-4 pb-1 [scrollbar-width:none]">
            {GRADIENTS.map((g) => {
              const on = g.id === gradient.id;
              return (
                <button
                  key={g.id}
                  type="button"
                  role="radio"
                  aria-checked={on}
                  aria-label={gradientName(g)}
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

          <Segmented className="mt-2" value={variant} options={variants} onChange={setVariant} />

          <div className="mt-4 flex gap-2.5">
            <XButton variant="primary" className="flex-1" onClick={() => void run("share")} disabled={busy !== null}>
              {busy === "share" ? t("share.shareBusy") : t("share.share")}
            </XButton>
            <XButton variant="secondary" className="flex-1" onClick={() => void run("save")} disabled={busy !== null}>
              {busy === "save" ? t("share.saveBusy") : t("share.save")}
            </XButton>
          </div>
        </div>
      </DrawerContent>
    </Drawer>
  );
}
