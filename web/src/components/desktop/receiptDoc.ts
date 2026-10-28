import { useMemo } from "react";
import { buildDailyReceipt } from "@/logic/dailyReceipt";
import { addDays } from "@/logic/time";
import { buildReceiptDoc, totalOutOn, type ReceiptDoc, type Variant } from "@/features/receipt/receiptModel";
import { useLedger } from "@/services/LedgerProvider";

/** Receipt document for any day, with yesterday's total looked up from the ledger (same numbers as the phone app). */
export function useDayDoc(dayStart: number, variant: Variant = "full"): ReceiptDoc {
  const { txns, now } = useLedger();
  return useMemo(
    () => buildReceiptDoc(buildDailyReceipt(txns, dayStart, now), { now, prevTotalCents: totalOutOn(txns, addDays(dayStart, -1)), variant }),
    [txns, now, dayStart, variant],
  );
}
