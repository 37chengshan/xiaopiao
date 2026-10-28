import { useMemo } from "react";
import { useLedger } from "@/services/LedgerProvider";
import type { DailyReceipt } from "../../domain/types";
import { addDays } from "../../logic/time";
import { buildReceiptDoc, totalOutOn, type ReceiptDoc, type Variant } from "./receiptModel";

/** A receipt document with yesterday's total looked up from the ledger. */
export function useReceiptDoc(receipt: DailyReceipt, variant: Variant = "full"): ReceiptDoc {
  const { txns, now, mode } = useLedger();
  return useMemo(
    () => buildReceiptDoc(receipt, { now, prevTotalCents: totalOutOn(txns, addDays(receipt.dayStart, -1)), demo: mode === "demo", variant }),
    [receipt, txns, now, mode, variant],
  );
}
