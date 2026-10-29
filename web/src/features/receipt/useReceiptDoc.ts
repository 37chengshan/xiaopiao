import { useMemo } from "react";
import { useLocale } from "@/lib/strings";
import { useLedger } from "@/services/LedgerProvider";
import type { DailyReceipt } from "../../domain/types";
import { addDays } from "../../logic/time";
import { buildReceiptDoc, totalOutOn, type ReceiptDoc, type Variant } from "./receiptModel";

/** A receipt document with yesterday's total looked up from the ledger. */
export function useReceiptDoc(receipt: DailyReceipt, variant: Variant = "full"): ReceiptDoc {
  const locale = useLocale();
  const { txns, now } = useLedger();
  return useMemo(
    () => (
      void locale,
      buildReceiptDoc(receipt, { now, prevTotalCents: totalOutOn(txns, addDays(receipt.dayStart, -1)), variant })
    ),
    [receipt, txns, now, variant, locale],
  );
}
