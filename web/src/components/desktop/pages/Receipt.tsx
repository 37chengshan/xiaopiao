import { ChevronLeft, ChevronRight, Download, Printer } from "lucide-react";
import { AnimatePresence, motion, useReducedMotion } from "framer-motion";
import { useMemo, useState } from "react";
import { toast } from "sonner";
import { ReceiptPrinterStage } from "@/features/receipt/ReceiptPrinterStage";
import { GRADIENTS, cssGradient, gradientById, gradientName } from "@/features/receipt/gradients";
import { renderReceiptPng } from "@/features/receipt/receiptImage";
import { startOfDay } from "@/domain/types";
import { addDays } from "@/logic/time";
import { useLedger } from "@/services/LedgerProvider";
import { cn } from "@/lib/utils";
import { useLocale } from "@/lib/strings";
import { downloadBlob } from "../csvExport";
import { relativeDay } from "../format";
import { useDayDoc } from "../receiptDoc";
import { Button, Card, Hint, Segmented, Toggle } from "../ui";
import { s } from "../strings";
import { useShell } from "../Shell";
import type { Variant } from "@/features/receipt/receiptModel";

/** The five paper themes offered on the web (the phone keeps the full set). */
const THEMES = ["terracotta", "moss", "mist", "dusk", "midnight"] as const;

export function ReceiptPage() {
  useLocale();
  const reduce = useReducedMotion() ?? false;
  const { now } = useLedger();
  const shell = useShell();
  const today = startOfDay(now);
  const [day, setDay] = useState(today);
  const [variant, setVariant] = useState<Variant>("full");
  const [groups, setGroups] = useState(true);
  const [stamp, setStamp] = useState(true);
  const [theme, setTheme] = useState<string>(THEMES[0]);
  const [exporting, setExporting] = useState(false);

  const doc = useDayDoc(day, variant);
  const shown = useMemo(() => ({ ...doc, groups: groups ? doc.groups : [], badgeReason: stamp ? doc.badgeReason : null }), [doc, groups, stamp]);

  const exportPng = async () => {
    setExporting(true);
    try {
      const blob = await renderReceiptPng(shown, gradientById(theme));
      downloadBlob(`xiaopiao-${new Date(day).toISOString().slice(0, 10)}.png`, blob);
      toast(s("rc.exported"));
    } catch {
      toast.error(s("common.error"));
    } finally {
      setExporting(false);
    }
  };

  return (
    <div className="grid grid-cols-12 gap-5">
      <Card className="col-span-12 xl:col-span-7" padded={false}>
        <div className="flex items-center justify-between border-b border-d-hairline px-4 py-2">
          <div className="flex items-center gap-1">
            <button
              type="button"
              aria-label={s("rc.prevDay")}
              onClick={() => setDay((d) => addDays(d, -1))}
              className="d-press grid size-7 place-items-center rounded-md text-d-ink-2 hover:bg-d-fill hover:text-d-ink"
            >
              <ChevronLeft className="size-4" strokeWidth={2} aria-hidden />
            </button>
            <span className="min-w-[128px] text-center text-[12.5px] font-medium text-d-ink">{relativeDay(day, now)}</span>
            <button
              type="button"
              aria-label={s("rc.nextDay")}
              disabled={day >= today}
              onClick={() => setDay((d) => Math.min(today, addDays(d, 1)))}
              className="d-press grid size-7 place-items-center rounded-md text-d-ink-2 hover:bg-d-fill hover:text-d-ink disabled:opacity-30"
            >
              <ChevronRight className="size-4" strokeWidth={2} aria-hidden />
            </button>
            {day !== today && (
              <Button size="sm" variant="ghost" onClick={() => setDay(today)}>
                {s("common.today")}
              </Button>
            )}
          </div>
          <Hint>{s("rc.preview")}</Hint>
        </div>

        <div className="relative flex flex-col items-center rounded-b-xl bg-d-fill/40 px-4 pb-7 pt-6">
          <AnimatePresence mode="wait" initial={false}>
            <motion.div
              key={day}
              initial={reduce ? false : { opacity: 0, y: 6 }}
              animate={{ opacity: 1, y: 0 }}
              exit={reduce ? { opacity: 1 } : { opacity: 0, y: -6 }}
              transition={{ duration: 0.16, ease: [0.23, 1, 0.32, 1] }}
            >
              {/* The sheet the printer holds for this day: same ritual as the phone (docs/DESIGN.md §7). */}
              <ReceiptPrinterStage doc={shown} stamped={stamp} />
            </motion.div>
          </AnimatePresence>
          {doc.zero && <p className="mt-3 text-[12px] text-d-ink-3">{s("rc.empty")}</p>}
        </div>
      </Card>

      <div className="col-span-12 flex flex-col gap-5 xl:col-span-5">
        <Card title={s("rc.theme")}>
          <div className="flex flex-wrap gap-2">
            {GRADIENTS.filter((g) => (THEMES as readonly string[]).includes(g.id)).map((g) => (
              <button
                key={g.id}
                type="button"
                aria-label={gradientName(g)}
                aria-pressed={theme === g.id}
                onClick={() => setTheme(g.id)}
                className={cn(
                  "d-press grid size-9 place-items-center rounded-lg ring-offset-2 ring-offset-d-surface",
                  theme === g.id && "ring-2 ring-d-accent-ink",
                )}
                style={{ background: cssGradient(g) }}
              >
                <span className="text-[10px] font-medium text-white/90">{gradientName(g).slice(0, 1)}</span>
              </button>
            ))}
          </div>
        </Card>

        <Card title={s("rc.content")}>
          <div className="space-y-3">
            <div className="flex items-center justify-between gap-3">
              <span className="text-[12.5px] text-d-ink-2">{s("rc.variant")}</span>
              <Segmented
                value={variant}
                onChange={setVariant}
                label={s("rc.variant")}
                size="sm"
                options={[
                  { value: "full", label: s("rc.full") },
                  { value: "brief", label: s("rc.brief") },
                ]}
              />
            </div>
            <div className="flex items-center justify-between gap-3">
              <span className="text-[12.5px] text-d-ink-2">{s("rc.groups")}</span>
              <Toggle checked={groups} onChange={setGroups} label={s("rc.groups")} />
            </div>
            <div className="flex items-center justify-between gap-3">
              <span className="text-[12.5px] text-d-ink-2">{s("rc.stamp")}</span>
              <Toggle checked={stamp} onChange={setStamp} label={s("rc.stamp")} />
            </div>
          </div>
        </Card>

        <Card>
          <div className="flex flex-wrap items-center gap-2">
            <Button variant="primary" icon={Printer} onClick={() => shell.openPrint(shown)}>
              {s("rc.printPaper")}
            </Button>
            <Button icon={Download} onClick={() => void exportPng()} disabled={exporting}>
              {s("rc.export")}
            </Button>
            <span className="text-[11.5px] text-d-ink-3">{s("rc.printHint")}</span>
          </div>
        </Card>
      </div>
    </div>
  );
}
