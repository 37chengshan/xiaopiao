import { useEffect, useRef } from "react";
import { useLocale } from "@/lib/strings";
import "./landing.css";
import { CursorHalo } from "./cursor/CursorHalo";
import { useHaloEnabled } from "./hooks/useMediaQuery";
import { Band } from "./v3/Band";
import { Cta, Foot } from "./v3/Cta";
import { Day } from "./v3/Day";
import { Faq } from "./v3/Faq";
import { Features } from "./v3/Features";
import { Hero } from "./v3/Hero";
import { Nav } from "./v3/Nav";
import { Stamp } from "./v3/Stamp";
import { Verbs } from "./v3/Verbs";

/**
 * Landing v3 (tt 2026-10-29, 暖纸·杂志感; taste-skill "pinned poster"): today's receipt holds still while the
 * day passes under it. Order follows the argument in .taste-skill/notes.md.
 */
export function LandingPage() {
  useLocale();
  const halo = useHaloEnabled();
  const root = useRef<HTMLDivElement>(null);

  // Entrances: parts below the fold are lifted once as they arrive; everything is visible without this.
  useEffect(() => {
    const el = root.current;
    if (!el) return;
    const items = Array.from(el.querySelectorAll<HTMLElement>("[data-reveal]"));
    const below = items.filter((n) => n.getBoundingClientRect().top > window.innerHeight * 0.9);
    for (const n of below) n.classList.add("is-pending");
    const io = new IntersectionObserver(
      (entries) => {
        for (const e of entries) {
          if (!e.isIntersecting) continue;
          e.target.classList.remove("is-pending");
          io.unobserve(e.target);
        }
      },
      { rootMargin: "0px 0px -12% 0px" },
    );
    for (const n of below) io.observe(n);
    return () => io.disconnect();
  }, []);

  return (
    <div ref={root} className="lp3 min-h-svh overflow-x-clip">
      <Nav />
      <main>
        <Hero />
        <Day />
        <Stamp />
        <Features />
        <Verbs />
        <Band />
        <Faq />
        <Cta />
      </main>
      <Foot />
      {halo ? <CursorHalo /> : null}
    </div>
  );
}
