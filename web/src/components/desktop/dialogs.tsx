import * as DialogPrimitive from "@radix-ui/react-dialog";
import { motion, useAnimationControls } from "framer-motion";
import { createPortal } from "react-dom";
import { Printer } from "lucide-react";
import { useEffect, useLayoutEffect, useRef, useState, type ReactNode } from "react";
import { toast } from "sonner";
import { ReceiptPaper } from "@/features/receipt/ReceiptPaper";
import { type ReceiptDoc } from "@/features/receipt/receiptModel";
import { PAPER_W } from "@/features/receipt/zigzag";
import { CATEGORIES, type Category, type Direction, type Txn, startOfDay } from "@/domain/types";
import { useLedger } from "@/services/LedgerProvider";
import { at } from "@/logic/time";
import { parseDateKey, dateKey } from "@/logic/time";
import { cn } from "@/lib/utils";
import { SPRING_SHEET } from "./motion";
import { s } from "./strings";
import { Button, Field, Segmented, TextInput, inputClass } from "./ui";

const OVERLAY =
  "fixed inset-0 z-50 bg-black/25 data-[state=open]:animate-in data-[state=open]:fade-in-0 data-[state=open]:duration-[180ms] data-[state=closed]:animate-out data-[state=closed]:fade-out-0 data-[state=closed]:duration-[140ms]";
const CONTENT =
  "fixed left-1/2 top-1/2 z-50 max-h-[90vh] -translate-x-1/2 -translate-y-1/2 overflow-y-auto rounded-[14px] bg-d-surface p-5 shadow-[0_24px_70px_-16px_oklch(0_0_0/35%)] outline-none data-[state=open]:animate-in data-[state=open]:fade-in-0 data-[state=open]:zoom-in-[0.96] data-[state=open]:duration-[180ms] data-[state=closed]:animate-out data-[state=closed]:fade-out-0 data-[state=closed]:zoom-out-[0.96] data-[state=closed]:duration-[140ms]";

export function Modal({ open, onOpenChange, label, width = 480, children }: { open: boolean; onOpenChange: (v: boolean) => void; label: string; width?: number; children: ReactNode }) {
  return (
    <DialogPrimitive.Root open={open} onOpenChange={onOpenChange}>
      <DialogPrimitive.Portal>
        <DialogPrimitive.Overlay className={OVERLAY} />
        <DialogPrimitive.Content className={CONTENT} style={{ width: `min(92vw, ${width}px)` }} aria-label={label}>
          <DialogPrimitive.Title className="sr-only">{label}</DialogPrimitive.Title>
          {children}
        </DialogPrimitive.Content>
      </DialogPrimitive.Portal>
    </DialogPrimitive.Root>
  );
}

/* --------------------------------------------------------------- print */

const MM_TO_PX = 96 / 25.4;

/** Thermal paper widths: 58mm is the small roll, 80mm the wide one. */
export const PAPER_MM = { "58": 58, "80": 80 } as const;
export type PaperWidth = keyof typeof PAPER_MM;

export function PrintDialog({
  open,
  onOpenChange,
  doc,
  origin,
}: {
  open: boolean;
  onOpenChange: (v: boolean) => void;
  doc: ReceiptDoc;
  /** rect of the toolbar receipt control, so the sheet appears to grow out of it */
  origin?: DOMRect | null;
}) {
  const [width, setWidth] = useState<PaperWidth>("80");
  const paperRef = useRef<HTMLDivElement>(null);
  const paperBox = useRef<HTMLDivElement>(null);
  const sheet = useAnimationControls();

  // Grow from the widget's rect on open (a plain transform, no cross-tree layout animation).
  useLayoutEffect(() => {
    const box = paperBox.current;
    if (!box) return;
    if (!origin) {
      sheet.set({ opacity: 0, scale: 0.97 });
      void sheet.start({ opacity: 1, scale: 1, transition: { duration: 0.18, ease: [0.23, 1, 0.32, 1] } });
      return;
    }
    const target = box.getBoundingClientRect();
    const scale = Math.max(0.06, origin.width / target.width);
    sheet.set({
      opacity: 0.4,
      scale,
      x: origin.left + origin.width / 2 - (target.left + target.width / 2),
      y: origin.top + origin.height / 2 - (target.top + target.height / 2),
    });
    void sheet.start({ opacity: 1, scale: 1, x: 0, y: 0, transition: SPRING_SHEET });
  }, [origin, sheet]);

  const start = () => {
    const mm = PAPER_MM[width];
    const pxWidth = mm * MM_TO_PX;
    const scale = pxWidth / PAPER_W;
    const heightPx = paperRef.current?.offsetHeight ?? 600;
    const heightMm = Math.ceil((heightPx * scale) / MM_TO_PX) + 2;
    const style = document.createElement("style");
    style.id = "d-print-page";
    style.textContent = `@page { size: ${mm}mm ${heightMm}mm; margin: 0; }`;
    document.head.append(style);
    const cleanup = () => {
      style.remove();
      window.removeEventListener("afterprint", cleanup);
    };
    window.addEventListener("afterprint", cleanup);
    window.print();
    window.setTimeout(cleanup, 4000);
  };

  return (
    <>
      <Modal open={open} onOpenChange={onOpenChange} label={s("pr.title")} width={440}>
        <h2 className="text-[15px] font-semibold text-d-ink">{s("pr.title")}</h2>
        <div className="mt-3 flex justify-center rounded-xl bg-d-fill/60 px-4 py-5">
          <motion.div ref={paperBox} animate={sheet} className="shadow-[0_6px_18px_oklch(0_0_0/12%)]">
            <div ref={paperRef}>
              <ReceiptPaper doc={doc} stamp="shown" />
            </div>
          </motion.div>
        </div>
        <div className="mt-4 flex items-center justify-between gap-3">
          <div className="flex items-center gap-2">
            <span className="text-[12px] font-medium text-d-ink-2">{s("pr.width")}</span>
            <Segmented
              value={width}
              onChange={setWidth}
              label={s("pr.width")}
              options={[
                { value: "58", label: s("pr.58") },
                { value: "80", label: s("pr.80") },
              ]}
            />
          </div>
          <div className="flex items-center gap-2">
            <Button variant="quiet" onClick={() => onOpenChange(false)}>
              {s("common.cancel")}
            </Button>
            <Button variant="primary" icon={Printer} onClick={start}>
              {s("pr.go")}
            </Button>
          </div>
        </div>
        <p className="mt-3 text-[11.5px] leading-[16px] text-d-ink-3">{s("pr.hint")}</p>
      </Modal>

      {/* The printed sheet: everything else is hidden in print, so the paper lands on its own page. */}
      {createPortal(
        <div id="d-print-root" className="d-print-root">
          <div style={{ transform: `scale(${(PAPER_MM[width] * MM_TO_PX) / PAPER_W})`, transformOrigin: "top left", width: PAPER_W }}>
            <ReceiptPaper doc={doc} stamp="shown" />
          </div>
        </div>,
        document.body,
      )}
    </>
  );
}

/* --------------------------------------------------------------- entry */

const CATEGORY_LIST = Object.keys(CATEGORIES) as Category[];

export function EntryDialog({ open, onOpenChange, initial }: { open: boolean; onOpenChange: (v: boolean) => void; initial?: Txn | null }) {
  const { add, update } = useLedger();
  const [direction, setDirection] = useState<Direction>("OUT");
  const [amount, setAmount] = useState("");
  const [merchant, setMerchant] = useState("");
  const [category, setCategory] = useState<Category>("MEAL");
  const [day, setDay] = useState(dateKey(Date.now()));
  const [note, setNote] = useState("");
  const [saving, setSaving] = useState(false);

  // Re-seed whenever the dialog opens (new entry, or an existing row being edited).
  useEffect(() => {
    if (!open) return;
    setDirection(initial?.direction ?? "OUT");
    setAmount(initial ? (initial.amountCents / 100).toFixed(2).replace(/\.00$/, "") : "");
    setMerchant(initial?.merchant ?? "");
    setCategory(initial?.category ?? "MEAL");
    setDay(dateKey(initial?.occurredAt ?? Date.now()));
    setNote(initial?.note ?? "");
  }, [open, initial]);

  const cents = Math.round(Number(amount) * 100);
  const valid = amount.trim() !== "" && Number.isFinite(cents) && cents > 0 && merchant.trim() !== "";

  const save = async () => {
    if (!valid || saving) return;
    setSaving(true);
    const dayStart = parseDateKey(day);
    // Keep the original clock time when editing; new entries land at midday unless it is today.
    const occurredAt = initial ? initial.occurredAt : dayStart === startOfDay(Date.now()) ? Date.now() : at(dayStart, 12);
    try {
      if (initial) {
        await update({ ...initial, amountCents: cents, direction, merchant: merchant.trim(), category, occurredAt, note: note.trim() || undefined });
      } else {
        await add([{ id: `manual-${Date.now()}`, amountCents: cents, direction, merchant: merchant.trim(), category, occurredAt, source: "MANUAL", note: note.trim() || undefined }]);
      }
      toast.success(s("en.saved"));
      onOpenChange(false);
    } catch {
      toast.error(s("common.error"));
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal open={open} onOpenChange={onOpenChange} label={initial ? s("en.edit") : s("en.title")} width={460}>
      <form
        onSubmit={(e) => {
          e.preventDefault();
          void save();
        }}
        className="flex flex-col gap-3.5"
      >
        <div className="flex items-center justify-between">
          <h2 className="text-[15px] font-semibold text-d-ink">{initial ? s("en.edit") : s("en.title")}</h2>
          <Segmented
            value={direction}
            onChange={setDirection}
            label={s("en.out")}
            size="sm"
            options={[
              { value: "OUT", label: s("en.out") },
              { value: "IN", label: s("en.in") },
            ]}
          />
        </div>

        <label className="flex flex-col gap-1">
          <span className="text-[12px] font-medium text-d-ink-2">{s("en.amount")}</span>
          <div className="flex items-baseline gap-1.5">
            <span className="text-[20px] font-medium text-d-ink-3">¥</span>
            <input
              autoFocus
              value={amount}
              onChange={(e) => setAmount(e.target.value.replace(/[^\d.]/g, ""))}
              inputMode="decimal"
              placeholder="0.00"
              aria-label={s("en.amount")}
              className="num w-full min-w-0 bg-transparent text-[24px] font-semibold tracking-[-0.01em] text-d-ink outline-none placeholder:text-d-ink-3"
            />
          </div>
          <span className="h-px bg-d-hairline" />
        </label>

        <div className="grid grid-cols-2 gap-3">
          <Field label={s("en.merchant")}>
            <TextInput value={merchant} onChange={(e) => setMerchant(e.target.value)} placeholder={s("en.merchant")} aria-label={s("en.merchant")} />
          </Field>
          <Field label={s("en.date")}>
            <input type="date" value={day} max={dateKey(Date.now())} onChange={(e) => setDay(e.target.value)} aria-label={s("en.date")} className={inputClass} />
          </Field>
        </div>

        <div className="flex flex-col gap-1.5">
          <span className="text-[12px] font-medium text-d-ink-2">{s("en.category")}</span>
          <div role="radiogroup" aria-label={s("en.category")} className="grid grid-cols-4 gap-1.5">
            {CATEGORY_LIST.map((key) => {
              const on = key === category;
              return (
                <button
                  key={key}
                  type="button"
                  role="radio"
                  aria-checked={on}
                  onClick={() => setCategory(key)}
                  className={cn(
                    "d-press flex h-7 items-center gap-1 truncate rounded-lg px-2 text-[12px] transition-colors",
                    on ? "bg-d-accent/10 text-d-accent" : "bg-d-fill text-d-ink-2 hover:text-d-ink",
                  )}
                >
                  <span aria-hidden>{CATEGORIES[key].emoji}</span>
                  <span className="truncate">{CATEGORIES[key].label}</span>
                </button>
              );
            })}
          </div>
        </div>

        <Field label={s("en.note")}>
          <TextInput value={note} onChange={(e) => setNote(e.target.value)} placeholder={s("en.note")} aria-label={s("en.note")} />
        </Field>

        <div className="mt-1 flex items-center justify-end gap-2">
          {!valid && amount !== "" && <span className="mr-auto text-[11.5px] text-d-ink-3">{s("en.invalid")}</span>}
          <Button variant="quiet" onClick={() => onOpenChange(false)}>
            {s("common.cancel")}
          </Button>
          <Button type="submit" variant="primary" disabled={!valid || saving}>
            {s("common.save")}
          </Button>
        </div>
      </form>
    </Modal>
  );
}
