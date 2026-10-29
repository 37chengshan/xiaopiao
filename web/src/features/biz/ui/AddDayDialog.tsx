import { useEffect, useMemo, useState } from "react";
import { centsToInput, dateKey, money, parseDayText, parseMoneyText, startOfDay } from "@/logic/biz/format";
import { entryFor, type RevenueEntry } from "@/logic/biz/book";
import { useBizStrings } from "../strings";
import { fill } from "../text";
import { Button, Modal } from "./primitives";

/**
 * Manual entry for one day: what the owner knows that the CSV never had. Opening on a day that
 * already holds numbers pre-fills them, and saving over such a day asks 覆盖 / 跳过 first — the
 * dialog used to wipe orders and note silently.
 */
export function AddDayDialog({
  open,
  today,
  day,
  book,
  onClose,
  onSave,
}: {
  open: boolean;
  today: number;
  day: number;
  book: readonly RevenueEntry[];
  onClose: () => void;
  onSave: (entry: RevenueEntry) => void;
}) {
  const t = useBizStrings();
  const [date, setDate] = useState(dateKey(day));
  const [revenue, setRevenue] = useState("");
  const [orders, setOrders] = useState("");
  const [note, setNote] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [confirming, setConfirming] = useState(false);

  const existing = useMemo(() => {
    const parsed = parseDayText(date, today);
    return parsed === null ? null : entryFor(book, parsed);
  }, [book, date, today]);

  const prefill = (value: string) => {
    const parsed = parseDayText(value, today);
    const entry = parsed === null ? null : entryFor(book, parsed);
    setRevenue(entry ? centsToInput(entry.revenueCents) : "");
    setOrders(entry && entry.orders > 0 ? `${entry.orders}` : "");
    setNote(entry?.note ?? "");
    setError(null);
    setConfirming(false);
  };

  useEffect(() => {
    if (!open) return;
    const value = dateKey(day);
    setDate(value);
    prefill(value);
    // Re-seed whenever the dialog opens; `book` only changes when this dialog (or a sibling) saves.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, day, today, book]);

  const submit = () => {
    const parsedDay = parseDayText(date, today);
    if (parsedDay === null) return setError(t("import.error.date"));
    if (parsedDay > today) return setError(t("add.future"));
    const cents = parseMoneyText(revenue);
    if (cents === null) return setError(t("add.invalidMoney"));
    if (cents < 0) return setError(t("import.error.negative"));
    const orderCount = orders.trim() === "" ? 0 : Number(orders.trim());
    if (!Number.isInteger(orderCount) || orderCount < 0) return setError(t("add.invalidOrders"));
    const next: RevenueEntry = {
      day: startOfDay(parsedDay),
      revenueCents: cents,
      orders: orderCount,
      note: note.trim().slice(0, 40),
      source: "MANUAL",
    };
    if (entryFor(book, next.day) && !confirming) {
      setConfirming(true);
      return;
    }
    onSave(next);
    onClose();
  };

  return (
    <Modal
      open={open}
      onClose={onClose}
      title={t("add.title")}
      width={420}
      footer={
        confirming ? (
          <>
            <Button tone="fill" onClick={onClose}>
              {t("import.policy.keep")}
            </Button>
            <Button tone="solid" onClick={submit}>
              {t("import.policy.replace")}
            </Button>
          </>
        ) : (
          <>
            <Button tone="fill" onClick={onClose}>
              {t("import.cancel")}
            </Button>
            <Button tone="solid" onClick={submit}>
              {t("add.save")}
            </Button>
          </>
        )
      }
    >
      <form
        className="space-y-3"
        onSubmit={(e) => {
          e.preventDefault();
          submit();
        }}
      >
        <Field label={t("add.date")}>
          <input
            type="date"
            value={date}
            max={dateKey(today)}
            onChange={(e) => {
              setDate(e.target.value);
              prefill(e.target.value);
            }}
            className="h-8 w-full rounded-lg bg-d-fill px-2.5 text-[13px] text-d-ink outline-none focus-visible:ring-2 focus-visible:ring-d-accent-ink/45"
          />
        </Field>
        <Field label={t("add.revenue")}>
          <input
            inputMode="decimal"
            value={revenue}
            placeholder={centsToInput(128_050)}
            onChange={(e) => {
              setRevenue(e.target.value);
              setConfirming(false);
            }}
            className="h-8 w-full rounded-lg bg-d-fill px-2.5 text-[13px] tabular-nums text-d-ink outline-none focus-visible:ring-2 focus-visible:ring-d-accent-ink/45"
            autoFocus
          />
        </Field>
        <Field label={t("add.orders")}>
          <input
            inputMode="numeric"
            value={orders}
            placeholder="86"
            onChange={(e) => {
              setOrders(e.target.value);
              setConfirming(false);
            }}
            className="h-8 w-full rounded-lg bg-d-fill px-2.5 text-[13px] tabular-nums text-d-ink outline-none focus-visible:ring-2 focus-visible:ring-d-accent-ink/45"
          />
        </Field>
        <Field label={t("add.note")}>
          <input
            value={note}
            maxLength={40}
            onChange={(e) => {
              setNote(e.target.value);
              setConfirming(false);
            }}
            className="h-8 w-full rounded-lg bg-d-fill px-2.5 text-[13px] text-d-ink outline-none focus-visible:ring-2 focus-visible:ring-d-accent-ink/45"
          />
        </Field>
        {confirming && existing && (
          <div className="rounded-lg bg-d-warning/[8%] px-3 py-2.5">
            <p className="text-[12px] leading-4 text-d-ink">
              {fill(t("add.existing"), money(existing.revenueCents), `${existing.orders}`, t("unit.orders"))}
            </p>
            {existing.note && <p className="mt-0.5 text-[12px] leading-4 text-d-ink-2">{fill(t("add.existingNote"), existing.note)}</p>}
            <p className="mt-1 text-[11px] leading-4 text-d-ink-2">{t("add.overwriteAsk")}</p>
          </div>
        )}
        {error && <p className="text-[12px] leading-4 text-d-warning">{error}</p>}
        <button type="submit" className="hidden" aria-hidden tabIndex={-1} />
      </form>
    </Modal>
  );
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <label className="block">
      <span className="mb-1 block text-[12px] font-medium leading-4 text-d-ink-2">{label}</span>
      {children}
    </label>
  );
}
