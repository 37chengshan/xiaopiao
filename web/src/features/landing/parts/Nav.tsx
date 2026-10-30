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

/** Sections the in-page links point at, in page order; the lens rests on the one under the nav. */
const SECTIONS = ["launch", "five", "shelf", "privacy"] as const;

/**
 * The original glass nav, visible from the opening frame (same brand, links and layout). Liquid Glass motion on
 * top (tt 2026-10-29: 上边栏优化一下): once the page scrolls the bar eases into a compact capsule, and a small
 * glass lens slides under the hovered link — or, at rest, the section you are reading — with a slight
 * overshoot and squash while it travels, the way the selection lens moves in Apple's tab bars.
 */
export function Nav(_props: { progress: MotionValue<number> }) {
  useLocale();
  useEffect(() => {
    restoreLocale();
  }, []);

  const [dark, setDark] = useState(false);
  const [compact, setCompact] = useState(false);
  const [active, setActive] = useState<string | null>(null);
  const [hovered, setHovered] = useState<string | null>(null);
  const [lens, setLens] = useState<{ left: number; width: number } | null>(null);
  const [moving, setMoving] = useState(false);
  const navRef = useRef<HTMLElement>(null);
  const linksRef = useRef<HTMLDivElement>(null);
  const [box, setBox] = useState({ width: 0, height: 0 });

  // The displacement map is built for the bar's own size, so it follows the viewport. The compact transition
  // resizes the bar every frame; rebuilding the map that often is wasted work, so settle first.
  useEffect(() => {
    const el = navRef.current;
    if (!el) return;
    let timer = 0;
    const update = () =>
      setBox((previous) =>
        previous.width === el.offsetWidth && previous.height === el.offsetHeight
          ? previous
          : { width: el.offsetWidth, height: el.offsetHeight },
      );
    const observer = new ResizeObserver(() => {
      window.clearTimeout(timer);
      timer = window.setTimeout(update, 140);
    });
    observer.observe(el);
    update();
    return () => {
      observer.disconnect();
      window.clearTimeout(timer);
    };
  }, []);

  // Compact past the opening; the current section decides where the lens rests.
  useEffect(() => {
    let frame = 0;
    const check = () => {
      frame = 0;
      setCompact(window.scrollY > 120);
      const probe = 96;
      let current: string | null = null;
      for (const id of SECTIONS) {
        const r = document.getElementById(id)?.getBoundingClientRect();
        if (r && r.top <= probe && r.bottom > probe) current = id;
      }
      setActive(current);
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

  // Lens target: hover wins, otherwise the section being read; measured against the links row.
  const target = hovered ?? active;
  useEffect(() => {
    const row = linksRef.current;
    const el = target ? row?.querySelector<HTMLElement>(`[data-section="${target}"]`) : null;
    if (!row || !el || el.offsetParent === null) {
      setLens(null);
      return;
    }
    const next = { left: el.offsetLeft, width: el.offsetWidth };
    setLens((previous) => {
      if (previous && previous.left === next.left && previous.width === next.width) return previous;
      if (previous) {
        setMoving(true);
        window.setTimeout(() => setMoving(false), 220);
      }
      return next;
    });
  }, [target, compact, box.width]);

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
      className={`lx-nav${dark ? " is-dark" : ""}${compact ? " is-compact" : ""}`}
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
      <div className="lx-nav__links" ref={linksRef} onMouseLeave={() => setHovered(null)}>
        <span
          className={`lx-nav__lens${lens ? " is-on" : ""}${moving ? " is-moving" : ""}`}
          aria-hidden="true"
          style={lens ? { transform: `translateX(${lens.left}px)`, width: lens.width } : undefined}
        />
        {(
          [
            ["launch", "nav.today", ""],
            ["five", "nav.five", ""],
            ["shelf", "nav.shelf", ""],
            ["privacy", "nav.privacy", " lx-nav__wide"],
          ] as const
        ).map(([id, key, extra]) => (
          <button
            key={id}
            type="button"
            className={`lx-nav__link${extra}${target === id ? " is-current" : ""}`}
            data-cursor="link"
            data-section={id}
            aria-current={active === id ? "true" : undefined}
            onMouseEnter={() => setHovered(id)}
            onFocus={() => setHovered(id)}
            onBlur={() => setHovered(null)}
            onClick={() => scrollToId(id)}
          >
            {s(key)}
          </button>
        ))}
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
