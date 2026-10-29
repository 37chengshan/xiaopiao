import { type Cents, type Txn } from "@/domain/types";
import { hashString } from "@/logic/time";
import { getLocale } from "@/lib/strings";
import { monthStartOf } from "./statsModel";

/**
 * Card wallet entries. Only the issuer and the last four digits exist on the web; the full number
 * stays on the phone (docs/WEB-SPEC.md §A.3). Local placeholder until the card service exists.
 */
export interface WalletCard {
  id: string;
  bank: { zh: string; en: string };
  kind: "debit" | "credit";
  tail: string;
  isDefault: boolean;
}

export const WALLET: readonly WalletCard[] = [
  { id: "c-1", bank: { zh: "招商银行", en: "China Merchants Bank" }, kind: "credit", tail: "4821", isDefault: true },
  { id: "c-2", bank: { zh: "中国银行", en: "Bank of China" }, kind: "debit", tail: "7130", isDefault: false },
  { id: "c-3", bank: { zh: "交通银行", en: "Bank of Communications" }, kind: "credit", tail: "2296", isDefault: false },
];

/** Issuer name in the reading language; callers subscribe through `useLocale()`. */
export function bankName(card: WalletCard): string {
  return getLocale() === "en" ? card.bank.en : card.bank.zh;
}

export type CardTone = "ink" | "fill" | "outline";

/** The default card is the dark one; the others alternate so the stack keeps some depth. */
export function cardTone(card: WalletCard, cards: readonly WalletCard[], isDefault: boolean): CardTone {
  if (isDefault) return "ink";
  return cards.indexOf(card) % 2 === 1 ? "fill" : "outline";
}

export interface CardSpend {
  cents: Cents;
  count: number;
}

/**
 * This month's spending attributed to one card. Web transactions do not carry a card id yet — the
 * phone learns it from the payment notification — so here a stable hash of the transaction id stands
 * in for that link. The totals still move with the ledger instead of being a fixed number.
 */
export function monthSpend(txns: readonly Txn[], now: number, card: WalletCard, cards: readonly WalletCard[]): CardSpend {
  const from = monthStartOf(now);
  const slot = cards.findIndex((entry) => entry.id === card.id);
  if (slot < 0) return { cents: 0, count: 0 };
  let cents = 0;
  let count = 0;
  for (const txn of txns) {
    if (txn.direction !== "OUT" || txn.occurredAt < from || txn.occurredAt > now) continue;
    if (hashString(txn.id) % cards.length !== slot) continue;
    cents += txn.amountCents;
    count += 1;
  }
  return { cents, count };
}
