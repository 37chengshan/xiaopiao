import { useMemo, useState } from "react";
import { money, shortDate } from "@/logic/biz/format";
import {
  buildPreview,
  decideRow,
  parseRevenueCsv,
  type ConflictPolicy,
  type ImportPreview,
  type PreviewRow,
  type RowOutcome,
} from "@/logic/biz/import";
import type { RevenueEntry } from "@/logic/biz/book";
import { cn } from "@/lib/utils";
import { useBizStrings } from "../strings";
import { fill } from "../text";
import { Button, Chip, Modal, Segmented } from "./primitives";

/**
 * Import preview: what the file would do to the book, day by day, before anything is written.
 * The policy sets the default; each day that can be written also gets its own 新增 / 覆盖 / 跳过
 * switch. Seeded days count as 新增 — the owner never entered those numbers.
 */
export function ImportDialog({
  open,
  fileName,
  text,
  book,
  today,
  onClose,
  onApply,
}: {
  open: boolean;
  fileName: string;
  text: string;
  book: readonly RevenueEntry[];
  today: number;
  onClose: () => void;
  onApply: (preview: ImportPreview) => void;
}) {
  const t = useBizStrings();
  const [policy, setPolicy] = useState<ConflictPolicy>("replace");
  const [overrides, setOverrides] = useState<ReadonlyMap<number, RowOutcome>>(new Map());

  const preview = useMemo(() => {
    let next = buildPreview(parseRevenueCsv(text, today), book, policy, fileName);
    for (const [line, outcome] of overrides) next = decideRow(next, line, outcome);
    return next;
  }, [text, book, policy, today, fileName, overrides]);
  const changes = preview.counts.add + preview.counts.overwrite;

  const decide = (line: number, outcome: RowOutcome) =>
    setOverrides((prev) => {
      const next = new Map(prev);
      next.set(line, outcome);
      return next;
    });

  return (
    <Modal
      open={open}
      onClose={onClose}
      title={t("import.title")}
      description={`${fileName} · ${t("import.fileNote")}`}
      width={760}
      footer={
        <>
          <Button tone="fill" onClick={onClose}>
            {t("import.cancel")}
          </Button>
          <Button
            tone="solid"
            disabled={changes === 0}
            onClick={() => {
              onApply(preview);
              onClose();
            }}
          >
            {changes === 0 ? t("import.confirmNone") : fill(t("import.confirm"), `${changes}`)}
          </Button>
        </>
      }
    >
      {preview.rows.length === 0 ? (
        <p className="py-6 text-center text-[12px] leading-5 text-d-ink-2">{t("import.empty")}</p>
      ) : (
        <div className="space-y-4">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <p className="text-[12px] leading-4 text-d-ink-2">
              {fill(
                t("import.summary"),
                `${preview.counts.add}`,
                `${preview.counts.overwrite}`,
                `${preview.counts.skip}`,
                `${preview.counts.error}`,
              )}
            </p>
            <div className="flex items-center gap-2">
              <span className="text-[12px] text-d-ink-2">{t("import.policy")}</span>
              <Segmented<ConflictPolicy>
                label={t("import.policy")}
                value={policy}
                onChange={(next) => {
                  setPolicy(next);
                  setOverrides(new Map());
                }}
                options={[
                  { value: "replace", label: t("import.policy.replace") },
                  { value: "keep", label: t("import.policy.keep") },
                ]}
              />
            </div>
          </div>

          <div className="max-h-[46vh] overflow-y-auto rounded-lg bg-d-fill/40">
            <table className="w-full border-collapse text-[12px]">
              <thead>
                <tr className="sticky top-0 bg-d-surface text-left text-[11px] font-medium text-d-ink-2">
                  <th scope="col" className="w-10 px-3 py-2">
                    #
                  </th>
                  <th scope="col" className="px-3 py-2">
                    {t("import.column")}
                  </th>
                  <th scope="col" className="px-3 py-2">
                    {t("import.book")}
                  </th>
                  <th scope="col" className="w-40 px-3 py-2">
                    {t("import.decide")}
                  </th>
                </tr>
              </thead>
              <tbody>
                {preview.rows.map((row) => (
                  <PreviewRowView key={`${row.line}-${row.raw}`} row={row} onDecide={decide} />
                ))}
              </tbody>
            </table>
          </div>
          <p className="text-[11px] leading-4 text-d-ink-3">
            {t("import.policy.hint")} {t("import.decideHint")}
          </p>
        </div>
      )}
    </Modal>
  );
}

function PreviewRowView({ row, onDecide }: { row: PreviewRow; onDecide: (line: number, outcome: RowOutcome) => void }) {
  const t = useBizStrings();
  const tone = row.status === "new" ? "accent" : row.status === "error" ? "warn" : "neutral";
  const statusLabel =
    row.status === "error"
      ? t("import.status.error")
      : row.status === "duplicate"
        ? t("import.status.duplicate")
        : row.status === "same"
          ? t("import.status.same")
          : row.outcome === "add"
            ? t("import.status.new")
            : row.outcome === "overwrite"
              ? t("import.policy.replace")
              : t("import.policy.keep");
  const choices: ReadonlyArray<{ value: RowOutcome; label: string }> =
    row.status === "new"
      ? [
          { value: "add", label: t("import.status.new") },
          { value: "skip", label: t("import.policy.keep") },
        ]
      : row.status === "differ"
        ? [
            { value: "overwrite", label: t("import.policy.replace") },
            { value: "skip", label: t("import.policy.keep") },
          ]
        : [];
  const sourceLabel =
    row.existing?.source === "CSV"
      ? t("revenue.source.csv")
      : row.existing?.source === "MANUAL"
        ? t("revenue.source.manual")
        : t("revenue.source.seed");

  return (
    <tr className="border-t border-d-hairline/70">
      <td className="px-3 py-1.5 tabular-nums text-d-ink-3">{row.line}</td>
      <td className="px-3 py-1.5">
        <span className={row.status === "error" ? "text-d-warning" : "text-d-ink"}>
          {row.error
            ? row.raw
            : `${row.day === null ? "—" : shortDate(row.day)} · ${money(row.revenueCents ?? 0)}${row.orders ? ` · ${row.orders}` : ""}`}
        </span>
        {row.error && <span className="ml-2 text-[11px] text-d-ink-3">{t(`import.error.${row.error}`)}</span>}
      </td>
      <td className="px-3 py-1.5">
        {row.existing ? (
          <span className="flex items-center gap-1.5">
            <span className="tabular-nums text-d-ink-2">
              {money(row.existing.revenueCents)}
              {row.existing.orders ? ` · ${row.existing.orders}` : ""}
            </span>
            <Chip>{sourceLabel}</Chip>
          </span>
        ) : (
          <span className="text-d-ink-2">—</span>
        )}
      </td>
      <td className="px-3 py-1.5">
        <div className="flex items-center gap-2">
          <Chip tone={tone}>{statusLabel}</Chip>
          {choices.length > 0 && (
            <div role="radiogroup" aria-label={t("import.decide")} className="inline-flex overflow-hidden rounded-md bg-d-fill">
              {choices.map((choice) => {
                const on = row.outcome === choice.value;
                return (
                  <button
                    key={choice.value}
                    type="button"
                    role="radio"
                    aria-checked={on}
                    onClick={() => onDecide(row.line, choice.value)}
                    className={cn(
                      "h-5 px-1.5 text-[11px] font-medium leading-5 transition-colors",
                      on ? "bg-d-ink text-d-canvas" : "text-d-ink-2 hover:text-d-ink",
                    )}
                  >
                    {choice.label}
                  </button>
                );
              })}
            </div>
          )}
        </div>
      </td>
    </tr>
  );
}
