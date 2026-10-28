import { Delete } from "lucide-react";
import { useState } from "react";
import { toast } from "sonner";
import { Drawer, DrawerContent, DrawerDescription, DrawerTitle } from "@/components/ui/drawer";
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
      toast.success(request.editing ? "已更新 ✏️" : "记好啦 ✅", { description: `${name} ¥${cents / 100}` });
      onOpenChange(false);
    } catch {
      toast.error("没记上，再试一次吧");
    } finally {
      setSaving(false);
    }
  };

  return (
    <Drawer open={open} onOpenChange={onOpenChange}>
      <DrawerContent className="mx-auto max-h-[92dvh] max-w-[430px] bg-canvas outline-none" onKeyDown={keyHandler}>
        <DrawerTitle className="sr-only">{request.editing ? "编辑账目" : "记一笔"}</DrawerTitle>
        <DrawerDescription className="sr-only">手动记账或导入支付宝、微信账单</DrawerDescription>
        <div className="mx-4 mt-3 flex rounded-full bg-secondary p-1" role="tablist">
          {(["manual", "import"] as const).map((t) => (
            <button
              key={t}
              role="tab"
              aria-selected={tab === t}
              onClick={() => setTab(t)}
              className={cn("flex-1 rounded-full py-1.5 text-sm font-semibold transition", tab === t ? "bg-card text-ink shadow-sm" : "text-ink-2")}
            >
              {t === "manual" ? (request.editing ? "编辑" : "手动记") : "导入账单"}
            </button>
          ))}
        </div>

        {tab === "import" ? (
          <ImportPanel onDone={() => onOpenChange(false)} />
        ) : (
          <div className="flex min-h-0 flex-col overflow-y-auto px-4 pb-[max(1rem,env(safe-area-inset-bottom))] pt-3">
            <div className="flex items-end justify-between">
              <div className="flex rounded-full bg-secondary p-0.5 text-xs font-semibold">
                {(["OUT", "IN"] as const).map((d) => (
                  <button key={d} onClick={() => setDirection(d)} aria-pressed={direction === d} className={cn("rounded-full px-3 py-1", direction === d ? "bg-card text-ink shadow-sm" : "text-ink-2")}>
                    {d === "OUT" ? "支出" : "收入"}
                  </button>
                ))}
              </div>
              <output aria-live="polite" className="thermal text-[44px] font-extrabold leading-none text-ink">
                <span className="mr-1 text-2xl text-ink-2">¥</span>
                {amount || "0"}
              </output>
            </div>

            <div className="mt-4 grid grid-cols-5 gap-1.5" role="radiogroup" aria-label="分类">
              {(Object.keys(CATEGORIES) as Category[]).map((c) => (
                <button
                  key={c}
                  role="radio"
                  aria-checked={category === c}
                  onClick={() => {
                    setCategory(c);
                    setTouchedCat(true);
                  }}
                  className={cn("flex flex-col items-center rounded-2xl border py-1.5 text-[10px] transition", category === c ? "border-coral bg-coral-soft text-ink" : "border-transparent bg-card text-ink-2")}
                >
                  <span className="text-xl" aria-hidden>
                    {CATEGORIES[c].emoji}
                  </span>
                  <span className="truncate">{CATEGORIES[c].label}</span>
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
              className="mt-3 w-full rounded-2xl border border-hairline bg-card px-4 py-2.5 text-[15px] outline-none focus:border-coral"
            />

            <div className="mt-2 flex flex-wrap items-center gap-1.5">
              {[0, 1, 2].map((back) => {
                const k = dateKey(addDays(today, -back));
                return (
                  <button key={back} onClick={() => setDay(k)} aria-pressed={day === k} className={cn("rounded-full px-3 py-1 text-xs font-semibold", day === k ? "bg-ink text-white" : "bg-card text-ink-2")}>
                    {["今天", "昨天", "前天"][back]}
                  </button>
                );
              })}
              <input type="date" value={day} max={dateKey(today)} onChange={(e) => e.target.value && setDay(e.target.value)} aria-label="日期" className="rounded-full bg-card px-3 py-1 text-xs text-ink-2" />
              {init.occurredAt && dateKey(init.occurredAt) === day && <span className="text-[11px] text-ink-2">{hhmm(init.occurredAt)}</span>}
            </div>

            <div className="mt-3 grid grid-cols-3 gap-1.5">
              {KEYS.map((k) => (
                <button
                  key={k}
                  onClick={() => setAmount((a) => pressKey(a, k))}
                  aria-label={k === "⌫" ? "删除" : k}
                  className="grid h-12 place-items-center rounded-2xl bg-card text-xl font-semibold text-ink transition active:scale-95 active:bg-secondary"
                >
                  {k === "⌫" ? <Delete className="size-5" /> : k}
                </button>
              ))}
            </div>
            <button
              onClick={save}
              disabled={cents <= 0 || saving}
              className="mt-3 h-12 rounded-2xl bg-coral text-base font-bold text-white transition active:scale-[0.98] disabled:opacity-40"
            >
              {request.editing ? "保存修改" : "记下来"}
            </button>
          </div>
        )}
      </DrawerContent>
    </Drawer>
  );
}
