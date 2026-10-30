import { useState } from "react";
import { useLocale } from "@/lib/strings";
import { dayRemark, dayTitle, type SpendKind } from "../dayTitle";
import { SPEND_ITEMS, receiptAmount } from "../lib/days";
import { useCountUp, useInViewOnce, useRotator } from "../hooks/useLiveMotion";
import { s } from "../strings";

/**
 * Bento: seven cards, each one a real interface fragment with its own small motion. Only the last card is
 * interactive (tap what you spent and the day's title is recomputed with the app's own rules).
 */
const SPARK = [0.42, 0.6, 0.34, 0.72, 0.55, 0.9, 0.64];
const WAVE = [6, 12, 20, 9, 26, 14, 7, 18, 24, 11, 5, 16, 28, 13, 8, 21, 10, 25, 15, 6, 19, 27, 12, 9, 22, 17, 7, 14];
const SHOP = [
  { key: "bento.shop.mon", v: 0.52 },
  { key: "bento.shop.tue", v: 0.66 },
  { key: "bento.shop.wed", v: 0.44 },
  { key: "bento.shop.thu", v: 0.78 },
  { key: "bento.shop.fri", v: 1 },
  { key: "bento.shop.sat", v: 0.9 },
  { key: "bento.shop.sun", v: 0.58 },
] as const;

const MEMBERS = [
  { who: "bento.awards.m1", title: "bento.awards.1" },
  { who: "bento.awards.m2", title: "bento.awards.2" },
  { who: "bento.awards.m3", title: "bento.awards.4" },
  { who: "bento.awards.m4", title: "bento.awards.1" },
] as const;

function TodayCard() {
  const [ref, seen] = useInViewOnce<HTMLDivElement>();
  const value = useCountUp(136_00, seen, { drift: 50 });
  return (
    <article className="lx-card lx-card--today" ref={ref}>
      <p className="lx-card__label">{s("bento.today")}</p>
      <p className="lx-card__big">
        <span className="lx-card__unit">¥</span>
        <span className="lx-card__digits">{(value / 100).toFixed(2)}</span>
      </p>
      <div className="lx-card__meta">
        <span>{s("bento.today.count")}</span>
        <span className="lx-card__delta">↓ {s("bento.today.delta")}</span>
      </div>
      <div className={`lx-spark${seen ? " is-live" : ""}`} aria-hidden="true">
        {SPARK.map((h, i) => (
          <i key={i} style={{ ["--h" as string]: `${h * 100}%`, ["--d" as string]: `${i * 60}ms` }} />
        ))}
      </div>
    </article>
  );
}

function NotifyCard() {
  return (
    <article className="lx-card lx-card--notify">
      <p className="lx-card__label">{s("bento.notify")}</p>
      <div className="lx-notify" aria-hidden="true">
        <div className="lx-notify__toast">
          <span className="lx-notify__dot" />
          <span className="lx-notify__text">{s("bento.notify.line")}</span>
          <span className="lx-notify__amt">28.00</span>
        </div>
        <div className="lx-notify__list">
          <div className="lx-notify__row">
            <span>09:20</span>
            <span>{s("spend.metro")}</span>
            <span>¥4.00</span>
          </div>
          <div className="lx-notify__row">
            <span>11:30</span>
            <span>{s("spend.tea")}</span>
            <span>¥16.00</span>
          </div>
          <div className="lx-notify__row lx-notify__row--fresh">
            <span>12:30</span>
            <span>{s("bento.notify.row")}</span>
            <span>¥28.00</span>
          </div>
        </div>
      </div>
      <p className="lx-card__foot">{s("bento.notify.foot")}</p>
    </article>
  );
}

function AiCard() {
  return (
    <article className="lx-card lx-card--ai">
      <p className="lx-card__label">{s("bento.ai")}</p>
      <div className="lx-chat" aria-hidden="true">
        <p className="lx-chat__q">{s("bento.ai.q")}</p>
        <p className="lx-chat__a">{s("bento.ai.a")}</p>
        <div className="lx-wave">
          {WAVE.map((h, i) => (
            <i key={i} style={{ ["--h" as string]: `${h * 3.4}%`, ["--d" as string]: `${(i % 9) * 90}ms` }} />
          ))}
        </div>
      </div>
      <p className="lx-card__foot">{s("bento.ai.hint")}</p>
    </article>
  );
}

function AwardsCard() {
  const [ref, seen] = useInViewOnce<HTMLDivElement>();
  const index = useRotator(MEMBERS.length, seen, 2400);
  return (
    <article className="lx-card lx-card--awards" ref={ref}>
      <p className="lx-card__label">{s("bento.awards")}</p>
      <ul className="lx-awards" aria-live="polite">
        {MEMBERS.map((member, i) => (
          <li key={member.who} className={`lx-awards__row${i === index ? " is-active" : ""}`}>
            <span className="lx-awards__who">
              <i aria-hidden="true">{s(member.who).slice(0, 1)}</i>
              {s(member.who)}
            </span>
            <span className="lx-awards__title">{s(member.title)}</span>
          </li>
        ))}
      </ul>
      <p className="lx-card__foot">{s("bento.awards.foot")}</p>
    </article>
  );
}

function PrintCard() {
  const [ref, seen] = useInViewOnce<HTMLDivElement>();
  return (
    <article className="lx-card lx-card--print" ref={ref}>
      <p className="lx-card__label">
        <span className={`lx-pulse${seen ? " is-live" : ""}`} aria-hidden="true" />
        {s("bento.print")}
      </p>
      <p className="lx-card__body">{s("bento.print.line")}</p>
      <div className={`lx-feed${seen ? " is-live" : ""}`} aria-hidden="true">
        <span className="lx-feed__bar" />
      </div>
      <p className="lx-card__foot">{s("bento.print.eta")}</p>
    </article>
  );
}

function ShopCard() {
  const [ref, seen] = useInViewOnce<HTMLDivElement>();
  return (
    <article className="lx-card lx-card--shop" ref={ref}>
      <p className="lx-card__label">{s("bento.shop")}</p>
      <div className={`lx-bars${seen ? " is-live" : ""}`} aria-hidden="true">
        {SHOP.map((d, i) => (
          <div className="lx-bars__col" key={d.key}>
            <span style={{ ["--h" as string]: `${d.v * 100}%`, ["--d" as string]: `${i * 70}ms` }} />
            <em>{s(d.key)}</em>
          </div>
        ))}
      </div>
      <p className="lx-card__foot">{s("bento.shop.foot")}</p>
    </article>
  );
}

function StampCard() {
  const locale = useLocale();
  const [picked, setPicked] = useState<ReadonlySet<number>>(() => new Set<number>());
  const spends = [...picked].map((i) => ({ kind: SPEND_ITEMS[i].kind as SpendKind, cents: SPEND_ITEMS[i].cents }));
  const total = spends.reduce((sum, x) => sum + x.cents, 0);
  const title = total === 0 ? null : s(`title.${dayTitle(spends)}` as Parameters<typeof s>[0]);
  const remark = s(dayRemark(total));

  const toggle = (i: number) => {
    setPicked((prev) => {
      const next = new Set(prev);
      if (next.has(i)) next.delete(i);
      else next.add(i);
      return next;
    });
  };

  return (
    <article className="lx-card lx-card--stamp">
      <div className="lx-stamp__intro">
        <p className="lx-card__label">{s("bento.stamp.title")}</p>
        <p className="lx-card__body">{s("bento.stamp.lead")}</p>
        <div className="lx-stamp__sum">
          <span className="lx-stamp__sumLabel">{s("bento.stamp.total")}</span>
          <span className="lx-stamp__sumValue">¥{receiptAmount(total)}</span>
          <span className="lx-stamp__sumCount">
            {picked.size === 0 ? s("bento.stamp.empty") : locale === "en" ? `${picked.size}×` : `${picked.size} 笔`}
          </span>
        </div>
        <div className={`lx-stamp__badge${title ? " is-on" : ""}`} aria-live="polite">
          <span className="lx-stamp__badgeText">{title ?? "—"}</span>
        </div>
        <p className="lx-stamp__remark">{title ? remark : s("bento.stamp.hint")}</p>
        {picked.size > 0 ? (
          <button type="button" className="lx-textbtn" onClick={() => setPicked(new Set())} data-cursor="link">
            {s("bento.stamp.reset")}
          </button>
        ) : null}
      </div>
      <div className="lx-stamp__grid" role="group" aria-label={s("bento.stamp.title")}>
        {SPEND_ITEMS.map((item, i) => {
          const on = picked.has(i);
          return (
            <button
              key={item.label}
              type="button"
              className={`lx-spend${on ? " is-on" : ""}`}
              aria-pressed={on}
              onClick={() => toggle(i)}
              data-cursor="card"
            >
              <span className="lx-spend__shot">
                <img src={item.img} alt="" loading="lazy" decoding="async" />
              </span>
              <span className="lx-spend__name">{s(item.label as Parameters<typeof s>[0])}</span>
              <span className="lx-spend__check" aria-hidden="true">
                ✓
              </span>
            </button>
          );
        })}
      </div>
    </article>
  );
}

export function Bento() {
  return (
    <section id="bento" className="lx-bento">
      <header className="lx-sechead">
        <p className="lx-eyebrow">03</p>
        <h2 className="lx-h2">{s("bento.heading")}</h2>
        <p className="lx-lead">{s("bento.lead")}</p>
      </header>
      <div className="lx-bento__grid">
        <TodayCard />
        <NotifyCard />
        <AiCard />
        <AwardsCard />
        <PrintCard />
        <ShopCard />
        <StampCard />
      </div>
    </section>
  );
}
