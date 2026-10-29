/**
 * The stamp a day earns, ported 1:1 from the app (`ui/today/ReceiptLayout.dayTitle`), so the landing's
 * "stamp your day" shows exactly what the receipt would print. Only the day's own spending decides it;
 * 钱包休眠者 is reserved for a day with nothing spent.
 */
export type SpendKind =
  | "meal"
  | "drink"
  | "snack"
  | "grocery"
  | "transport"
  | "shopping"
  | "fun"
  | "subscription";

export type TitleKey =
  | "sleeping"
  | "lavish"
  | "maniac"
  | "smallJoys"
  | "oneAndDone"
  | SpendKind;

export interface Spend {
  kind: SpendKind;
  cents: number;
}

export function dayTitle(spends: readonly Spend[]): TitleKey {
  const total = spends.reduce((sum, s) => sum + s.cents, 0);
  if (spends.length === 0 || total === 0) return "sleeping";
  if (total >= 500_00) return "lavish";
  if (spends.length >= 8) return "maniac";
  if (total < 20_00) return "smallJoys";
  if (spends.length === 1) return "oneAndDone";
  const byKind = new Map<SpendKind, number>();
  for (const s of spends) byKind.set(s.kind, (byKind.get(s.kind) ?? 0) + s.cents);
  let top: SpendKind = spends[0].kind;
  for (const [kind, cents] of byKind) if (cents > (byKind.get(top) ?? 0)) top = kind;
  return top;
}

/** The one sentence the receipt says about the day: it records, it never advises. */
export type RemarkKey = "remark.zero" | "remark.small" | "remark.normal" | "remark.big";

export function dayRemark(totalCents: number): RemarkKey {
  if (totalCents === 0) return "remark.zero";
  if (totalCents < 20_00) return "remark.small";
  if (totalCents >= 500_00) return "remark.big";
  return "remark.normal";
}
