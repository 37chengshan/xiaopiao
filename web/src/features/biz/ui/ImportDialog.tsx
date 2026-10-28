import { useMemo, useState } from "react";
import { money, shortDate } from "@/logic/biz/format";
import { buildPreview, parseRevenueCsv, type ConflictPolicy, type ImportPreview, type PreviewRow } from "@/logic/biz/import";
import type { RevenueEntry } from "@/logic/biz/book";
import { useBizStrings } from "../strings";
import { fill } from "../text";
import { Button, Chip, Modal, Segmented } from "./primitives";

/**
 * Import preview: what the file would do to the book, day by day, before anything is written.
 * `新增` follows the file alone; a day that already holds different numbers follows the policy.
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
  const preview = useMemo(
    () => buildPreview(parseRevenueCsv(text, today), book, policy, fileName),
    [text, book, policy, today, fileName],
  );
  const changes = preview.counts.add + preview.counts.overwrite;

  return (
    <Modal
      open={open}
      onClose={onClose}
      title={t("import.title")}
      description={`${fileName} · ${t("import.fileNote")}`}
      width={720}
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
                onChange={setPolicy}
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
                  <th scope="col" className="w-12 px-3 py-2">
                    #
                  </th>
                  <th scope="col" className="px-3 py-2">
                    {t("import.column")}
                  </th>
                  <th scope="col" className="px-3 py-2">
                    {t("import.book")}
                  </th>
                  <th scope="col" className="w-28 px-3 py-2">
                    {t("revenue.col.source")}
                  </th>
                </tr>
              </thead>
              <tbody>
                {preview.rows.map((row) => (
                  <PreviewRowView key={`${row.line}-${row.raw}`} row={row} />
                ))}
              </tbody>
            </table>
          </div>
          <p className="text-[11px] leading-4 text-d-ink-3">{t("import.policy.hint")}</p>
        </div>
      )}
    </Modal>
  );
}

function PreviewRowView({ row }: { row: PreviewRow }) {
  const t = useBizStrings();
  const tone = row.status === "new" ? "accent" : row.status === "error" ? "warn" : "neutral";
  const statusLabel =
    row.status === "new"
      ? t("import.status.new")
      : row.status === "same"
        ? t("import.status.same")
        : row.status === "differ"
          ? row.outcome === "overwrite"
            ? t("import.policy.replace")
            : t("import.status.differ")
          : row.status === "duplicate"
            ? t("import.status.duplicate")
            : t("import.status.error");

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
      <td className="px-3 py-1.5 tabular-nums text-d-ink-2">
        {row.existing ? `${money(row.existing.revenueCents)}${row.existing.orders ? ` · ${row.existing.orders}` : ""}` : "—"}
      </td>
      <td className="px-3 py-1.5">
        <Chip tone={tone}>{statusLabel}</Chip>
      </td>
    </tr>
  );
}
