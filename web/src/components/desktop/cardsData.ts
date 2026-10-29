/**
 * Card wallet entries. Only the issuer and the last four digits exist on the web; the full number
 * stays on the phone (docs/WEB-SPEC.md §A.3). Local placeholder until the card service exists.
 */
export interface WalletCard {
  id: string;
  bank: string;
  kind: "debit" | "credit";
  tail: string;
  /** visual weight: the default card is the dark one */
  tone: "ink" | "fill" | "outline";
  isDefault: boolean;
}

export const WALLET: readonly WalletCard[] = [
  { id: "c-1", bank: "招商银行", kind: "credit", tail: "4821", tone: "ink", isDefault: true },
  { id: "c-2", bank: "中国银行", kind: "debit", tail: "7130", tone: "fill", isDefault: false },
  { id: "c-3", bank: "交通银行", kind: "credit", tail: "2296", tone: "outline", isDefault: false },
];
