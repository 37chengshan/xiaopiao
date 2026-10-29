import { Check, X } from "lucide-react";
import { useState } from "react";
import { CATEGORIES, type Category, startOfDay } from "@/domain/types";
import { type DraftTxn } from "@/logic/quickAdd";
import { at, dateKey, dayDiff, hhmm, parseDateKey } from "@/logic/time";
import { cn } from "@/lib/utils";
import { categoryLabel } from "../labels";
import { money } from "../format";
import { s } from "../strings";
import { Button, inputClass } from "../ui";
import { s as ai } from "./copy/ai";

const CATEGORY_LIST = Object.keys(CATEGORIES) as Category[];

/**
 * Parsed rows stay editable until the human confirms them — the assistant only drafts.
 * Editing happens inline (no sheet): name, amount, category and day, time of day is kept.
 */
function DraftRow({
  draft,
  now,
  saved,
  onChange,
  onRemove,
}: {
  draft: DraftTxn;
  now: number;
  saved: boolean;
  onChange: (d: DraftTxn) => void;
  onRemove: (id: string) => void;
}) {
  const [editing, setEditing] = useState(false);
  const [name, setName] = useState(draft.merchant);
  const [amount, setAmount] = useState((draft.amountCents / 100).toFixed(2));
  const [category, setCategory] = useState<Category>(draft.category);
  const [day, setDay] = useState(dateKey(draft.occurredAt));

  const cents = Math.round(Number(amount) * 100);
  const valid = Number.isFinite(cents) && cents > 0 && /^\d{4}-\d{2}-\d{2}$/.test(day);

  const open = () => {
    setName(draft.merchant);
    setAmount((draft.amountCents / 100).toFixed(2));
    setCategory(draft.category);
    setDay(dateKey(draft.occurredAt));
    setEditing(true);
  };

  const commit = () => {
    if (!valid) return;
    const time = new Date(draft.occurredAt);
    onChange({
      ...draft,
      merchant: name.trim() || draft.merchant,
      amountCents: cents,
      category,
      occurredAt: at(startOfDay(parseDateKey(day)), time.getHours(), time.getMinutes()),
    });
    setEditing(false);
  };

  if (editing) {
    return (
      <form
        onSubmit={(e) => {
          e.preventDefault();
          commit();
        }}
        className="my-1 space-y-1.5 rounded-md bg-d-fill p-2"
      >
        <div className="flex gap-1.5">
          <input
            value={name}
            onChange={(e) => setName(e.target.value)}
            aria-label={ai("drafts.name")}
            autoFocus
            className={cn(inputClass, "h-8 min-w-0 flex-1 text-[12.5px]")}
          />
          <input
            value={amount}
            onChange={(e) => setAmount(e.target.value)}
            inputMode="decimal"
            aria-label={ai("drafts.amount")}
            className={cn(inputClass, "num h-8 w-[5rem] shrink-0 text-right text-[12.5px]")}
          />
          <input
            type="date"
            value={day}
            max={dateKey(now)}
            onChange={(e) => setDay(e.target.value)}
            aria-label={ai("drafts.date")}
            className={cn(inputClass, "h-8 w-[8.5rem] shrink-0 text-[12.5px]")}
          />
        </div>
        <div role="radiogroup" aria-label={ai("drafts.category")} className="grid grid-cols-4 gap-1">
          {CATEGORY_LIST.map((key) => {
            const on = key === category;
            return (
              <button
                key={key}
                type="button"
                role="radio"
                aria-checked={on}
                onClick={() => setCategory(key)}
                className={cn(
                  "d-press flex h-6 items-center gap-1 truncate rounded-md px-1.5 text-[11.5px] transition-colors",
                  on ? "bg-d-accent/10 text-d-accent-ink" : "bg-d-fill/80 text-d-ink-2 hover:text-d-ink",
                )}
              >
                <span aria-hidden>{CATEGORIES[key].emoji}</span>
                <span className="truncate">{categoryLabel(key)}</span>
              </button>
            );
          })}
        </div>
        <div className="flex justify-end gap-1.5">
          <Button size="sm" variant="ghost" onClick={() => setEditing(false)}>
            {s("common.cancel")}
          </Button>
          <Button size="sm" type="submit" variant="primary" disabled={!valid}>
            {ai("drafts.done")}
          </Button>
        </div>
      </form>
    );
  }

  const isToday = dayDiff(startOfDay(draft.occurredAt), startOfDay(now)) === 0;
  const when = `${isToday ? "" : `${dateKey(draft.occurredAt).slice(5)} `}${hhmm(draft.occurredAt)}`;

  return (
    <div className="flex items-center gap-0.5">
      <button
        type="button"
        disabled={saved}
        onClick={open}
        aria-label={saved ? undefined : ai("drafts.editAria").replace("{}", draft.merchant)}
        className="d-press flex min-h-9 min-w-0 flex-1 items-center gap-2 rounded-md px-1.5 py-1 text-left transition-colors hover:bg-d-fill/70 disabled:hover:bg-transparent"
      >
        <span aria-hidden>{CATEGORIES[draft.category].emoji}</span>
        <span className="min-w-0 flex-1">
          <span className="block truncate text-[12.5px] leading-[17px] text-d-ink">{draft.merchant}</span>
          <span className="block truncate text-[11px] leading-[15px] text-d-ink-3">
            {when} · {categoryLabel(draft.category)}
          </span>
        </span>
        <span className={cn("num shrink-0 text-[12.5px]", draft.direction === "IN" ? "text-d-positive" : "text-d-ink")}>
          {draft.direction === "IN" ? "+" : ""}
          {money(draft.amountCents)}
        </span>
      </button>
      {!saved && (
        <button
          type="button"
          onClick={() => onRemove(draft.id)}
          aria-label={ai("drafts.removeAria").replace("{}", draft.merchant)}
          className="d-press grid size-7 shrink-0 place-items-center rounded-md text-d-ink-3 hover:bg-d-fill hover:text-d-ink"
        >
          <X className="size-3.5" strokeWidth={2} aria-hidden />
        </button>
      )}
    </div>
  );
}

export function DraftsCard({
  drafts,
  now,
  saved,
  onChange,
  onRemove,
  onConfirm,
}: {
  drafts: readonly DraftTxn[];
  now: number;
  saved: boolean;
  onChange: (d: DraftTxn) => void;
  onRemove: (id: string) => void;
  onConfirm: () => void;
}) {
  const total = drafts.filter((d) => d.direction === "OUT").reduce((sum, d) => sum + d.amountCents, 0);
  return (
    <div role="group" aria-label={ai("drafts.group")} className="mt-2 rounded-lg bg-d-fill/50 p-2">
      {drafts.map((d) => (
        <DraftRow key={d.id} draft={d} now={now} saved={saved} onChange={onChange} onRemove={onRemove} />
      ))}
      {drafts.length === 0 && !saved && <p className="px-1 py-0.5 text-[12px] text-d-ink-3">{ai("drafts.empty")}</p>}

      <div className="mt-1 flex items-center justify-between gap-2 border-t border-d-hairline px-1.5 pt-1.5">
        <span className="text-[11.5px] text-d-ink-3">{ai("drafts.total")}</span>
        <span className="num text-[12.5px] font-medium text-d-ink">{money(total)}</span>
      </div>

      <div className="mt-1.5 flex justify-end px-1">
        {saved ? (
          <span className="flex items-center gap-1 text-[12px] text-d-positive">
            <Check className="size-3.5" strokeWidth={2.2} aria-hidden />
            {s("ai.confirmed")}
          </span>
        ) : (
          drafts.length > 0 && (
            <Button size="sm" variant="primary" onClick={onConfirm}>
              {s("ai.confirm")}
            </Button>
          )
        )}
      </div>
    </div>
  );
}
