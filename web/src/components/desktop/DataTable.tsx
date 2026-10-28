import { ChevronDown, ChevronUp } from "lucide-react";
import { useCallback, useEffect, useLayoutEffect, useRef, useState, type ReactNode } from "react";
import { cn } from "@/lib/utils";
import { nextSort, stepIndex, toggleAll, toggleSelected, virtualWindow, type Sort, type SortKey } from "./tableModel";
import { s } from "./strings";

export interface Column<T> {
  key: string;
  header: string;
  /** css width, e.g. `112px` */
  width?: string;
  align?: "left" | "right";
  /** presence of `sortKey` makes the header clickable */
  sortKey?: SortKey;
  cell: (row: T, ctx: { query: string }) => ReactNode;
}

export interface DataTableProps<T> {
  rows: readonly T[];
  columns: readonly Column<T>[];
  rowKey: (row: T) => string;
  /** accessible name for the table */
  label: string;
  sort?: Sort;
  onSort?: (next: Sort) => void;
  selected?: ReadonlySet<string>;
  onSelected?: (next: Set<string>) => void;
  activeId?: string | null;
  onActive?: (id: string | null) => void;
  onActivate?: (row: T) => void;
  onDelete?: (rows: T[]) => void;
  query?: string;
  empty?: ReactNode;
  rowHeight?: number;
  /** table body scrolls inside itself (sticky header + virtualization) */
  maxHeight?: string;
  virtualizeAfter?: number;
}

/**
 * macOS-style data table (docs/WEB-SPEC.md §A.2): 40px rows, hairline rules, sticky 12px header,
 * hover fill, accent-tinted selection, multi-select, column sorting, keyboard movement and
 * windowed rendering past `virtualizeAfter` rows.
 */
export function DataTable<T>({
  rows,
  columns,
  rowKey,
  label,
  sort,
  onSort,
  selected,
  onSelected,
  activeId = null,
  onActive,
  onActivate,
  onDelete,
  query = "",
  empty,
  rowHeight = 40,
  maxHeight,
  virtualizeAfter = 500,
}: DataTableProps<T>) {
  const scrollRef = useRef<HTMLDivElement>(null);
  const [scrollTop, setScrollTop] = useState(0);
  const [viewportH, setViewportH] = useState(480);
  const selectable = Boolean(selected && onSelected);

  useLayoutEffect(() => {
    const el = scrollRef.current;
    if (!el) return;
    const sync = () => setViewportH(el.clientHeight);
    sync();
    const ro = new ResizeObserver(sync);
    ro.observe(el);
    return () => ro.disconnect();
  }, []);

  const ids = rows.map(rowKey);
  const virtual = rows.length > virtualizeAfter;
  const win = virtual ? virtualWindow({ scrollTop, viewportHeight: viewportH, rowHeight, count: rows.length }) : { start: 0, end: rows.length, padTop: 0, padBottom: 0 };
  const visible = rows.slice(win.start, win.end);

  // keep the keyboard cursor on screen
  useEffect(() => {
    if (!activeId) return;
    const index = rows.findIndex((r) => rowKey(r) === activeId);
    const el = scrollRef.current;
    if (index < 0 || !el) return;
    const top = index * rowHeight;
    const headerH = 34;
    if (top + rowHeight + headerH > el.scrollTop + el.clientHeight) el.scrollTo({ top: top + rowHeight + headerH - el.clientHeight });
    else if (top < el.scrollTop - headerH) el.scrollTo({ top: Math.max(0, top - headerH) });
  }, [activeId, rows, rowHeight, rowKey]);

  const onKeyDown = useCallback(
    (event: React.KeyboardEvent<HTMLDivElement>) => {
      const index = activeId ? rows.findIndex((r) => rowKey(r) === activeId) : -1;
      if (event.key === "ArrowDown" || event.key === "ArrowUp") {
        event.preventDefault();
        const next = stepIndex(index, rows.length, event.key === "ArrowDown" ? 1 : -1);
        if (next >= 0) onActive?.(rowKey(rows[next]));
        return;
      }
      if (!activeId) return;
      const row = rows[index];
      if (!row) return;
      if (event.key === "Enter") {
        event.preventDefault();
        onActivate?.(row);
      } else if (event.key === "Delete" || event.key === "Backspace") {
        if (!onDelete) return;
        event.preventDefault();
        const targets = selected && selected.size > 0 ? rows.filter((r) => selected.has(rowKey(r))) : [row];
        onDelete(targets);
      } else if (event.key === " " && selected && onSelected) {
        event.preventDefault();
        onSelected(selected instanceof Set ? toggleSelected(selected, activeId) : new Set(selected));
      }
    },
    [activeId, onActivate, onActive, onDelete, onSelected, rowKey, rows, selected],
  );

  const allSelected = rows.length > 0 && rows.every((r) => selected?.has(rowKey(r)));

  return (
    <div
      ref={scrollRef}
      role="group"
      aria-label={label}
      tabIndex={0}
      onKeyDown={onKeyDown}
      onScroll={(e) => setScrollTop(e.currentTarget.scrollTop)}
      style={maxHeight ? { maxHeight } : undefined}
      className={cn(
        "relative overflow-auto overscroll-contain rounded-xl bg-d-surface outline-none",
        "focus-visible:outline-2 focus-visible:outline-offset-1 focus-visible:outline-d-accent",
        maxHeight && "min-h-0",
      )}
    >
      <table className="w-full border-collapse text-left text-[13px]">
        <colgroup>
          {selectable && <col style={{ width: 36 }} />}
          {columns.map((c) => (
            <col key={c.key} style={c.width ? { width: c.width } : undefined} />
          ))}
        </colgroup>
        <thead>
          <tr className="sticky top-0 z-10 bg-d-surface">
            {selectable && (
              <th scope="col" className="h-[34px] border-b border-d-hairline pl-3 pr-0 align-middle">
                <input
                  type="checkbox"
                  aria-label={s("ledger.selected")}
                  checked={allSelected}
                  onChange={() => onSelected?.(toggleAll(selected ?? new Set(), ids))}
                  className="size-4 cursor-pointer rounded-[4px] accent-[var(--d-accent)] focus-visible:outline-2 focus-visible:outline-offset-1 focus-visible:outline-d-accent"
                />
              </th>
            )}
            {columns.map((c) => {
              const active = sort?.key === c.sortKey;
              return (
                <th
                  key={c.key}
                  scope="col"
                  aria-sort={c.sortKey ? (active ? (sort?.dir === "asc" ? "ascending" : "descending") : "none") : undefined}
                  className={cn(
                    "h-[34px] whitespace-nowrap border-b border-d-hairline px-3 text-[12px] font-medium text-d-ink-2",
                    c.align === "right" && "text-right",
                  )}
                >
                  {c.sortKey && onSort ? (
                    <button
                      type="button"
                      onClick={() => onSort(nextSort(sort ?? { key: c.sortKey!, dir: "desc" }, c.sortKey!))}
                      title={s("ledger.sortHint")}
                      className={cn(
                        "press -mx-1 inline-flex items-center gap-0.5 rounded px-1 hover:text-d-ink",
                        active && "text-d-ink",
                        c.align === "right" && "flex-row-reverse",
                      )}
                    >
                      {c.header}
                      {active ? <ChevronUp className={cn("size-3", sort?.dir === "desc" && "rotate-180")} strokeWidth={2.25} aria-hidden /> : null}
                    </button>
                  ) : (
                    c.header
                  )}
                </th>
              );
            })}
          </tr>
        </thead>
        <tbody>
          {win.padTop > 0 && (
            <tr aria-hidden style={{ height: win.padTop }}>
              <td colSpan={columns.length + (selectable ? 1 : 0)} />
            </tr>
          )}
          {visible.map((row) => {
            const id = rowKey(row);
            const isActive = id === activeId;
            const isSelected = selected?.has(id) ?? false;
            return (
              <tr
                key={id}
                style={{ height: rowHeight }}
                aria-selected={selectable ? isSelected : undefined}
                onMouseDown={() => onActive?.(id)}
                onDoubleClick={() => onActivate?.(row)}
                className={cn(
                  "group align-middle transition-colors",
                  isSelected ? "bg-d-accent/[0.08]" : isActive ? "bg-d-fill/70" : "hover:bg-d-fill/40",
                  "border-b border-d-hairline last:border-b-0",
                )}
              >
                {selectable && (
                  <td className="pl-3 pr-0">
                    <input
                      type="checkbox"
                      aria-label={id}
                      checked={isSelected}
                      onChange={() => onSelected?.(toggleSelected(selected ?? new Set(), id))}
                      className="size-4 cursor-pointer rounded-[4px] accent-[var(--d-accent)] focus-visible:outline-2 focus-visible:outline-offset-1 focus-visible:outline-d-accent"
                    />
                  </td>
                )}
                {columns.map((c) => (
                  <td
                    key={c.key}
                    className={cn("truncate px-3 py-0 text-d-ink", c.align === "right" && "text-right tabular-nums")}
                  >
                    {c.cell(row, { query })}
                  </td>
                ))}
              </tr>
            );
          })}
          {win.padBottom > 0 && (
            <tr aria-hidden style={{ height: win.padBottom }}>
              <td colSpan={columns.length + (selectable ? 1 : 0)} />
            </tr>
          )}
          {rows.length === 0 && (
            <tr>
              <td colSpan={columns.length + (selectable ? 1 : 0)}>{empty}</td>
            </tr>
          )}
        </tbody>
      </table>
    </div>
  );
}
