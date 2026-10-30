import { Link } from "@tanstack/react-router";
import { useState } from "react";
import { s } from "../strings";

const FAQ = ["faq.q1", "faq.q2", "faq.q3", "faq.q4", "faq.q5", "faq.q6"] as const;
const ANSWERS = ["faq.a1", "faq.a2", "faq.a3", "faq.a4", "faq.a5", "faq.a6"] as const;

/** Closing: six honest answers, then the way in. */
export function Closing() {
  const [open, setOpen] = useState<number | null>(0);

  return (
    <>
      <section id="faq" className="lx-faq">
        <header className="lx-sechead lx-sechead--row">
          <div>
            <p className="lx-eyebrow">08</p>
            <h2 className="lx-h2">{s("faq.heading")}</h2>
          </div>
        </header>
        <ul className="lx-faq__list">
          {FAQ.map((q, i) => {
            const isOpen = open === i;
            return (
              <li key={q} className={`lx-faq__item${isOpen ? " is-open" : ""}`}>
                <button
                  type="button"
                  className="lx-faq__q"
                  aria-expanded={isOpen}
                  onClick={() => setOpen(isOpen ? null : i)}
                  data-cursor="link"
                >
                  <span>{s(q)}</span>
                  <span className="lx-faq__mark" aria-hidden="true" />
                </button>
                <div className="lx-faq__a">
                  <div>
                    <p>{s(ANSWERS[i])}</p>
                  </div>
                </div>
              </li>
            );
          })}
        </ul>
      </section>

      <section id="close" className="lx-close">
        <p className="lx-close__line">{s("close.line")}</p>
        <div className="lx-close__actions">
          <Link to="/app" className="lx-pillbtn lx-pillbtn--ink lx-pillbtn--xl" data-cursor="cta">
            {s("close.cta")}
          </Link>
          <span className="lx-close__app">
            {s("close.app")}
            <em>{s("close.appNote")}</em>
          </span>
        </div>
        <footer className="lx-footer">
          <span>{s("footer.credit")}</span>
          <span>{s("footer.photos")}</span>
        </footer>
      </section>
    </>
  );
}
