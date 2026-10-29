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
  activeFilterCount,
  filterTxns,
  orderCentsRange,
  orderDayRange,
  parseCentsInput,
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
import { fill, s as copy } from "./copy/ledger";
import { useShell } from "../Shell";

const POPOVER = "z-50 w-[380px] max-w-[calc(100vw-32px)] rounded-xl bg-d-surface p-4 shadow-[0_18px_50px_-12px_oklch(0_0_0/30%)] outline-none";
const SOURCES: Source[] = ["MANUAL", "QUICK_ADD", "VOICE", "CSV", "DEMO"];
/** Import preview lists the head of the file; the rest stays one click away. */
const PREVIEW_ROWS = 8;

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
  const [preview, setPreview] = useState<{ rows: Txn[]; failed: number; failures: string[]; duplicates: number } | null>(null);
  const [exportOpen, setExportOpen] = useState(false);
  // Only text that does not parse yet lives here; a valid amount goes straight into `filters`.
  const [amountDraft, setAmountDraft] = useState<Record<"minCents" | "maxCents", string | null>>({ minCents: null, maxCents: null });
  const fileRef = useRef<HTMLInputElement>(null);

  const rows = useMemo(() => sortTxns(filterTxns(txns, { ...filters, query: debounced }), sort), [txns, filters, debounced, sort]);
  const filterCount = activeFilterCount(filters);
  const filtering = debounced.trim() !== "" || filterCount > 0;
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
          toast(parsed.failures[0] ?? copy("import.readFail"));
          return;
        }
        setPreview({ rows: parsed.txns, failed: parsed.failed, failures: parsed.failures, duplicates });
      } catch {
        toast.error(copy("import.readFail"));
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

  const today = startOfDay(now);

  const timePreset = (): TimePreset => {
    if (filters.from === null && filters.to === null) return "all";
    if (filters.from === today && filters.to === addDays(today, 1)) return "today";
    if (filters.from === weekStartOf(now) && filters.to === addDays(today, 1)) return "week";
    if (filters.from !== null && filters.from === new Date(new Date(now).getFullYear(), new Date(now).getMonth(), 1).getTime() && filters.to === addDays(today, 1)) return "month";
    if (filters.from === addDays(today, -29) && filters.to === addDays(today, 1)) return "days30";
    return "custom";
  };

  /** `custom` is offered only while it is the current state; the date inputs own that range. */
  const preset = timePreset();

  const applyTime = (next: TimePreset) => {
    const set = (from: number | null, to: number | null) => setFilters((f) => ({ ...f, from, to }));
    if (next === "all") set(null, null);
    else if (next === "today") set(today, addDays(today, 1));
    else if (next === "week") set(weekStartOf(now), addDays(today, 1));
    else if (next === "month") set(new Date(new Date(now).getFullYear(), new Date(now).getMonth(), 1).getTime(), addDays(today, 1));
    else if (next === "days30") set(addDays(today, -29), addDays(today, 1));
  };

  const setDay = (side: "from" | "to", raw: string) => {
    if (raw === "") {
      setFilters((f) => (side === "from" ? { ...f, from: null } : { ...f, to: null }));
      return;
    }
    const day = Math.min(new Date(`${raw}T00:00:00`).getTime(), today);
    setFilters((f) => (side === "from" ? { ...f, from: day } : { ...f, to: addDays(day, 1) }));
  };

  /** Two dates typed the wrong way round swap instead of leaving an empty table. */
  const settleDayRange = () => setFilters((f) => ({ ...f, ...orderDayRange(f.from, f.to) }));

  const setAmount = (side: "minCents" | "maxCents", raw: string) => {
    setAmountDraft((d) => (side === "minCents" ? { ...d, minCents: raw } : { ...d, maxCents: raw }));
    const cents = parseCentsInput(raw);
    setFilters((f) => (side === "minCents" ? { ...f, minCents: cents } : { ...f, maxCents: cents }));
  };

  /** Blur drops the draft, so "12." reads back as ¥12, and a reversed range settles. */
  const settleAmount = (side: "minCents" | "maxCents") => {
    setAmountDraft((d) => (side === "minCents" ? { ...d, minCents: null } : { ...d, maxCents: null }));
    setFilters((f) => ({ ...f, ...orderCentsRange(f.minCents, f.maxCents) }));
  };

  const amountText = (side: "minCents" | "maxCents") => amountDraft[side] ?? (filters[side] === null ? "" : String(filters[side] / 100));

  const clearFilters = () => {
    setFilters(EMPTY_FILTERS);
    setAmountDraft({ minCents: null, maxCents: null });
  };

  const deleteRows = async (targets: Txn[]) => {
    if (targets.length === 0) return;
    const gone = new Set(targets.map((t) => t.id));
    setSelected((prev) => new Set([...prev].filter((id) => !gone.has(id))));
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

  const exportRows = (list: readonly Txn[], message: string) => {
    downloadCsv(`xiaopiao-${dateKey(now)}.csv`, list);
    setExportOpen(false);
    toast(message);
  };

  const columns: Column<Txn>[] = [
    {
      key: "occurredAt",
      header: s("ledger.colTime"),
      sortKey: "occurredAt",
      width: "132px",
      phoneHidden: true,
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
      phoneHidden: true,
      cell: (t) => <span className="text-[12px] text-d-ink-2">{categoryLabel(t.category)}</span>,
    },
    {
      key: "source",
      header: s("ledger.colSource"),
      sortKey: "source",
      width: "84px",
      phoneHidden: true,
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
                  <button type="button" onClick={clearFilters} className="d-press text-[12px] text-d-accent-ink">
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
                    value={preset}
                    onChange={applyTime}
                    label={s("ledger.time")}
                    size="sm"
                    className="max-w-full flex-wrap"
                    options={[
                      { value: "all", label: s("common.all") },
                      { value: "today", label: s("common.today") },
                      { value: "week", label: s("stats.week") },
                      { value: "month", label: s("stats.month") },
                      { value: "days30", label: s("ledger.days30") },
                      ...(preset === "custom" ? [{ value: "custom" as const, label: copy("time.custom") }] : []),
                    ]}
                  />
                  <div className="flex items-center gap-2 pt-1">
                    <input
                      type="date"
                      aria-label={s("stats.from")}
                      value={filters.from ? dateKey(filters.from) : ""}
                      max={dateKey(filters.to !== null ? Math.min(today, filters.to - 1) : today)}
                      onChange={(e) => setDay("from", e.target.value)}
                      onBlur={settleDayRange}
                      className="h-7 min-w-0 flex-1 rounded-md bg-d-fill px-2 text-[12px] text-d-ink outline-none"
                    />
                    <span className="text-d-ink-3">–</span>
                    <input
                      type="date"
                      aria-label={s("stats.to")}
                      value={filters.to ? dateKey(addDays(filters.to, -1)) : ""}
                      min={filters.from !== null ? dateKey(filters.from) : undefined}
                      max={dateKey(today)}
                      onChange={(e) => setDay("to", e.target.value)}
                      onBlur={settleDayRange}
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
                      value={amountText("minCents")}
                      onChange={(e) => setAmount("minCents", e.target.value)}
                      onBlur={() => settleAmount("minCents")}
                      className="h-7 text-[12px]"
                    />
                    <TextInput
                      inputMode="decimal"
                      placeholder={s("ledger.max")}
                      aria-label={`${s("ledger.amount")} ${s("ledger.max")}`}
                      value={amountText("maxCents")}
                      onChange={(e) => setAmount("maxCents", e.target.value)}
                      onBlur={() => settleAmount("maxCents")}
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
              <Chip
                onRemove={() => {
                  setFilters((f) => ({ ...f, minCents: null, maxCents: null }));
                  setAmountDraft({ minCents: null, maxCents: null });
                }}
              >
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
        {filtering ? (
          <PopoverPrimitive.Root open={exportOpen} onOpenChange={setExportOpen}>
            <PopoverPrimitive.Trigger asChild>
              <Button icon={Download}>{s("ledger.export")}</Button>
            </PopoverPrimitive.Trigger>
            <PopoverPrimitive.Portal>
              <PopoverPrimitive.Content align="end" sideOffset={6} aria-label={copy("export.scope")} className={cn(POPOVER, "w-[248px] p-1.5")}>
                <button
                  type="button"
                  disabled={rows.length === 0}
                  onClick={() => exportRows(rows, fill(copy("export.doneShown"), rows.length))}
                  className="d-press block w-full rounded-lg px-2.5 py-1.5 text-left text-[12.5px] text-d-ink hover:bg-d-fill disabled:opacity-40"
                >
                  {fill(copy("export.shown"), rows.length)}
                </button>
                <button
                  type="button"
                  onClick={() => exportRows(txns, fill(copy("export.doneAll"), txns.length))}
                  className="d-press block w-full rounded-lg px-2.5 py-1.5 text-left text-[12.5px] text-d-ink-2 hover:bg-d-fill hover:text-d-ink"
                >
                  {fill(copy("export.all"), txns.length)}
                </button>
              </PopoverPrimitive.Content>
            </PopoverPrimitive.Portal>
          </PopoverPrimitive.Root>
        ) : (
          <Button icon={Download} onClick={() => exportRows(txns, fill(copy("export.doneAll"), txns.length))}>
            {s("ledger.export")}
          </Button>
        )}
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
                          clearFilters();
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
            <span>{copy("hint.edit")}</span>
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
              {preview.rows.slice(0, PREVIEW_ROWS).map((t) => (
                <div key={t.id} className="flex items-center gap-2 border-b border-d-hairline px-3 py-1.5 text-[12px] last:border-b-0">
                  <span className="num w-[86px] shrink-0 text-d-ink-3">{dateKey(t.occurredAt).slice(5)}</span>
                  <span className="min-w-0 flex-1 truncate text-d-ink">{t.merchant}</span>
                  <span className="shrink-0 text-d-ink-3">{categoryLabel(t.category)}</span>
                  <span className={cn("num w-[84px] shrink-0 text-right", t.direction === "IN" ? "text-d-positive" : "text-d-ink")}>
                    {t.direction === "IN" ? "+" : "−"}
                    {money(t.amountCents)}
                  </span>
                </div>
              ))}
            </div>
            {preview.rows.length > PREVIEW_ROWS && (
              <p className="mt-1.5 text-[11.5px] text-d-ink-3">{fill(copy("import.ofTotal"), PREVIEW_ROWS, preview.rows.length)}</p>
            )}
            {preview.failures.length > 0 && (
              <details className="mt-2 rounded-lg bg-d-fill/50 px-3 py-2">
                <summary className="cursor-pointer text-[12px] text-d-ink-2">{fill(copy("import.skipped"), preview.failed)}</summary>
                <ul className="mt-1.5 space-y-0.5 text-[11.5px] text-d-ink-3">
                  {preview.failures.map((f) => (
                    <li key={f}>· {f}</li>
                  ))}
                </ul>
                {preview.failed > preview.failures.length && (
                  <p className="mt-1 text-[11.5px] text-d-ink-3">{fill(copy("import.skippedRest"), preview.failed - preview.failures.length)}</p>
                )}
              </details>
            )}
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
