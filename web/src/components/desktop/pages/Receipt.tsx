import { ChevronLeft, ChevronRight, Download, Printer } from "lucide-react";
import { useMemo, useState } from "react";
import { toast } from "sonner";
import { ReceiptPaper } from "@/features/receipt/ReceiptPaper";
import { GRADIENTS, cssGradient, gradientById } from "@/features/receipt/gradients";
import { renderReceiptPng } from "@/features/receipt/receiptImage";
import { startOfDay } from "@/domain/types";
import { addDays } from "@/logic/time";
import { useLedger } from "@/services/LedgerProvider";
import { cn } from "@/lib/utils";
import { downloadBlob } from "../csvExport";
import { relativeDay } from "../format";
import { useDayDoc } from "../receiptDoc";
import { Button, Card, Hint, Segmented, Toggle } from "../ui";
import { s } from "../strings";
import { useShell } from "../Shell";
import type { Variant } from "@/features/receipt/receiptModel";

/** The five paper themes offered on the web (the phone keeps the full set). */
const THEMES = ["terracotta", "moss", "mist", "dusk", "midnight"] as const;

/** Line-art thermal printer, 1.5px strokes and token fills so it follows the theme. */
function PrinterArt() {
  return (
    <svg viewBox="0 0 360 168" className="w-full max-w-[320px] text-d-ink" fill="none" stroke="currentColor" strokeWidth={1.5} strokeLinecap="round" aria-hidden>
      <rect x="34" y="54" width="292" height="92" rx="14" fill="var(--d-surface)" />
      <path d="M112 66h136" stroke="var(--d-hairline)" />
      <rect x="50" y="84" width="46" height="7" rx="3.5" fill="var(--d-fill)" stroke="none" />
      <rect x="252" y="84" width="46" height="7" rx="3.5" fill="var(--d-fill)" stroke="none" />
      <circle cx="304" cy="70" r="3.5" fill="var(--d-accent)" stroke="none" />
      <path d="M64 146v9M296 146v9" />
    </svg>
  );
}

export function ReceiptPage() {
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

        <div className="relative flex flex-col items-center rounded-b-xl bg-d-fill/40 px-4 pb-8 pt-6">
          <div className="relative z-0 -mb-1">
            <PrinterArt />
          </div>
          <div className="relative z-10 shadow-[0_10px_26px_oklch(0_0_0/16%)]">
            <ReceiptPaper doc={shown} stamp={stamp ? "shown" : "hidden"} />
          </div>
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
                aria-label={g.name}
                aria-pressed={theme === g.id}
                onClick={() => setTheme(g.id)}
                className={cn(
                  "d-press grid size-9 place-items-center rounded-lg ring-offset-2 ring-offset-d-surface",
                  theme === g.id && "ring-2 ring-d-accent-ink",
                )}
                style={{ background: cssGradient(g) }}
              >
                <span className="text-[10px] font-medium text-white/90">{g.name.slice(0, 1)}</span>
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
              {s("rc.print")}
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
