import { Link } from "@tanstack/react-router";
import { motion, type MotionValue } from "framer-motion";
import { useEffect, useRef, useState } from "react";
import { applyLocale, restoreLocale } from "@/lib/locale";
import { getLocale, useLocale } from "@/lib/strings";
import { s } from "../strings";
import { GlassFilter } from "./GlassFilter";

/** Hash routing owns the URL fragment, so in-page links scroll instead of setting `#id`. */
export function scrollToId(id: string) {
  document.getElementById(id)?.scrollIntoView({ behavior: "smooth", block: "start" });
}

/**
 * The original glass nav is visible from the opening frame; its appearance is unchanged.
 */
export function Nav(_props: { progress: MotionValue<number> }) {
  useLocale();
  useEffect(() => {
    restoreLocale();
  }, []);

  const [dark, setDark] = useState(false);
  const navRef = useRef<HTMLElement>(null);
  const [box, setBox] = useState({ width: 0, height: 0 });

  // The displacement map is built for the bar's own size, so it follows the viewport.
  useEffect(() => {
    const el = navRef.current;
    if (!el) return;
    const update = () =>
      setBox((previous) =>
        previous.width === el.offsetWidth && previous.height === el.offsetHeight
          ? previous
          : { width: el.offsetWidth, height: el.offsetHeight },
      );
    const observer = new ResizeObserver(update);
    observer.observe(el);
    update();
    return () => observer.disconnect();
  }, []);

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

  return (
    <motion.nav
      ref={navRef}
      className={`lx-nav${dark ? " is-dark" : ""}`}
      aria-label={s("nav.aria")}
      style={{ "--lx-nav-filter": "url(#lx-nav-glass)" } as React.CSSProperties}
    >
      {box.width > 0 ? (
        <GlassFilter
          id="lx-nav-glass"
          width={box.width}
          height={box.height}
          radius={box.height / 2}
          band={Math.max(14, box.height * 0.42)}
          strength={22}
        />
      ) : null}
      <span className="lx-nav__tint" aria-hidden="true" />
      <span className="lx-nav__specular" aria-hidden="true" />
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
