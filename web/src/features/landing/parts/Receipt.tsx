import { s } from "../strings";

export type ReceiptLineKey = "receipt.metro" | "receipt.lunch" | "receipt.coffee" | "receipt.dinner";

export const DAY_LINES: { key: ReceiptLineKey; time: string; amount: string }[] = [
  { key: "receipt.metro", time: "08:24", amount: "4.00" },
  { key: "receipt.lunch", time: "12:30", amount: "28.00" },
  { key: "receipt.coffee", time: "15:40", amount: "18.00" },
  { key: "receipt.dinner", time: "19:20", amount: "86.00" },
];

/**
 * Today's receipt as the app prints it (a real interface fragment: `data-ui` keeps its own monospace and
 * dashed rules). [printed] lines are fed out one by one; [done] lands the total, the remark and the stamp.
 */
export function Receipt({ printed, done }: { printed: number; done: boolean }) {
  return (
    <div className="lp3-receipt" data-ui aria-label={s("receipt.title")}>
      <div className="lp3-receipt__title">{s("receipt.title")}</div>
      <div className="lp3-receipt__date">{s("receipt.date")}</div>
      <div className="lp3-receipt__dash" />
      {DAY_LINES.map((line, i) => (
        <div key={line.key} className={`lp3-receipt__line${i < printed ? " is-on" : ""}`} aria-hidden={i >= printed}>
          <div>
            <span>
              {line.time} {s(line.key)}
            </span>
            <span>{line.amount}</span>
          </div>
        </div>
      ))}
      <div className="lp3-receipt__dash" />
      <div className="lp3-receipt__total">
        <span>
          {s("receipt.total")} · {s("receipt.count")}
        </span>
        <span>¥136.00</span>
      </div>
      <div className={`lp3-receipt__line${done ? " is-on" : ""}`}>
        <div className="lp3-receipt__remark">{s("receipt.remark")}</div>
      </div>
      <div className={`lp3-receipt__stamp${done ? " is-on" : ""}`}>{s("receipt.stamp")}</div>
      <div className="lp3-receipt__foot">{s("receipt.foot")}</div>
    </div>
  );
}
