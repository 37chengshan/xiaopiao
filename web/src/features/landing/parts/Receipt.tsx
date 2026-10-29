import { useLocale } from "@/lib/strings";
import { s } from "../strings";
import { dayDate, dayFacts, receiptAmount, type ShelfDay } from "../lib/days";

/**
 * One day as the thermal printer renders it. Sizes are all in `em`, so the same component prints at
 * shelf size (a 150px-wide front face) and at dialog size without a second layout.
 */
export function ReceiptPaper({ day, className = "" }: { day: ShelfDay; className?: string }) {
  const locale = useLocale();
  const date = dayDate(day, locale);
  const facts = dayFacts(day);

  return (
    <article className={`lx-receipt ${className}`} aria-label={`${s("shelf.receipt")} · ${date.full}`}>
      <div className="lx-receipt__brand">{s("shelf.receipt")}</div>
      <div className="lx-receipt__date">
        {date.full}
      </div>
      <div className="lx-receipt__rule" />
      {day.lines.length === 0 ? <div className="lx-receipt__quiet">{s("remark.zero")}</div> : null}
      {day.lines.map((line, i) => (
        <div className="lx-receipt__line" key={`${line.label}-${i}`}>
          <span className="lx-receipt__time">{line.time}</span>
          <span className="lx-receipt__what">{s(line.label as Parameters<typeof s>[0])}</span>
          <span className="lx-receipt__amt">{receiptAmount(line.cents)}</span>
        </div>
      ))}
      <div className="lx-receipt__rule" />
      <div className="lx-receipt__total">
        <span>
          {s("bento.stamp.total")} · {facts.count} {locale === "en" ? "" : "笔"}
        </span>
        <span className="lx-receipt__amt">¥{receiptAmount(facts.total)}</span>
      </div>
      <div className="lx-receipt__remark">{s(facts.remark)}</div>
      <div className="lx-receipt__code" aria-hidden="true" />
      <div className="lx-receipt__title">{s(`title.${facts.title}` as Parameters<typeof s>[0])}</div>
      <div className="lx-receipt__foot">{s("shelf.record")}</div>
    </article>
  );
}
