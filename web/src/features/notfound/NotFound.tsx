import { useState } from "react";
import { BrandMark } from "@/components/brand/BrandMark";
import { useLocale } from "@/lib/strings";
import "@/lib/locale";
import "./notfound.css";
import { s } from "./strings";

/**
 * The 404 screen (tt 2026-10-28). Full-bleed night photo of a thermal printer eating a crumpled
 * receipt, the brand mark centred at the top, and one very large Geist Mono 404 over it.
 *
 * The photo is a plain `<img>` rather than a CSS background so a missing file degrades to the
 * radial-gradient placeholder in `.nf-bg` instead of an empty black page — the QA harness swaps the
 * image without touching this component.
 */
export function NotFound({ heading = "404" }: { heading?: string }) {
  useLocale();
  const [missing, setMissing] = useState(false);

  return (
    <main className="nf">
      <div className="nf-bg" aria-hidden="true">
        {!missing && (
          <img
            src="/img/notfound-jam.webp"
            alt=""
            decoding="async"
            fetchPriority="high"
            onError={() => setMissing(true)}
          />
        )}
      </div>

      <header className="nf-brand">
        <BrandMark />
        <span className="nf-wordmark">{s("nf.brand")}</span>
      </header>

      <div className="nf-stage">
        <h1 className="nf-num" aria-label={`${heading} · ${s("nf.label")}`}>
          {heading}
        </h1>
        <div className="nf-rule" aria-hidden="true" />
        <div className="nf-copy">
          <p className="nf-lead">{s("nf.lead")}</p>
          <p className="nf-body">{s("nf.body")}</p>
          <nav className="nf-links" aria-label={s("nf.label")}>
            <a className="nf-link" href="#/">
              {s("nf.home")}
              <span aria-hidden="true">→</span>
            </a>
            <a className="nf-link" href="#/app">
              {s("nf.app")}
              <span aria-hidden="true">→</span>
            </a>
          </nav>
        </div>
      </div>
    </main>
  );
}
