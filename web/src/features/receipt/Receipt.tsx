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
        <div className="max-h-[500px] overflow-hidden [mask-image:linear-gradient(to_bottom,#000_72%,transparent)]">
          <ReceiptPaper doc={doc} stamp="shown" tickerRun instant />
        </div>
      </div>
      <XButton variant="clay" className="sticky bottom-3 mx-auto mt-4 flex w-full max-w-[304px] bg-[linear-gradient(180deg,color-mix(in_oklab,var(--clay)_86%,white),var(--clay))] shadow-[inset_0_1px_0_rgb(255_255_255/0.3),0_8px_18px_-6px_color-mix(in_oklab,var(--clay)_60%,transparent)] dark:shadow-[inset_0_1px_0_rgb(255_255_255/0.25),0_0_0_1px_color-mix(in_oklab,var(--clay)_70%,transparent),0_6px_22px_-6px_color-mix(in_oklab,var(--clay)_55%,transparent)]" onClick={start}>
        <Printer className="size-[18px]" strokeWidth={1.75} aria-hidden />
        打印
      </XButton>
      {column && <ReceiptStage receipt={receipt} column={column} onClose={() => setColumn(null)} />}
    </div>
  );
}
