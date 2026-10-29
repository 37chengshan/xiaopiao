import { cn } from "@/lib/utils";

/**
 * A receipt drawn in HTML (never an image) so the same component works inside the hero's paper slot,
 * the demo panel, the archive marquee and the bento cards. Content is passed in: the landing page
 * uses anonymised sample figures, the app has its own paper component on the App side.
 */
export interface ReceiptRow {
  label: string;
  amount: string;
}

export function MiniReceipt({
  date,
  brand = "今日小票",
  rows,
  total,
  stamp,
  className,
  dense = false,
  large = false,
}: {
  date: string;
  brand?: string;
  rows: ReceiptRow[];
  total: string;
  stamp?: string;
  className?: string;
  dense?: boolean;
  large?: boolean;
}) {
  return (
    <div data-ui="" className={cn("lp-mini", dense && "!p-2", className)}>
      <p className={cn("text-center font-semibold tracking-[0.32em]", large ? "text-[13px]" : "text-[10px]")}>{brand}</p>
      <p className={cn("mt-1 text-center opacity-60", large ? "text-[9.5px]" : "text-[8px]")}>{date}</p>
      <span className="lp-mini-rule" />
      {rows.map((row) => (
        <div key={row.label} className="lp-mini-row" style={large ? { fontSize: 10.5, lineHeight: 1.85 } : undefined}>
          <span className="truncate opacity-80">{row.label}</span>
          <span className="num shrink-0">{row.amount}</span>
        </div>
      ))}
      <span className="lp-mini-rule" />
      <div className={cn("lp-mini-row font-semibold", large ? "!text-[13px]" : "!text-[13px]")}>
        <span>合计</span>
        <span className="num">{total}</span>
      </div>
      {stamp && (
        <div className="mt-2 flex justify-end">
          <span className="lp-stamp">{stamp}</span>
        </div>
      )}
    </div>
  );
}
