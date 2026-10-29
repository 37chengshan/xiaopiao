import { type Txn } from "@/domain/types";
import { dateKey } from "@/logic/time";
import { Modal } from "../dialogs";
import { categoryLabel } from "../labels";
import { money } from "../format";
import { s } from "../strings";
import { Button } from "../ui";
import { s as ai } from "./copy/ai";

export interface ImportPreviewState {
  rows: Txn[];
  failed: number;
  duplicates: number;
}

/**
 * A statement is never written straight into the ledger: the parsed rows are shown first,
 * with the count that is actually new and the count the ledger already has.
 */
export function ImportPreview({
  preview,
  onClose,
  onConfirm,
}: {
  preview: ImportPreviewState | null;
  onClose: () => void;
  onConfirm: () => void;
}) {
  return (
    <Modal open={preview !== null} onOpenChange={(open: boolean) => !open && onClose()} label={s("ledger.importTitle")} width={520}>
      {preview && (
        <>
          <h2 className="text-[15px] font-semibold text-d-ink">{s("ledger.importTitle")}</h2>
          <p className="mt-1 text-[12px] leading-[17px] text-d-ink-3">{ai("import.note")}</p>
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
            <Button onClick={onClose}>{s("common.cancel")}</Button>
            <Button variant="primary" onClick={onConfirm}>
              {s("ledger.importConfirm")}
            </Button>
          </div>
        </>
      )}
    </Modal>
  );
}
