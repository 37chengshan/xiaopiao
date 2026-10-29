import * as PopoverPrimitive from "@radix-ui/react-popover";
import { Download, Filter, Plus, RefreshCw, Upload, X } from "lucide-react";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { toast } from "sonner";
import { notifyUndo } from "../toast";
import { CATEGORIES, type Category, type Source, type Txn, startOfDay } from "@/domain/types";
import { addDays, dateKey, hhmm, weekStartOf } from "@/logic/time";
import { decodeBillBytes, parseBillCsv } from "@/logic/csv";
import { useLedger } from "@/services/LedgerProvider";
import { cn } from "@/lib/utils";
import { useLocale } from "@/lib/strings";
import { DataTable, type Column } from "../DataTable";
import { downloadCsv } from "../csvExport";
import { money } from "../format";
import { useDebounced, useFirstPaint } from "../hooks";
import { Loadable } from "../motion";
import { Modal } from "../dialogs";
import {
  EMPTY_FILTERS,
  SOURCE_LABEL,
  activeFilterCount,
  filterTxns,
  retainVisible,
  segments,
  sortTxns,
  toggleAll,
  type DirectionFilter,
  type Filters,
  type Sort,
} from "../tableModel";
import { categoryLabel, sourceLabel } from "../labels";
import { Button, Card, Chip, EmptyState, Skeleton, TextInput, Segmented, SearchInput } from "../ui";
import { s } from "../strings";
import { useShell } from "../Shell";

const POPOVER = "z-50 w-[380px] max-w-[calc(100vw-32px)] rounded-xl bg-d-surface p-4 shadow-[0_18px_50px_-12px_oklch(0_0_0/30%)] outline-none";
const SOURCES: Source[] = ["MANUAL", "QUICK_ADD", "VOICE", "CSV", "DEMO"];

type TimePreset = "all" | "today" | "week" | "month" | "days30" | "custom";

function Highlight({ text, query }: { text: string; query: string }) {
  return (
    <>
      {segments(text, query).map((part, i) =>
        part.hit ? (
          <mark key={i} className="rounded-[2px] bg-d-accent/15 text-d-ink">
            {part.text}
          </mark>
        ) : (
          <span key={i}>{part.text}</span>
        ),
      )}
    </>
  );
}

export function LedgerPage() {
  useLocale();
  const { txns, now, isLoading, isError, refetch, add, update, remove, restore } = useLedger();
  const shell = useShell();
  const skeleton = useFirstPaint(isLoading);

  const [query, setQuery] = useState("");
  const debounced = useDebounced(query, 300);
  const [filters, setFilters] = useState<Filters>(EMPTY_FILTERS);
  const [sort, setSort] = useState<Sort>({ key: "occurredAt", dir: "desc" });
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [activeId, setActiveId] = useState<string | null>(null);
  const [filterOpen, setFilterOpen] = useState(false);
  const [bulkOpen, setBulkOpen] = useState(false);
  const [dragging, setDragging] = useState(false);
  const [preview, setPreview] = useState<{ rows: Txn[]; failed: number; duplicates: number } | null>(null);
  const fileRef = useRef<HTMLInputElement>(null);

  const rows = useMemo(() => sortTxns(filterTxns(txns, { ...filters, query: debounced }), sort), [txns, filters, debounced, sort]);
  const filterCount = activeFilterCount(filters);
  const selectedRows = useMemo(() => rows.filter((r) => selected.has(r.id)), [rows, selected]);

  // Selection never keeps rows that a filter just hid.
  useEffect(() => {
    setSelected((prev) => retainVisible(prev, rows.map((r) => r.id)));
  }, [rows]);

  const readFile = useCallback(
    async (file: File) => {
      try {
        const parsed = parseBillCsv(decodeBillBytes(await file.arrayBuffer()));
        const known = new Set(txns.map((t) => t.dedupeKey ?? t.id));
        const duplicates = parsed.txns.filter((t) => known.has(t.dedupeKey ?? t.id)).length;
        if (parsed.txns.length === 0) {
          toast(s("ledger.importEmpty"));
          return;
        }
        setPreview({ rows: parsed.txns, failed: parsed.failed, duplicates });
      } catch {
        toast.error(s("ledger.importEmpty"));
      }
    },
    [txns],
  );

  // Drop a bill anywhere on the page, not just on the button.
  useEffect(() => {
    let depth = 0;
    const enter = (e: DragEvent) => {
      if (!e.dataTransfer?.types.includes("Files")) return;
      e.preventDefault();
      depth++;
      setDragging(true);
    };
    const over = (e: DragEvent) => {
      if (!e.dataTransfer?.types.includes("Files")) return;
      e.preventDefault();
    };
    const leave = () => {
      depth = Math.max(0, depth - 1);
      if (depth === 0) setDragging(false);
    };
    const drop = (e: DragEvent) => {
      e.preventDefault();
      depth = 0;
      setDragging(false);
      const file = e.dataTransfer?.files?.[0];
      if (file) void readFile(file);
    };
    window.addEventListener("dragenter", enter);
    window.addEventListener("dragover", over);
    window.addEventListener("dragleave", leave);
    window.addEventListener("drop", drop);
    return () => {
      window.removeEventListener("dragenter", enter);
      window.removeEventListener("dragover", over);
      window.removeEventListener("dragleave", leave);
      window.removeEventListener("drop", drop);
    };
  }, [readFile]);

  const timePreset = (): TimePreset => {
    if (filters.from === null && filters.to === null) return "all";
    const today = startOfDay(now);
    if (filters.from === today && filters.to === addDays(today, 1)) return "today";
    if (filters.from === weekStartOf(now) && filters.to === addDays(today, 1)) return "week";
    if (filters.from !== null && filters.from === new Date(new Date(now).getFullYear(), new Date(now).getMonth(), 1).getTime() && filters.to === addDays(today, 1)) return "month";
    if (filters.from === addDays(today, -29) && filters.to === addDays(today, 1)) return "days30";
    return "custom";
  };

  const applyTime = (preset: TimePreset) => {
    const today = startOfDay(now);
    const set = (from: number | null, to: number | null) => setFilters((f) => ({ ...f, from, to }));
    if (preset === "all") set(null, null);
    else if (preset === "today") set(today, addDays(today, 1));
    else if (preset === "week") set(weekStartOf(now), addDays(today, 1));
    else if (preset === "month") set(new Date(new Date(now).getFullYear(), new Date(now).getMonth(), 1).getTime(), addDays(today, 1));
    else if (preset === "days30") set(addDays(today, -29), addDays(today, 1));
  };

  const deleteRows = async (targets: Txn[]) => {
    if (targets.length === 0) return;
    setSelected(new Set());
    await Promise.all(targets.map((t) => remove(t.id)));
    notifyUndo(`${s("ledger.deleted")} ${targets.length} ${s("ledger.itemUnit")}`, () => void Promise.all(targets.map(restore)));
  };

  const reclassify = async (category: Category) => {
    setBulkOpen(false);
    if (selectedRows.length === 0) return;
    const before = selectedRows.map((t) => ({ ...t }));
    await Promise.all(selectedRows.map((t) => update({ ...t, category })));
    setSelected(new Set());
    notifyUndo(s("ledger.reclassified"), () => void Promise.all(before.map((t) => update(t))));
  };

  const columns: Column<Txn>[] = [
    {
      key: "occurredAt",
      header: s("ledger.colTime"),
      sortKey: "occurredAt",
      width: "132px",
      cell: (t) => (
        <span className="num text-[12px] text-d-ink-3">
          {dateKey(t.occurredAt).slice(5)} {hhmm(t.occurredAt)}
        </span>
      ),
    },
    {
      key: "merchant",
      header: s("ledger.colMerchant"),
      sortKey: "merchant",
      cell: (t) => (
        <div className="flex min-w-0 items-center gap-2">
          <span aria-hidden className="grid size-5 shrink-0 place-items-center rounded bg-d-fill text-[11px]">{CATEGORIES[t.category].emoji}</span>
          <span className="truncate text-d-ink">
            <Highlight text={t.merchant} query={debounced} />
          </span>
          {t.note && <span className="truncate text-[11.5px] text-d-ink-3">{t.note}</span>}
        </div>
      ),
    },
    {
      key: "category",
      header: s("ledger.colCategory"),
      sortKey: "category",
      width: "128px",
      cell: (t) => <span className="text-[12px] text-d-ink-2">{categoryLabel(t.category)}</span>,
    },
    {
      key: "source",
      header: s("ledger.colSource"),
      sortKey: "source",
      width: "84px",
      cell: (t) => <span className="text-[12px] text-d-ink-3">{sourceLabel(t.source)}</span>,
    },
    {
      key: "amount",
      header: s("ledger.colAmount"),
      sortKey: "amountCents",
      align: "right",
      width: "128px",
      cell: (t) => (
        <span className={cn("num text-[13px] font-medium", t.direction === "IN" ? "text-d-positive" : "text-d-ink")}>
          {t.direction === "IN" ? `+${money(t.amountCents)}` : money(t.amountCents)}
        </span>
      ),
    },
  ];

  return (
    <div className={cn("relative flex min-h-0 flex-col gap-3", dragging && "d-drop")}>
      {dragging && (
        <div aria-hidden className="pointer-events-none fixed inset-0 z-30 grid place-items-center bg-d-canvas/60 backdrop-blur-[1px]">
          <span className="rounded-xl bg-d-ink px-4 py-2 text-[13px] font-medium text-d-canvas shadow-[0_10px_30px_-8px_oklch(0_0_0/40%)]">{s("ledger.drop")}</span>
        </div>
      )}
      <div className="flex flex-wrap items-center gap-2">
        <SearchInput
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder={s("ledger.search")}
          aria-label={s("ledger.search")}
          className="h-8 w-full sm:w-[280px]"
        />

        <PopoverPrimitive.Root open={filterOpen} onOpenChange={setFilterOpen}>
          <PopoverPrimitive.Trigger asChild>
            <Button variant={filterCount > 0 ? "quiet" : "quiet"} icon={Filter} className={cn(filterCount > 0 && "text-d-accent-ink")}>
              {s("ledger.filter")}
              {filterCount > 0 && <span className="num text-[11px]">{filterCount}</span>}
            </Button>
          </PopoverPrimitive.Trigger>
          <PopoverPrimitive.Portal>
            <PopoverPrimitive.Content align="start" sideOffset={6} className={POPOVER} aria-label={s("ledger.filters")}>
              <div className="space-y-3.5">
                <div className="flex items-center justify-between">
                  <span className="text-[12px] font-semibold text-d-ink">{s("ledger.filters")}</span>
                  <button type="button" onClick={() => setFilters(EMPTY_FILTERS)} className="d-press text-[12px] text-d-accent-ink">
                    {s("ledger.clearFilters")}
                  </button>
                </div>

                <div className="space-y-1.5">
                  <p className="text-[11.5px] text-d-ink-3">{s("ledger.direction")}</p>
                  <Segmented
                    value={filters.direction}
                    onChange={(v: DirectionFilter) => setFilters((f) => ({ ...f, direction: v }))}
                    label={s("ledger.direction")}
                    size="sm"
                    options={[
                      { value: "ALL", label: s("common.all") },
                      { value: "OUT", label: s("en.out") },
                      { value: "IN", label: s("en.in") },
                    ]}
                  />
                </div>

                <div className="space-y-1.5">
                  <p className="text-[11.5px] text-d-ink-3">{s("ledger.time")}</p>
                  <Segmented
                    value={timePreset()}
                    onChange={applyTime}
                    label={s("ledger.time")}
                    size="sm"
                    options={[
                      { value: "all", label: s("common.all") },
                      { value: "today", label: s("common.today") },
                      { value: "week", label: s("stats.week") },
                      { value: "month", label: s("stats.month") },
                      { value: "days30", label: s("ledger.days30") },
                    ]}
                  />
                  <div className="flex items-center gap-2 pt-1">
                    <input
                      type="date"
                      aria-label={s("stats.from")}
                      value={filters.from ? dateKey(filters.from) : ""}
                      onChange={(e) => setFilters((f) => ({ ...f, from: e.target.value ? new Date(`${e.target.value}T00:00:00`).getTime() : null }))}
                      className="h-7 min-w-0 flex-1 rounded-md bg-d-fill px-2 text-[12px] text-d-ink outline-none"
                    />
                    <span className="text-d-ink-3">–</span>
                    <input
                      type="date"
                      aria-label={s("stats.to")}
                      value={filters.to ? dateKey(addDays(filters.to, -1)) : ""}
                      onChange={(e) => setFilters((f) => ({ ...f, to: e.target.value ? addDays(new Date(`${e.target.value}T00:00:00`).getTime(), 1) : null }))}
                      className="h-7 min-w-0 flex-1 rounded-md bg-d-fill px-2 text-[12px] text-d-ink outline-none"
                    />
                  </div>
                </div>

                <div className="space-y-1.5">
                  <p className="text-[11.5px] text-d-ink-3">{s("ledger.category")}</p>
                  <div className="flex flex-wrap gap-1">
                    {(Object.keys(CATEGORIES) as Category[]).map((key) => {
                      const on = filters.categories.includes(key);
                      return (
                        <button
                          key={key}
                          type="button"
                          aria-pressed={on}
                          onClick={() => setFilters((f) => ({ ...f, categories: on ? f.categories.filter((c) => c !== key) : [...f.categories, key] }))}
                          className={cn("d-press h-6 rounded-md px-2 text-[12px]", on ? "bg-d-accent/10 text-d-accent-ink" : "bg-d-fill text-d-ink-2 hover:text-d-ink")}
                        >
                          {categoryLabel(key)}
                        </button>
                      );
                    })}
                  </div>
                </div>

                <div className="space-y-1.5">
                  <p className="text-[11.5px] text-d-ink-3">{s("ledger.amount")}</p>
                  <div className="flex items-center gap-2">
                    <TextInput
                      inputMode="decimal"
                      placeholder={s("ledger.min")}
                      aria-label={`${s("ledger.amount")} ${s("ledger.min")}`}
                      value={filters.minCents === null ? "" : String(filters.minCents / 100)}
                      onChange={(e) => setFilters((f) => ({ ...f, minCents: e.target.value === "" ? null : Math.round(Number(e.target.value) * 100) }))}
                      className="h-7 text-[12px]"
                    />
                    <TextInput
                      inputMode="decimal"
                      placeholder={s("ledger.max")}
                      aria-label={`${s("ledger.amount")} ${s("ledger.max")}`}
                      value={filters.maxCents === null ? "" : String(filters.maxCents / 100)}
                      onChange={(e) => setFilters((f) => ({ ...f, maxCents: e.target.value === "" ? null : Math.round(Number(e.target.value) * 100) }))}
                      className="h-7 text-[12px]"
                    />
                  </div>
                </div>

                <div className="space-y-1.5">
                  <p className="text-[11.5px] text-d-ink-3">{s("ledger.source")}</p>
                  <div className="flex flex-wrap gap-1">
                    {SOURCES.map((key) => {
                      const on = filters.sources.includes(key);
                      return (
                        <button
                          key={key}
                          type="button"
                          aria-pressed={on}
                          onClick={() => setFilters((f) => ({ ...f, sources: on ? f.sources.filter((c) => c !== key) : [...f.sources, key] }))}
                          className={cn("d-press h-6 rounded-md px-2 text-[12px]", on ? "bg-d-accent/10 text-d-accent-ink" : "bg-d-fill text-d-ink-2 hover:text-d-ink")}
                        >
                          {sourceLabel(key)}
                        </button>
                      );
                    })}
                  </div>
                </div>
              </div>
            </PopoverPrimitive.Content>
          </PopoverPrimitive.Portal>
        </PopoverPrimitive.Root>

        {filterCount > 0 && (
          <div className="flex flex-wrap items-center gap-1.5">
            {filters.direction !== "ALL" && (
              <Chip onRemove={() => setFilters((f) => ({ ...f, direction: "ALL" }))}>{filters.direction === "OUT" ? s("en.out") : s("en.in")}</Chip>
            )}
            {(filters.from !== null || filters.to !== null) && (
              <Chip onRemove={() => setFilters((f) => ({ ...f, from: null, to: null }))}>
                {filters.from ? dateKey(filters.from).slice(5) : "…"}
                {filters.to ? ` – ${dateKey(addDays(filters.to, -1)).slice(5)}` : ""}
              </Chip>
            )}
            {filters.categories.map((c) => (
              <Chip key={c} onRemove={() => setFilters((f) => ({ ...f, categories: f.categories.filter((x) => x !== c) }))}>
                {categoryLabel(c)}
              </Chip>
            ))}
            {(filters.minCents !== null || filters.maxCents !== null) && (
              <Chip onRemove={() => setFilters((f) => ({ ...f, minCents: null, maxCents: null }))}>
                {filters.minCents !== null ? money(filters.minCents) : "…"}
                {filters.maxCents !== null ? ` – ${money(filters.maxCents)}` : ""}
              </Chip>
            )}
            {filters.sources.map((x) => (
              <Chip key={x} onRemove={() => setFilters((f) => ({ ...f, sources: f.sources.filter((y) => y !== x) }))}>
                {sourceLabel(x)}
              </Chip>
            ))}
          </div>
        )}

        <div className="min-w-0 flex-1" />

        <input
          ref={fileRef}
          type="file"
          accept=".csv,text/csv"
          className="hidden"
          onChange={(e) => {
            const file = e.target.files?.[0];
            if (file) void readFile(file);
            e.target.value = "";
          }}
        />
        <Button icon={Upload} onClick={() => fileRef.current?.click()}>
          {s("ledger.import")}
        </Button>
        <Button
          icon={Download}
          onClick={() => {
            downloadCsv(`xiaopiao-${dateKey(now)}.csv`, rows);
            toast(s("st.exported"));
          }}
        >
          {s("ledger.export")}
        </Button>
      </div>

      {isError ? (
        <Card padded={false}>
          <EmptyState
            title={s("empty.errorTitle")}
            hint={s("empty.errorHint")}
            action={<Button variant="primary" icon={RefreshCw} onClick={refetch}>{s("common.retry")}</Button>}
          />
        </Card>
      ) : (
        <Loadable loading={skeleton} skeleton={<Skeleton rows={8} />}>
        <>
          <DataTable
            rows={rows}
            columns={columns}
            rowKey={(t) => t.id}
            label={s("nav.ledger")}
            sort={sort}
            onSort={setSort}
            selected={selected}
            onSelected={setSelected}
            activeId={activeId}
            onActive={setActiveId}
            onActivate={(t) => shell.openEntry(t)}
            onDelete={(targets) => void deleteRows(targets)}
            query={debounced}
            maxHeight="calc(100dvh - var(--d-chrome, 88px) - 190px)"
            empty={
              rows.length === 0 ? (
                txns.length === 0 ? (
                  <EmptyState
                    art="/img/app-empty-ledger.jpg"
                    title={s("empty.ledgerTitle")}
                    hint={s("empty.ledgerHint")}
                    action={<Button variant="primary" icon={Plus} onClick={() => shell.openEntry()}>{s("cm.record")}</Button>}
                  />
                ) : (
                  <EmptyState
                    icon={Filter}
                    title={s("ledger.noMatch")}
                    hint={s("ledger.noMatchHint")}
                    action={
                      <Button
                        variant="quiet"
                        onClick={() => {
                          setQuery("");
                          setFilters(EMPTY_FILTERS);
                        }}
                      >
                        {s("ledger.clearFilters")}
                      </Button>
                    }
                  />
                )
              ) : null
            }
          />
          <div className="flex items-center justify-between px-1 text-[11.5px] text-d-ink-3">
            <span>
              {rows.length} {s("ledger.itemUnit")}
            </span>
            <span>{s("ledger.editHint")}</span>
          </div>

          {selected.size > 0 && (
            <div className="sticky bottom-2 z-20 mx-auto flex items-center gap-2 rounded-xl bg-d-ink px-3 py-2 text-d-canvas shadow-[0_10px_30px_-8px_oklch(0_0_0/40%)]">
              <span className="num text-[12.5px]">
                {s("ledger.selected")} {selected.size} {s("ledger.itemUnit")}
              </span>
              <PopoverPrimitive.Root open={bulkOpen} onOpenChange={setBulkOpen}>
                <PopoverPrimitive.Trigger asChild>
                  <button type="button" className="d-press h-7 rounded-md bg-d-canvas/15 px-2.5 text-[12px] font-medium">
                    {s("ledger.changeCategory")}
                  </button>
                </PopoverPrimitive.Trigger>
                <PopoverPrimitive.Portal>
                  <PopoverPrimitive.Content align="center" sideOffset={8} className={cn(POPOVER, "w-[300px]")}>
                    <div className="flex flex-wrap gap-1">
                      {(Object.keys(CATEGORIES) as Category[]).map((key) => (
                        <button
                          key={key}
                          type="button"
                          onClick={() => void reclassify(key)}
                          className="d-press h-6 rounded-md bg-d-fill px-2 text-[12px] text-d-ink-2 hover:text-d-ink"
                        >
                          {categoryLabel(key)}
                        </button>
                      ))}
                    </div>
                  </PopoverPrimitive.Content>
                </PopoverPrimitive.Portal>
              </PopoverPrimitive.Root>
              <button type="button" onClick={() => void deleteRows(selectedRows)} className="d-press h-7 rounded-md bg-d-canvas/15 px-2.5 text-[12px] font-medium">
                {s("common.delete")}
              </button>
              <button type="button" onClick={() => setSelected(toggleAll(selected, rows.map((r) => r.id)))} className="d-press h-7 rounded-md px-2 text-[12px] text-d-canvas/70">
                {s("common.all")}
              </button>
              <button type="button" onClick={() => setSelected(new Set())} aria-label={s("ledger.clearSelection")} className="d-press grid size-6 place-items-center rounded-md text-d-canvas/70">
                <X className="size-3.5" strokeWidth={2} aria-hidden />
              </button>
            </div>
          )}
        </>
        </Loadable>
      )}

      <Modal open={preview !== null} onOpenChange={(open: boolean) => !open && setPreview(null)} label={s("ledger.importTitle")} width={520}>
        {preview && (
          <>
            <h2 className="text-[15px] font-semibold text-d-ink">{s("ledger.importTitle")}</h2>
            <div className="mt-3 flex gap-2">
              <div className="flex-1 rounded-lg bg-d-fill px-3 py-2">
                <p className="text-[11.5px] text-d-ink-3">{s("ledger.importReady")}</p>
                <p className="num text-[18px] font-semibold text-d-ink">{preview.rows.length - preview.duplicates}</p>
              </div>
              <div className="flex-1 rounded-lg bg-d-fill px-3 py-2">
                <p className="text-[11.5px] text-d-ink-3">{s("ledger.importDup")}</p>
                <p className="num text-[18px] font-semibold text-d-ink">{preview.duplicates}</p>
              </div>
              <div className="flex-1 rounded-lg bg-d-fill px-3 py-2">
                <p className="text-[11.5px] text-d-ink-3">{s("ledger.importFailed")}</p>
                <p className="num text-[18px] font-semibold text-d-ink">{preview.failed}</p>
              </div>
            </div>
            <div className="mt-3 max-h-[220px] overflow-y-auto rounded-lg bg-d-fill/50">
              {preview.rows.slice(0, 8).map((t) => (
                <div key={t.id} className="flex items-center gap-2 border-b border-d-hairline px-3 py-1.5 text-[12px] last:border-b-0">
                  <span className="num w-[86px] shrink-0 text-d-ink-3">{dateKey(t.occurredAt).slice(5)}</span>
                  <span className="min-w-0 flex-1 truncate text-d-ink">{t.merchant}</span>
                  <span className="shrink-0 text-d-ink-3">{categoryLabel(t.category)}</span>
                  <span className="num w-[76px] shrink-0 text-right text-d-ink">{money(t.amountCents)}</span>
                </div>
              ))}
            </div>
            <div className="mt-4 flex justify-end gap-2">
              <Button onClick={() => setPreview(null)}>{s("common.cancel")}</Button>
              <Button
                variant="primary"
                onClick={() => {
                  void add(preview.rows).then((result) => {
                    setPreview(null);
                    if (result.added === 0) toast(s("ledger.importDupAll"));
                    else toast(`${s("ledger.importDone")} ${result.added}`);
                  });
                }}
              >
                {s("ledger.importConfirm")}
              </Button>
            </div>
          </>
        )}
      </Modal>
    </div>
  );
}
