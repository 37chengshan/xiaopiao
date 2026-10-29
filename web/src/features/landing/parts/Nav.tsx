import { Link } from "@tanstack/react-router";
import { motion, useTransform, type MotionValue } from "framer-motion";
import { useEffect, useState } from "react";
import { applyLocale, restoreLocale } from "@/lib/locale";
import { getLocale, useLocale } from "@/lib/strings";
import { s } from "../strings";

/** Hash routing owns the URL fragment, so in-page links scroll instead of setting `#id`. */
export function scrollToId(id: string) {
  document.getElementById(id)?.scrollIntoView({ behavior: "smooth", block: "start" });
}

/**
 * The nav falls in from above while the hero opens and then stays: one transform, no re-render.
 */
export function Nav({ progress }: { progress: MotionValue<number> }) {
  useLocale();
  useEffect(() => {
    restoreLocale();
  }, []);

  const [dark, setDark] = useState(false);

  // The privacy section is the only dark block: flip the pill's material while it sits under the nav.
  useEffect(() => {
    const section = document.getElementById("privacy");
    if (!section) return;
    let frame = 0;
    const check = () => {
      frame = 0;
      const r = section.getBoundingClientRect();
      setDark(r.top < 84 && r.bottom > 56);
    };
    const onScroll = () => {
      if (!frame) frame = requestAnimationFrame(check);
    };
    check();
    window.addEventListener("scroll", onScroll, { passive: true });
    window.addEventListener("resize", onScroll);
    return () => {
      window.removeEventListener("scroll", onScroll);
      window.removeEventListener("resize", onScroll);
      if (frame) cancelAnimationFrame(frame);
    };
  }, []);

  const openY = useTransform(progress, [0.22, 0.5], [-110, 0]);
  const openO = useTransform(progress, [0.2, 0.42], [0, 1]);
  const y = useTransform(openY, (top) => `${top}%`);

  return (
    <motion.nav className={`lx-nav${dark ? " is-dark" : ""}`} aria-label={s("nav.aria")} style={{ y, opacity: openO }}>
      <Link to="/" className="lx-nav__brand">
        <svg width="16" height="20" viewBox="0 0 18 22" aria-hidden="true">
          <path d="M1 1h16v18l-2.7-1.6L11.6 19 9 17.4 6.4 19l-2.7-1.6L1 19z" fill="currentColor" />
          <path d="M5 6h8M5 9.5h8M5 13h5" stroke="var(--lx-paper)" strokeWidth="1.6" strokeLinecap="round" />
        </svg>
        <span>{s("brand")}</span>
      </Link>
      <div className="lx-nav__links">
        <button type="button" className="lx-nav__link" data-cursor="link" onClick={() => scrollToId("launch")}>
          {s("nav.today")}
        </button>
        <button type="button" className="lx-nav__link" data-cursor="link" onClick={() => scrollToId("five")}>
          {s("nav.five")}
        </button>
        <button type="button" className="lx-nav__link" data-cursor="link" onClick={() => scrollToId("shelf")}>
          {s("nav.shelf")}
        </button>
        <button type="button" className="lx-nav__link lx-nav__wide" data-cursor="link" onClick={() => scrollToId("privacy")}>
          {s("nav.privacy")}
        </button>
        <button
          type="button"
          className="lx-nav__link"
          aria-label={s("nav.langLabel")}
          data-cursor="link"
          onClick={() => applyLocale(getLocale() === "zh" ? "en" : "zh")}
        >
          {s("nav.lang")}
        </button>
        <Link to="/app" className="lx-pillbtn lx-pillbtn--ink" data-cursor="cta">
          {s("nav.cta")}
        </Link>
      </div>
    </motion.nav>
  );
}
