import { Link } from "@tanstack/react-router";
import { useMemo, useState } from "react";
import { dayRemark, dayTitle, type SpendKind } from "../dayTitle";
import { IMG } from "../lib/assets";
import { s } from "../strings";

type SpendKey =
  | "spend.metro"
  | "spend.breakfast"
  | "spend.lunch"
  | "spend.tea"
  | "spend.movie"
  | "spend.grocery"
  | "spend.member"
  | "spend.taxi";

const SPENDS: { key: SpendKey; kind: SpendKind; cents: number; img: string }[] = [
  { key: "spend.metro", kind: "transport", cents: 4_00, img: "/img/spend-metro.webp" },
  { key: "spend.breakfast", kind: "meal", cents: 8_00, img: "/img/spend-breakfast.webp" },
  { key: "spend.lunch", kind: "meal", cents: 28_00, img: "/img/spend-lunch.webp" },
  { key: "spend.tea", kind: "drink", cents: 18_00, img: "/img/spend-tea.webp" },
  { key: "spend.movie", kind: "fun", cents: 45_00, img: "/img/spend-movie.webp" },
  { key: "spend.grocery", kind: "grocery", cents: 57_00, img: "/img/spend-grocery.webp" },
  { key: "spend.member", kind: "subscription", cents: 25_00, img: "/img/spend-member.webp" },
  { key: "spend.taxi", kind: "transport", cents: 32_00, img: "/img/spend-taxi.webp" },
];

const yuan = (cents: number) => `¥${(cents / 100).toFixed(2)}`;

/**
 * The live thing: tap what you spent today and the stamp changes, by the app's own rules (dayTitle.ts).
 * Full-bleed close-up of the printer slot behind; one solid card in the second column.
 */
export function Stamp() {
  const [on, setOn] = useState<ReadonlySet<SpendKey>>(new Set(["spend.metro", "spend.lunch", "spend.tea"]));
  const picked = useMemo(() => SPENDS.filter((x) => on.has(x.key)), [on]);
  const total = picked.reduce((sum, x) => sum + x.cents, 0);
  const title = dayTitle(picked);
  const last = picked.length ? picked[picked.length - 1] : null;

  const toggle = (key: SpendKey) =>
    setOn((prev) => {
      const next = new Set(prev);
      if (next.has(key)) next.delete(key);
      else next.add(key);
      return next;
    });

  return (
    <section className="lp3-show" aria-labelledby="lp3-stamp-h">
      <img className="lp3-show__bg" src={IMG.printerSlot} alt={s("stamp.bg")} width={1672} height={941} loading="lazy" />
      <div className="lp3-show__card">
        <h2 id="lp3-stamp-h" data-reveal>
          {s("stamp.heading")}
        </h2>
        <p className="lp3-show__lead" data-reveal style={{ ["--i" as string]: 1 }}>
          {s("stamp.lead")}
        </p>
        <div className="lp3-chips" role="group" aria-label={s("stamp.heading")}>
          {SPENDS.map((x) => (
            <button
              key={x.key}
              type="button"
              className="lp3-chip"
              aria-pressed={on.has(x.key)}
              data-cursor="card"
              onClick={() => toggle(x.key)}
            >
              <img className="lp3-chip__img" src={x.img} alt="" width={28} height={28} loading="lazy" />
              {s(x.key)} {(x.cents / 100).toFixed(0)}
            </button>
          ))}
        </div>
        <div className="lp3-show__result" aria-live="polite">
          <div key={title} className="lp3-show__title is-bump">
            {s(`title.${title}` as const)}
          </div>
          <div className="lp3-show__sum">
            {s("stamp.total")} {yuan(total)}
            <br />
            {s("stamp.items").replace("{n}", String(picked.length))}
          </div>
        </div>
        <p className="lp3-show__remark">{s(dayRemark(total))}</p>
        <img
          className="lp3-show__thumb"
          src={last ? last.img : IMG.handReceipt}
          alt={last ? s(last.key) : s("stamp.photo")}
          width={800}
          height={533}
          loading="lazy"
        />
        <Link to="/app" className="lp3-btn" data-cursor="cta">
          {s("stamp.cta")}
        </Link>
      </div>
    </section>
  );
}
