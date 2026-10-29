import { useState } from "react";
import { s } from "../strings";

const QS = [1, 2, 3, 4, 5, 6] as const;

/** Questions on the left, one answer large on the right; on a phone the answer opens under its question. */
export function Faq() {
  const [sel, setSel] = useState<(typeof QS)[number]>(1);
  return (
    <section className="lp3-faq" id="faq" aria-labelledby="lp3-faq-h">
      <ul className="lp3-faq__list">
        <li>
          <h2 className="lp3-faq__heading" id="lp3-faq-h">
            {s("faq.heading")}
          </h2>
        </li>
        {QS.map((n, i) => (
          <li key={n} data-reveal style={{ ["--i" as string]: i }}>
            <button
              type="button"
              className="lp3-faq__q"
              aria-expanded={sel === n}
              data-cursor="link"
              onClick={() => setSel(n)}
              onPointerEnter={(e) => e.pointerType === "mouse" && setSel(n)}
              onFocus={() => setSel(n)}
            >
              {s(`faq.q${n}` as const)}
            </button>
            {sel === n ? <p className="lp3-faq__inline">{s(`faq.a${n}` as const)}</p> : null}
          </li>
        ))}
      </ul>
      <div className="lp3-faq__pane" aria-live="polite">
        <p key={sel} className="lp3-faq__answer">
          {s(`faq.a${sel}` as const)}
        </p>
      </div>
    </section>
  );
}
