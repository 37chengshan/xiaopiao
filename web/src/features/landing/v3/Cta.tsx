import { Link } from "@tanstack/react-router";
import { s } from "../strings";
import { scrollToId } from "./Nav";

/** The whole band is the link: one honest verb. */
export function Cta() {
  return (
    <Link to="/app" className="lp3-cta" data-cursor="cta">
      <span className="lp3-cta__line">{s("cta.line")}</span>
      <svg className="lp3-cta__arrow" viewBox="0 0 48 48" aria-hidden="true">
        <path d="M12 36 36 12M16 12h20v20" fill="none" stroke="currentColor" strokeWidth="3.5" strokeLinecap="square" />
      </svg>
      <span className="lp3-cta__word">{s("cta.word")}</span>
    </Link>
  );
}

export function Foot() {
  return (
    <footer className="lp3-foot">
      <span>{s("footer.what")}</span>
      <span className="lp3-foot__links">
        <Link to="/app">{s("footer.app")}</Link>
        <button type="button" onClick={() => scrollToId("how")}>
          {s("footer.how")}
        </button>
        <button type="button" onClick={() => scrollToId("faq")}>
          {s("footer.faq")}
        </button>
      </span>
      <span className="lp3-foot__right">{s("footer.credit")}</span>
    </footer>
  );
}
