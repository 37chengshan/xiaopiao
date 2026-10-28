import { Printer } from "lucide-react";
import { useEffect, useRef, useState } from "react";
import { XButton } from "@/components/app/kit";
import type { DailyReceipt } from "../../domain/types";
import { ReceiptPaper } from "./ReceiptPaper";
import { ReceiptStage } from "./ReceiptStage";
import { unlockAudio } from "./sound";
import { phoneColumnOf } from "./usePushBack";
import { useReceiptDoc } from "./useReceiptDoc";

/**
 * A day's receipt on the page: the finished paper, and one button that plays the print moment over the phone column.
 * `playKey` changing (another day selected) closes a running print.
 */
export function Receipt({ receipt, playKey = 0 }: { receipt: DailyReceipt; playKey?: number }) {
  const doc = useReceiptDoc(receipt);
  const anchor = useRef<HTMLDivElement>(null);
  const [column, setColumn] = useState<HTMLElement | null>(null);

  useEffect(() => setColumn(null), [playKey, receipt.dayStart]);

  const start = () => {
    unlockAudio();
    setColumn(phoneColumnOf(anchor.current));
  };

  return (
    <div ref={anchor} className="px-4 pb-8 pt-2">
      <div className={`mx-auto w-fit [filter:drop-shadow(0_6px_14px_rgb(0_0_0/0.12))] transition-opacity duration-150 ${column ? "opacity-0" : ""}`}>
        <ReceiptPaper doc={doc} stamp="shown" tickerRun instant />
      </div>
      <XButton variant="clay" className="mx-auto mt-6 flex w-full max-w-[304px]" onClick={start}>
        <Printer className="size-[18px]" strokeWidth={1.75} aria-hidden />
        打印这张小票
      </XButton>
      {column && <ReceiptStage receipt={receipt} column={column} onClose={() => setColumn(null)} />}
    </div>
  );
}
