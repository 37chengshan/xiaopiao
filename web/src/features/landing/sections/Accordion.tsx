import { useEffect, useRef, useState } from "react";
import { IMG } from "../lib/assets";
import { s } from "../strings";

/**
 * Five vertical cards: the one under the pointer (or focus) grows to four times its width, its photo
 * zooms and its copy fades in. Once the row enters the viewport it walks through all five and settles on
 * the first; the first touch of a pointer or key stops the walk for good.
 */
const CARDS = [
  { id: "auto", img: IMG.metroPhone, title: "f.auto.title", lines: ["f.auto.1", "f.auto.2", "f.auto.3", "f.auto.4"], alt: "f.alt.auto" },
  { id: "receipt", img: IMG.printerSlot, title: "f.receipt.title", lines: ["f.receipt.1", "f.receipt.2", "f.receipt.3", "f.receipt.4"], alt: "f.alt.receipt" },
  { id: "ai", img: IMG.receiptStack, title: "f.ai.title", lines: ["f.ai.1", "f.ai.2", "f.ai.3", "f.ai.4"], alt: "f.alt.ai" },
  { id: "circle", img: IMG.friendsTable, title: "f.circle.title", lines: ["f.circle.1", "f.circle.2", "f.circle.3", "f.circle.4"], alt: "f.alt.circle" },
  { id: "shop", img: IMG.shopCounter, title: "f.shop.title", lines: ["f.shop.1", "f.shop.2", "f.shop.3", "f.shop.4"], alt: "f.alt.shop" },
] as const;

export function Accordion() {
  const [active, setActive] = useState(0);
  const [stopped, setStopped] = useState(false);
  const [inView, setInView] = useState(false);
  const row = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const el = row.current;
    if (!el || typeof IntersectionObserver === "undefined") {
      setInView(true);
      return;
    }
    const io = new IntersectionObserver((entries) => setInView(entries.some((e) => e.isIntersecting)), {
      threshold: 0.35,
    });
    io.observe(el);
    return () => io.disconnect();
  }, []);

  // One lap, 1s per card, then rest on the first card.
  useEffect(() => {
    if (!inView || stopped) return;
    const id = window.setInterval(() => setActive((i) => (i + 1) % CARDS.length), 1000);
    const stop = window.setTimeout(() => {
      window.clearInterval(id);
      setActive(0);
    }, CARDS.length * 1000);
    return () => {
      window.clearInterval(id);
      window.clearTimeout(stop);
    };
  }, [inView, stopped]);

  const stop = () => setStopped(true);

  return (
    <section id="five" className="lx-five">
      <header className="lx-sechead lx-sechead--row">
        <div>
          <p className="lx-eyebrow">04</p>
          <h2 className="lx-h2">{s("five.heading")}</h2>
        </div>
        <p className="lx-hint">{s("five.hint")}</p>
      </header>

      <div className="lx-five__row" ref={row} onPointerEnter={stop} onFocus={stop}>
        {CARDS.map((card, i) => {
          const open = i === active;
          return (
            <article
              key={card.id}
              className={`lx-fivecard${open ? " is-open" : ""}`}
              style={{ flexGrow: open ? 4 : 1 }}
              tabIndex={0}
              aria-expanded={open}
              onPointerDown={() => {
                stop();
                setActive(i);
              }}
              onClick={() => {
                stop();
                setActive(i);
              }}
              onFocus={() => {
                stop();
                setActive(i);
              }}
              data-cursor="card"
            >
              <img className="lx-fivecard__img" src={card.img} alt={s(card.alt)} loading="lazy" decoding="async" />
              <span className="lx-fivecard__scrim" aria-hidden="true" />
              <span className="lx-fivecard__index" aria-hidden="true">
                0{i + 1}
              </span>
              <span className="lx-fivecard__vtitle">{s(card.title)}</span>
              <div className="lx-fivecard__body">
                <h3 className="lx-fivecard__title">{s(card.title)}</h3>
                <ul>
                  {card.lines.map((line) => (
                    <li key={line}>{s(line)}</li>
                  ))}
                </ul>
              </div>
            </article>
          );
        })}
      </div>
    </section>
  );
}
