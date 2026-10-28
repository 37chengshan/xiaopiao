import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { Download, Plus, Trash2, Undo2, Upload } from "lucide-react";
import {
  addDays,
  centsToInput,
  dateKey,
  money,
  parseMoneyText,
  shortDate,
  weekdayIndex,
} from "@/logic/biz/format";
import {
  averageTicket,
  averageTicketOf,
  totalCents,
  totalOrders,
  type RevenueEntry,
} from "@/logic/biz/book";
import { exportRevenueCsv, type ImportPreview } from "@/logic/biz/import";
import { WEEKDAY_KEYS, useBizStrings } from "../strings";
import { bookActions, useBizBook } from "../store";
import { fill } from "../text";
import { readSpreadsheet } from "../fileImport";
import { AddDayDialog } from "./AddDayDialog";
import { ImportDialog } from "./ImportDialog";
import { BizPage, Button, Card, Chip, EmptyState, Segmented, SortHeader } from "./primitives";

type RangeKey = "7" | "14" | "30" | "all";
type FileErrorCode = "notXlsx" | "noSheet" | "unsupported" | "oldXls";
const FILE_ERRORS: readonly string[] = ["notXlsx", "noSheet", "unsupported", "oldXls"];
type SortKey = "day" | "revenueCents" | "orders";

/** `/app/biz/revenue` — the day-by-day book: import, amend, sort, export. */
export function RevenuePage() {
  const t = useBizStrings();
  const { book, today } = useBizBook();
  const [range, setRange] = useState<RangeKey>("14");
  const [sort, setSort] = useState<{ key: SortKey; desc: boolean }>({ key: "day", desc: true });
  const [selected, setSelected] = useState<Set<number>>(new Set());
  const [editing, setEditing] = useState<{ day: number; value: string } | null>(null);
  const [undo, setUndo] = useState<{ entries: RevenueEntry[]; label: string } | null>(null);
  const [importState, setImportState] = useState<{ name: string; text: string } | null>(null);
  const [addOpen, setAddOpen] = useState(false);
  const [dragging, setDragging] = useState(false);
  const [notice, setNotice] = useState<string | null>(null);
  const fileInput = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (!undo) return;
    const timer = window.setTimeout(() => setUndo(null), 5000);
    return () => window.clearTimeout(timer);
  }, [undo]);

  useEffect(() => {
    if (!notice) return;
    const timer = window.setTimeout(() => setNotice(null), 4000);
    return () => window.clearTimeout(timer);
  }, [notice]);

  const rows = useMemo(() => {
    const from = range === "all" ? Number.NEGATIVE_INFINITY : addDays(today, -(Number(range) - 1));
    const filtered = book.filter((e) => e.day >= from);
    const dir = sort.desc ? -1 : 1;
    return [...filtered].sort((a, b) => {
      const left = sort.key === "day" ? a.day : sort.key === "revenueCents" ? a.revenueCents : a.orders;
      const right = sort.key === "day" ? b.day : sort.key === "revenueCents" ? b.revenueCents : b.orders;
      return left === right ? b.day - a.day : (left - right) * dir;
    });
  }, [book, range, sort, today]);

  const totals = useMemo(
    () => ({ revenue: totalCents(rows), orders: totalOrders(rows), ticket: averageTicketOf(rows) }),
    [rows],
  );

  const toggleSort = (key: SortKey) =>
    setSort((prev) => (prev.key === key ? { key, desc: !prev.desc } : { key, desc: key !== "day" }));

  const onFiles = useCallback(
    async (files: FileList | null) => {
      const file = files?.[0];
      if (!file) return;
      try {
        const text = await readSpreadsheet(file);
        setImportState({ name: file.name, text });
      } catch (error) {
        const code = error instanceof Error ? error.message : "notXlsx";
        const detail = FILE_ERRORS.includes(code) ? t(`import.error.${code}` as `import.error.${FileErrorCode}`) : "";
        setNotice(detail ? `${t("import.failed")} · ${detail}` : t("import.failed"));
      }
    },
    [t],
  );

  const removeRows = (days: number[]) => {
    const entries = book.filter((e) => days.includes(e.day));
    bookActions.deleteDays(days);
    setSelected(new Set());
    setUndo({
      entries,
      label: days.length === 1 ? fill(t("revenue.deletedOne"), shortDate(days[0])) : fill(t("revenue.deletedMany"), `${days.length}`),
    });
  };

  const commitEdit = () => {
    if (!editing) return;
    const entry = book.find((e) => e.day === editing.day);
    const cents = parseMoneyText(editing.value);
    if (entry && cents !== null && cents >= 0 && cents !== entry.revenueCents) {
      bookActions.save({ ...entry, revenueCents: cents, source: "MANUAL" });
      setNotice(t("revenue.saved"));
    }
    setEditing(null);
  };

  const download = () => {
    const blob = new Blob([exportRevenueCsv(rows)], { type: "text/csv;charset=utf-8" });
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement("a");
    const stamp = dateKey(today);
    anchor.href = url;
    anchor.download = `xiaopiao-revenue-${stamp}.csv`;
    anchor.click();
    URL.revokeObjectURL(url);
    setNotice(fill(t("revenue.exported"), anchor.download));
  };

  const allSelected = rows.length > 0 && rows.every((e) => selected.has(e.day));

  return (
    <div
      className="relative min-h-full"
      onDragOver={(e) => {
        e.preventDefault();
        setDragging(true);
      }}
      onDragLeave={(e) => {
        if (e.currentTarget.contains(e.relatedTarget as Node | null)) return;
        setDragging(false);
      }}
      onDrop={(e) => {
        e.preventDefault();
        setDragging(false);
        void onFiles(e.dataTransfer.files);
      }}
    >
      <BizPage>
        <div className="grid grid-cols-12 gap-5">
          <div className="col-span-12 flex flex-wrap items-center justify-between gap-3">
            <Segmented<RangeKey>
              label={t("range.14")}
              value={range}
              onChange={setRange}
              options={[
                { value: "7", label: t("range.7") },
                { value: "14", label: t("range.14") },
                { value: "30", label: t("range.30") },
                { value: "all", label: t("range.all") },
              ]}
            />
            <div className="flex flex-wrap items-center gap-2">
              {notice && <span className="mr-1 text-[12px] text-d-ink-2">{notice}</span>}
              <Button tone="fill" onClick={() => fileInput.current?.click()}>
                <Upload className="size-3.5" strokeWidth={1.75} aria-hidden />
                {t("revenue.import")}
              </Button>
              <Button tone="solid" onClick={() => setAddOpen(true)}>
                <Plus className="size-3.5" strokeWidth={2} aria-hidden />
                {t("revenue.add")}
              </Button>
              <Button tone="fill" onClick={download} disabled={rows.length === 0}>
                <Download className="size-3.5" strokeWidth={1.75} aria-hidden />
                {t("revenue.export")}
              </Button>
            </div>
          </div>

          <Card className="col-span-12" bodyClassName="pt-4">
            <div className="grid grid-cols-2 gap-6 sm:grid-cols-4">
              <Stat label={t("kpi.entries")} value={`${rows.length}`} />
              <Stat label={t("revenue.total")} value={money(totals.revenue)} />
              <Stat label={t("revenue.col.orders")} value={`${totals.orders}`} />
              <Stat label={t("kpi.ticket")} value={money(totals.ticket)} />
            </div>
          </Card>

          <Card
            className="col-span-12"
            bodyClassName="px-0 pb-0 pt-0"
            title={t("revenue.col.revenue")}
            action={
              selected.size > 0 ? (
                <span className="flex items-center gap-2">
                  <span className="text-[12px] tabular-nums text-d-ink-2">{fill(t("revenue.selected"), `${selected.size}`)}</span>
                  <Button tone="fill" onClick={() => setSelected(new Set())}>
                    {t("revenue.clearSelection")}
                  </Button>
                  <Button tone="solid" onClick={() => removeRows([...selected])}>
                    <Trash2 className="size-3.5" strokeWidth={1.75} aria-hidden />
                    {t("revenue.deleteSelected")}
                  </Button>
                </span>
              ) : (
                <span className="text-[11px] text-d-ink-3">{t("revenue.editHint")}</span>
              )
            }
          >
            {rows.length === 0 ? (
              <EmptyState
                title={t("revenue.empty")}
                hint={t("revenue.emptyHint")}
                action={
                  <Button tone="solid" onClick={() => setAddOpen(true)}>
                    {t("revenue.add")}
                  </Button>
                }
              />
            ) : (
              <div className="max-h-[62vh] overflow-auto">
                <table className="w-full border-collapse text-[13px]">
                  <thead>
                    <tr className="border-b border-d-hairline">
                      <th scope="col" className="sticky top-0 z-10 w-9 bg-d-surface py-2 pl-4">
                        <input
                          type="checkbox"
                          aria-label={fill(t("revenue.selected"), `${rows.length}`)}
                          checked={allSelected}
                          onChange={() =>
                            setSelected(allSelected ? new Set() : new Set(rows.map((e) => e.day)))
                          }
                          className="size-3.5 accent-[var(--d-accent)]"
                        />
                      </th>
                      <SortHeader
                        label={t("revenue.col.date")}
                        active={sort.key === "day"}
                        descending={sort.desc}
                        onClick={() => toggleSort("day")}
                      />
                      <SortHeader
                        label={t("revenue.col.revenue")}
                        active={sort.key === "revenueCents"}
                        descending={sort.desc}
                        onClick={() => toggleSort("revenueCents")}
                        align="right"
                      />
                      <SortHeader
                        label={t("revenue.col.orders")}
                        active={sort.key === "orders"}
                        descending={sort.desc}
                        onClick={() => toggleSort("orders")}
                        align="right"
                      />
                      <th scope="col" className="sticky top-0 z-10 bg-d-surface px-2 py-2 text-right text-[12px] font-medium text-d-ink-2">
                        {t("revenue.col.ticket")}
                      </th>
                      <th scope="col" className="sticky top-0 z-10 bg-d-surface px-2 py-2 text-left text-[12px] font-medium text-d-ink-2">
                        {t("revenue.col.note")}
                      </th>
                      <th scope="col" className="sticky top-0 z-10 bg-d-surface px-2 py-2 text-left text-[12px] font-medium text-d-ink-2">
                        {t("revenue.col.source")}
                      </th>
                      <th scope="col" className="sticky top-0 z-10 bg-d-surface px-4 py-2" />
                    </tr>
                  </thead>
                  <tbody>
                    {rows.map((entry) => {
                      const checked = selected.has(entry.day);
                      const isEditing = editing?.day === entry.day;
                      return (
                        <tr
                          key={entry.day}
                          className={checked ? "bg-d-accent/[8%]" : "transition-colors hover:bg-d-fill/40"}
                        >
                          <td className="w-9 py-[9px] pl-4">
                            <input
                              type="checkbox"
                              aria-label={`${shortDate(entry.day)}`}
                              checked={checked}
                              onChange={() =>
                                setSelected((prev) => {
                                  const next = new Set(prev);
                                  if (next.has(entry.day)) next.delete(entry.day);
                                  else next.add(entry.day);
                                  return next;
                                })
                              }
                              className="size-3.5 accent-[var(--d-accent)]"
                            />
                          </td>
                          <td className="px-2 py-[9px] text-d-ink">
                            <span className="tabular-nums">{shortDate(entry.day)}</span>
                            <span className="ml-2 text-[11px] text-d-ink-2">{t(WEEKDAY_KEYS[weekdayIndex(entry.day)])}</span>
                          </td>
                          <td className="px-2 py-[9px] text-right tabular-nums">
                            {isEditing ? (
                              <input
                                autoFocus
                                value={editing.value}
                                onChange={(e) => setEditing({ day: entry.day, value: e.target.value })}
                                onBlur={commitEdit}
                                onKeyDown={(e) => {
                                  if (e.key === "Enter") commitEdit();
                                  if (e.key === "Escape") setEditing(null);
                                }}
                                className="h-7 w-24 rounded-md bg-d-surface px-1.5 text-right text-[13px] tabular-nums text-d-ink outline-none ring-2 ring-d-accent/50"
                              />
                            ) : (
                              <button
                                type="button"
                                onClick={() => setEditing({ day: entry.day, value: centsToInput(entry.revenueCents) })}
                                className="rounded px-1 text-d-ink transition-colors hover:bg-d-fill"
                                aria-label={`${t("revenue.edit")} ${shortDate(entry.day)}`}
                              >
                                {money(entry.revenueCents)}
                              </button>
                            )}
                          </td>
                          <td className="px-2 py-[9px] text-right tabular-nums text-d-ink-2">{entry.orders > 0 ? entry.orders : "—"}</td>
                          <td className="px-2 py-[9px] text-right tabular-nums text-d-ink-2">
                            {entry.orders > 0 ? money(averageTicket(entry.revenueCents, entry.orders)) : "—"}
                          </td>
                          <td className="max-w-[180px] truncate px-2 py-[9px] text-d-ink-2">{entry.note || "—"}</td>
                          <td className="px-2 py-[9px]">
                            <Chip tone={entry.source === "CSV" ? "accent" : "neutral"}>
                              {entry.source === "CSV"
                                ? t("revenue.source.csv")
                                : entry.source === "MANUAL"
                                  ? t("revenue.source.manual")
                                  : t("revenue.source.seed")}
                            </Chip>
                          </td>
                          <td className="px-4 py-[9px] text-right">
                            <button
                              type="button"
                              onClick={() => removeRows([entry.day])}
                              aria-label={`${t("revenue.delete")} ${shortDate(entry.day)}`}
                              className="rounded p-1 text-d-ink-3 transition-colors hover:bg-d-fill hover:text-d-negative"
                            >
                              <Trash2 className="size-3.5" strokeWidth={1.75} aria-hidden />
                            </button>
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            )}
          </Card>
        </div>
      </BizPage>

      {dragging && (
        <div className="pointer-events-none fixed inset-0 z-40 flex items-center justify-center bg-d-canvas/80">
          <div className="rounded-xl border-2 border-dashed border-d-accent px-10 py-8 text-[14px] font-medium text-d-accent">
            {t("revenue.drag")}
          </div>
        </div>
      )}

      {undo && (
        <div className="fixed bottom-6 left-1/2 z-40 flex -translate-x-1/2 items-center gap-3 rounded-xl bg-d-ink px-4 py-2 text-[12px] text-d-canvas shadow-[0_12px_32px_-12px_oklch(0_0_0/40%)]">
          <span>{undo.label}</span>
          <button
            type="button"
            onClick={() => {
              bookActions.restore(undo.entries);
              setUndo(null);
            }}
            className="inline-flex items-center gap-1 rounded-md px-1.5 py-0.5 font-medium text-d-canvas/90 transition-opacity hover:opacity-80"
          >
            <Undo2 className="size-3.5" strokeWidth={2} aria-hidden />
            {t("revenue.undo")}
          </button>
        </div>
      )}

      <input
        ref={fileInput}
        type="file"
        accept=".csv,.tsv,.txt,.xlsx,.xlsm,text/csv"
        className="hidden"
        onChange={(e) => {
          void onFiles(e.target.files);
          e.target.value = "";
        }}
      />

      {importState && (
        <ImportDialog
          open
          fileName={importState.name}
          text={importState.text}
          book={book}
          today={today}
          onClose={() => setImportState(null)}
          onApply={(preview: ImportPreview) => {
            bookActions.applyImport(preview);
            setNotice(
              fill(t("revenue.imported"), `${preview.counts.add + preview.counts.overwrite}`, `${preview.counts.skip}`),
            );
          }}
        />
      )}

      <AddDayDialog
        open={addOpen}
        today={today}
        day={today}
        onClose={() => setAddOpen(false)}
        onSave={(entry) => {
          bookActions.save(entry);
          setNotice(t("revenue.saved"));
        }}
      />
    </div>
  );
}

function Stat({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <p className="text-[12px] font-medium leading-4 text-d-ink-2">{label}</p>
      <p className="mt-1 text-[18px] font-semibold leading-6 tabular-nums">{value}</p>
    </div>
  );
}
