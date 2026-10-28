import { Delete } from "lucide-react";
import { useState } from "react";
import { toast } from "sonner";
import { CATEGORY_ICONS } from "@/components/app/categoryIcons";
import { IconTile, Segmented, XButton } from "@/components/app/kit";
import { Sheet } from "@/components/app/Sheet";
import { cn } from "@/lib/utils";
import { useLedger } from "@/services/LedgerProvider";
import { CATEGORIES, type Category, type Direction, type Txn, startOfDay } from "../../domain/types";
import { categorize } from "../../logic/categorize";
import { addDays, at, dateKey, hhmm, parseDateKey } from "../../logic/time";
import { ImportPanel } from "./ImportPanel";
import { type EntryRequest } from "./EntryProvider";

const KEYS = ["1", "2", "3", "4", "5", "6", "7", "8", "9", ".", "0", "⌫"] as const;

function pressKey(cur: string, key: string): string {
  if (key === "⌫") return cur.slice(0, -1);
  if (key === ".") return cur.includes(".") ? cur : cur === "" ? "0." : `${cur}.`;
  const [, dec] = cur.split(".");
  if (dec !== undefined && dec.length >= 2) return cur;
  if (cur === "0") return key;
  if (cur.replace(".", "").length >= 8) return cur;
  return cur + key;
}

export function EntryDrawer({ request, open, onOpenChange }: { request: EntryRequest; open: boolean; onOpenChange: (o: boolean) => void }) {
  const { add, update, now } = useLedger();
  const init = request.initial ?? {};
  const [tab, setTab] = useState<"manual" | "import">(request.tab ?? "manual");
  const [amount, setAmount] = useState(init.amountCents ? String(init.amountCents / 100) : "");
  const [direction, setDirection] = useState<Direction>(init.direction ?? "OUT");
  const [category, setCategory] = useState<Category>(init.category ?? "OTHER");
  const [touchedCat, setTouchedCat] = useState(init.category !== undefined);
  const [merchant, setMerchant] = useState(init.merchant ?? "");
  const [day, setDay] = useState(dateKey(init.occurredAt ?? now));
  const [saving, setSaving] = useState(false);

  const cents = Math.round(Number(amount || "0") * 100);
  const today = startOfDay(now);
  const keyHandler = (e: React.KeyboardEvent) => {
    if ((e.target as HTMLElement).tagName === "INPUT") return;
    if (/^[0-9.]$/.test(e.key)) setAmount((a) => pressKey(a, e.key));
    else if (e.key === "Backspace") setAmount((a) => pressKey(a, "⌫"));
  };

  const save = async () => {
    if (cents <= 0 || saving) return;
    setSaving(true);
    const dayStart = parseDateKey(day);
    const time = init.occurredAt && dateKey(init.occurredAt) === day ? init.occurredAt : dayStart === today ? now : at(dayStart, 12);
    const name = merchant.trim() || CATEGORIES[category].label;
    const txn: Txn = {
      id: init.id ?? `m-${now}-${Math.random().toString(36).slice(2, 7)}`,
      amountCents: cents,
      direction,
      merchant: name,
      category,
      occurredAt: time,
      source: init.source ?? "MANUAL",
      note: init.note,
      dedupeKey: init.dedupeKey,
    };
    try {
      if (request.onSave) request.onSave(txn);
      else if (request.editing) await update(txn);
      else await add([txn]);
      toast.success(request.editing ? "已更新" : "记好啦", { description: `${name} ¥${cents / 100}` });
      onOpenChange(false);
    } catch {
      toast.error("没记上，再试一次吧");
    } finally {
      setSaving(false);
    }
  };

  return (
    <Sheet open={open} onOpenChange={onOpenChange} title={request.editing ? "编辑账目" : "记一笔"} description="手动记账或导入支付宝、微信账单" onKeyDown={keyHandler}>
      <Segmented
        className="mx-4 mt-1 shrink-0"
        value={tab}
        onChange={setTab}
        options={[
          { value: "manual", label: request.editing ? "编辑" : "手动记" },
          { value: "import", label: "导入账单" },
        ]}
      />

      {tab === "import" ? (
        <ImportPanel onDone={() => onOpenChange(false)} />
      ) : (
        <div className="flex min-h-0 flex-col overflow-y-auto overscroll-contain px-4 pb-[max(1rem,env(safe-area-inset-bottom))] pt-4">
          <div className="flex items-center justify-between gap-3">
            <Segmented
              className="w-[9.5rem]"
              value={direction}
              onChange={setDirection}
              options={[
                { value: "OUT", label: "支出" },
                { value: "IN", label: "收入" },
              ]}
            />
            <output aria-live="polite" className={cn("num text-[48px] font-semibold leading-[52px] tracking-[-0.03em]", amount ? "text-ink" : "text-ink-3")}>
              <span className="mr-1 text-[0.5em] font-medium text-ink-2">¥</span>
              {amount || "0"}
            </output>
          </div>

          <div className="mt-5 grid grid-cols-7 gap-y-3" role="radiogroup" aria-label="分类">
            {(Object.keys(CATEGORIES) as Category[]).map((c) => (
              <button
                key={c}
                type="button"
                role="radio"
                aria-checked={category === c}
                onClick={() => {
                  setCategory(c);
                  setTouchedCat(true);
                }}
                className="flex flex-col items-center gap-1 transition-transform duration-100 ease-[var(--ease-out)] active:scale-[0.94]"
              >
                <IconTile icon={CATEGORY_ICONS[c]} size={38} tone={category === c ? "clay" : "neutral"} />
                <span className={cn("max-w-full truncate text-[10px] leading-[14px]", category === c ? "font-medium text-ink" : "text-ink-2")}>{CATEGORIES[c].label}</span>
              </button>
            ))}
          </div>

          <input
            value={merchant}
            onChange={(e) => {
              setMerchant(e.target.value);
              if (!touchedCat) setCategory(categorize(e.target.value));
            }}
            placeholder="商家 / 备注（选填）"
            aria-label="商家"
            className="mt-4 h-11 w-full rounded-[12px] bg-surface-2 px-3.5 text-[16px] text-ink outline-none placeholder:text-ink-3"
          />

          <div className="mt-2.5 flex flex-wrap items-center gap-1.5">
            {[0, 1, 2].map((back) => {
              const k = dateKey(addDays(today, -back));
              return (
                <button
                  key={back}
                  type="button"
                  onClick={() => setDay(k)}
                  aria-pressed={day === k}
                  className={cn("h-8 rounded-full px-3 text-[13px] font-medium transition-[transform,background-color,color] duration-150 active:scale-[0.97]", day === k ? "bg-ink text-bg" : "bg-fill text-ink-2")}
                >
                  {["今天", "昨天", "前天"][back]}
                </button>
              );
            })}
            <input type="date" value={day} max={dateKey(today)} onChange={(e) => e.target.value && setDay(e.target.value)} aria-label="日期" className="h-8 rounded-full bg-fill px-3 text-[13px] text-ink-2 outline-none" />
            {init.occurredAt && dateKey(init.occurredAt) === day && <span className="text-[12px] text-ink-2">{hhmm(init.occurredAt)}</span>}
          </div>

          <div className="mt-4 grid grid-cols-3 gap-2">
            {KEYS.map((k) => (
              <button
                key={k}
                type="button"
                onClick={() => setAmount((a) => pressKey(a, k))}
                aria-label={k === "⌫" ? "删除" : k}
                className="num grid h-12 place-items-center rounded-[12px] bg-fill text-[22px] font-medium text-ink transition-[transform,background-color] duration-100 ease-[var(--ease-out)] active:scale-[0.97] active:bg-surface-2"
              >
                {k === "⌫" ? <Delete className="size-5" strokeWidth={1.75} /> : k}
              </button>
            ))}
          </div>
          <XButton variant="primary" className="mt-3 w-full" onClick={save} disabled={cents <= 0 || saving}>
            {request.editing ? "保存修改" : "记下"}
          </XButton>
        </div>
      )}
    </Sheet>
  );
}
