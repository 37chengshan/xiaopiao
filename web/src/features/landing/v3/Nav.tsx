import { Link } from "@tanstack/react-router";
import { useEffect } from "react";
import { applyLocale, restoreLocale } from "@/lib/locale";
import { getLocale, useLocale } from "@/lib/strings";
import { s } from "../strings";

/** Hash routing owns the URL fragment, so in-page links scroll instead of setting `#id`. */
export function scrollToId(id: string) {
  document.getElementById(id)?.scrollIntoView({ behavior: "smooth", block: "start" });
}

export function Nav() {
  useLocale();
  useEffect(() => {
    restoreLocale();
  }, []);

  return (
    <nav className="lp3-nav" aria-label={s("nav.aria")}>
      <Link to="/" className="lp3-nav__brand" data-cursor="link">
        <svg width="18" height="22" viewBox="0 0 18 22" aria-hidden="true">
          <path d="M1 1h16v18l-2.7-1.6L11.6 19 9 17.4 6.4 19l-2.7-1.6L1 19z" fill="currentColor" />
          <path d="M5 6h8M5 9.5h8M5 13h5" stroke="#f2f1ee" strokeWidth="1.6" strokeLinecap="round" />
        </svg>
        {s("brand")}
      </Link>
      <div className="lp3-nav__links">
        <button type="button" className="lp3-nav__link lp3-nav__hide-sm" data-cursor="link" onClick={() => scrollToId("how")}>
          {s("nav.how")}
        </button>
        <button type="button" className="lp3-nav__link lp3-nav__hide-sm" data-cursor="link" onClick={() => scrollToId("features")}>
          {s("nav.features")}
        </button>
        <button type="button" className="lp3-nav__link lp3-nav__hide-sm" data-cursor="link" onClick={() => scrollToId("faq")}>
          {s("nav.faq")}
        </button>
        <button
          type="button"
          className="lp3-nav__link"
          aria-label={s("nav.langLabel")}
          data-cursor="link"
          onClick={() => applyLocale(getLocale() === "zh" ? "en" : "zh")}
        >
          {s("nav.lang")}
        </button>
        <Link to="/app" className="lp3-btn" data-cursor="cta">
          {s("nav.cta")}
        </Link>
      </div>
    </nav>
  );
}
