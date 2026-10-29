import { useEffect, useState } from "react";
import { centsToInput, dateKey, parseDayText, parseMoneyText, startOfDay } from "@/logic/biz/format";
import type { RevenueEntry } from "@/logic/biz/book";
import { useBizStrings } from "../strings";
import { Button, Modal } from "./primitives";

/** Manual entry for one day: what the owner knows that the CSV never had. */
export function AddDayDialog({
  open,
  today,
  day,
  onClose,
  onSave,
}: {
  open: boolean;
  today: number;
  day: number;
  onClose: () => void;
  onSave: (entry: RevenueEntry) => void;
}) {
  const t = useBizStrings();
  const [date, setDate] = useState(dateKey(day));
  const [revenue, setRevenue] = useState("");
  const [orders, setOrders] = useState("");
  const [note, setNote] = useState("");
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (open) {
      setDate(dateKey(day));
      setRevenue("");
      setOrders("");
      setNote("");
      setError(null);
    }
  }, [open, day]);

  const submit = () => {
    const parsedDay = parseDayText(date, today);
    if (parsedDay === null) return setError(t("import.error.date"));
    if (parsedDay > today) return setError(t("add.future"));
    const cents = parseMoneyText(revenue);
    if (cents === null) return setError(t("add.invalidMoney"));
    if (cents < 0) return setError(t("import.error.negative"));
    const orderCount = orders.trim() === "" ? 0 : Number(orders.trim());
    if (!Number.isInteger(orderCount) || orderCount < 0) return setError(t("add.invalidOrders"));
    onSave({ day: startOfDay(parsedDay), revenueCents: cents, orders: orderCount, note: note.trim().slice(0, 40), source: "MANUAL" });
    onClose();
  };

  return (
    <Modal
      open={open}
      onClose={onClose}
      title={t("add.title")}
      width={420}
      footer={
        <>
          <Button tone="fill" onClick={onClose}>
            {t("import.cancel")}
          </Button>
          <Button tone="solid" onClick={submit}>
            {t("add.save")}
          </Button>
        </>
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
            onChange={(e) => setDate(e.target.value)}
            className="h-8 w-full rounded-lg bg-d-fill px-2.5 text-[13px] text-d-ink outline-none focus-visible:ring-2 focus-visible:ring-d-accent-ink/45"
          />
        </Field>
        <Field label={t("add.revenue")}>
          <input
            inputMode="decimal"
            value={revenue}
            placeholder={centsToInput(128_050)}
            onChange={(e) => setRevenue(e.target.value)}
            className="h-8 w-full rounded-lg bg-d-fill px-2.5 text-[13px] tabular-nums text-d-ink outline-none focus-visible:ring-2 focus-visible:ring-d-accent-ink/45"
            autoFocus
          />
        </Field>
        <Field label={t("add.orders")}>
          <input
            inputMode="numeric"
            value={orders}
            placeholder="86"
            onChange={(e) => setOrders(e.target.value)}
            className="h-8 w-full rounded-lg bg-d-fill px-2.5 text-[13px] tabular-nums text-d-ink outline-none focus-visible:ring-2 focus-visible:ring-d-accent-ink/45"
          />
        </Field>
        <Field label={t("add.note")}>
          <input
            value={note}
            maxLength={40}
            onChange={(e) => setNote(e.target.value)}
            className="h-8 w-full rounded-lg bg-d-fill px-2.5 text-[13px] text-d-ink outline-none focus-visible:ring-2 focus-visible:ring-d-accent-ink/45"
          />
        </Field>
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
