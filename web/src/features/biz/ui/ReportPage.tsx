import { useMemo, useState } from "react";
import { ChevronLeft, ChevronRight, Download, Printer } from "lucide-react";
import { addDays, dateKey, money, shortDate, weekdayIndex } from "@/logic/biz/format";
import { averageTicket, entryFor, weekToDateChange } from "@/logic/biz/book";
import { reportFact } from "@/logic/biz/summary";
import { buildReceipt, type ReceiptMm } from "../receipt";
import { WEEKDAY_KEYS, useBizStrings } from "../strings";
import { bookActions, useBizBook } from "../store";
import { reportLine } from "../summaryText";
import { useNotify } from "./feedback";
import { exportSlipPng, printSlip, ReceiptSlip } from "./ReceiptSlip";
import { BizPage, Button, Card, Segmented } from "./primitives";
import "../print.css";

type Variant = "full" | "brief";

/** `/app/biz/report` — one day, one slip: preview it, print it on 58/80mm, or save it as a PNG. */
export function ReportPage() {
  const t = useBizStrings();
  const { book, today, reportDay } = useBizBook();
  const [mm, setMm] = useState<ReceiptMm>(80);
  const [variant, setVariant] = useState<Variant>("full");
  const [showOrders, setShowOrders] = useState(true);
  const [showTicket, setShowTicket] = useState(true);
  const [showWeek, setShowWeek] = useState(true);
  const [showSummary, setShowSummary] = useState(true);
  const notify = useNotify();

  const entry = entryFor(book, reportDay);
  const day = reportDay;

  const spec = useMemo(() => {
    const fact = reportFact(book, day);
    const week = weekToDateChange(book, day);
    return buildReceipt({
      mm,
      shopName: t("shop.name"),
      day,
      revenueCents: fact.revenueCents,
      orders: fact.orders,
      ticketCents: averageTicket(fact.revenueCents, fact.orders),
      weekCents: week.nowCents,
      summary: reportLine(fact, t),
      variant,
      showOrders: variant === "brief" ? false : showOrders,
      showTicket: variant === "brief" ? false : showTicket,
      showWeek: variant === "brief" ? false : showWeek,
      showSummary: variant === "brief" ? true : showSummary,
      labels: {
        head: t("report.head"),
        revenue: t("report.revenue"),
        orders: t("report.orders"),
        ticket: t("report.ticket"),
        week: t("report.week"),
        issued: t("report.issued"),
        thanks: t("report.thanks"),
        ordersUnit: t("unit.orders"),
        weekday: t(WEEKDAY_KEYS[weekdayIndex(day)]),
        summaryFallback: t("report.noEntryHint"),
      },
      issuedAt: new Date().toLocaleString("zh-CN", { hour12: false }),
    });
  }, [book, day, mm, variant, showOrders, showTicket, showWeek, showSummary, t]);

  const slip = <ReceiptSlip spec={spec} label={`${t("report.head")} ${shortDate(day)}`} />;
  const earliest = book.length > 0 ? book[0].day : today;

  return (
    <BizPage>
      <div className="grid grid-cols-12 gap-5 print:hidden">
        <Card
          className="col-span-12 lg:col-span-7"
          index={0}
          bodyClassName="flex items-center justify-center py-8"
          title={t("report.title")}
        >
          <div className="flex flex-col items-center gap-4">
            <div className="flex items-center gap-2">
              <button
                type="button"
                aria-label={t("report.prev")}
                onClick={() => bookActions.setReportDay(addDays(day, -1))}
                disabled={day <= earliest}
                className="grid size-7 place-items-center rounded-md bg-d-fill text-d-ink-2 transition-colors hover:text-d-ink disabled:opacity-40"
              >
                <ChevronLeft className="size-4" strokeWidth={1.75} aria-hidden />
              </button>
              <span className="min-w-[104px] text-center text-[13px] font-medium tabular-nums">
                {shortDate(day)} {t(WEEKDAY_KEYS[weekdayIndex(day)])}
              </span>
              <button
                type="button"
                aria-label={t("report.next")}
                onClick={() => bookActions.setReportDay(addDays(day, 1))}
                disabled={day >= today}
                className="grid size-7 place-items-center rounded-md bg-d-fill text-d-ink-2 transition-colors hover:text-d-ink disabled:opacity-40"
              >
                <ChevronRight className="size-4" strokeWidth={1.75} aria-hidden />
              </button>
            </div>
            <div className="flex justify-center">{slip}</div>
            {!entry && (
              <p className="text-[12px] text-d-ink-2">
                {t("report.noEntry")} · {t("report.noEntryHint")}
              </p>
            )}
          </div>
        </Card>

        <div className="col-span-12 space-y-5 lg:col-span-5">
          <Card title={t("report.settings")} index={1}>
            <div className="space-y-4">
              <Row label={t("report.size")}>
                <Segmented<`${ReceiptMm}`>
                  label={t("report.size")}
                  value={`${mm}`}
                  onChange={(value) => setMm(value === "58" ? 58 : 80)}
                  options={[
                    { value: "58", label: "58mm" },
                    { value: "80", label: "80mm" },
                  ]}
                />
              </Row>
              <Row label={t("report.layout")}>
                <Segmented<Variant>
                  label={t("report.layout")}
                  value={variant}
                  onChange={setVariant}
                  options={[
                    { value: "full", label: t("report.full") },
                    { value: "brief", label: t("report.brief") },
                  ]}
                />
              </Row>
              <div className="space-y-2 border-t border-d-hairline pt-3">
                <Toggle label={t("report.showOrders")} checked={showOrders} onChange={setShowOrders} disabled={variant === "brief"} />
                <Toggle label={t("report.showTicket")} checked={showTicket} onChange={setShowTicket} disabled={variant === "brief"} />
                <Toggle label={t("report.showWeek")} checked={showWeek} onChange={setShowWeek} disabled={variant === "brief"} />
                <Toggle label={t("report.showSummary")} checked={showSummary} onChange={setShowSummary} />
              </div>
            </div>
          </Card>

          <Card title={`${shortDate(day)} · ${money(entry?.revenueCents ?? 0)}`} index={2}>
            <div className="flex flex-wrap gap-2">
              <Button
                tone="solid"
                onClick={() => {
                  printSlip(spec);
                  notify.printed();
                }}
              >
                <Printer className="size-3.5" strokeWidth={1.75} aria-hidden />
                {t("report.print")}
              </Button>
              <Button
                tone="fill"
                onClick={() => {
                  const svg = document.querySelector<SVGSVGElement>(".biz-slip");
                  if (!svg) return;
                  void exportSlipPng(svg, spec, `xiaopiao-report-${dateKey(day)}.png`).then(
                    () => notify.imageSaved(),
                    () => notify.failed(t("banner.importFailed")),
                  );
                }}
              >
                <Download className="size-3.5" strokeWidth={1.75} aria-hidden />
                {t("report.export")}
              </Button>
            </div>
            <p className="mt-3 text-[11px] leading-4 text-d-ink-3">{t("report.titleHint")}</p>
          </Card>
        </div>
      </div>

      {/* Print sheet: the only thing on the page when the printer dialog opens. */}
      <div className="biz-print-sheet hidden">
        <ReceiptSlip spec={spec} label={`${t("report.head")} ${shortDate(day)}`} />
      </div>
    </BizPage>
  );
}

function Row({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="flex items-center justify-between gap-3">
      <span className="text-[12px] font-medium text-d-ink-2">{label}</span>
      {children}
    </div>
  );
}

function Toggle({
  label,
  checked,
  onChange,
  disabled,
}: {
  label: string;
  checked: boolean;
  onChange: (next: boolean) => void;
  disabled?: boolean;
}) {
  return (
    <label className="flex cursor-pointer items-center justify-between gap-3 text-[13px] text-d-ink">
      <span>{label}</span>
      <input
        type="checkbox"
        checked={checked}
        disabled={disabled}
        onChange={(e) => onChange(e.target.checked)}
        className="size-3.5 accent-[var(--d-accent-ink)] disabled:opacity-40"
      />
    </label>
  );
}
