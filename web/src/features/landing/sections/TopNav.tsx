import { useEffect, useRef, useState, type CSSProperties, type RefObject } from "react";
import { getLocale, useLocale } from "@/lib/strings";
import { applyLocale, restoreLocale } from "@/lib/locale";
import { BrandMark } from "@/components/brand/BrandMark";
import { cn } from "@/lib/utils";
import { s } from "../strings";

const LINKS = [
  { id: "demo", key: "nav.demo" },
  { id: "archive", key: "nav.archive" },
  { id: "features", key: "nav.features" },
  { id: "privacy", key: "nav.privacy" },
] as const;

/** Fixed nav that drops in as part of the hero timeline; hash routing means anchors are onClick. */
export function TopNav({ navRef, style }: { navRef?: RefObject<HTMLElement | null>; style?: CSSProperties }) {
  useLocale();
  const [open, setOpen] = useState(false);
  const localRef = useRef<HTMLElement | null>(null);

  useEffect(() => {
    restoreLocale();
  }, []);

  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => e.key === "Escape" && setOpen(false);
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [open]);

  const jump = (id: string) => {
    setOpen(false);
    document.getElementById(id)?.scrollIntoView({ behavior: "smooth", block: "start" });
  };

  return (
    <header
      ref={(node) => {
        localRef.current = node;
        if (navRef) navRef.current = node;
      }}
      className="fixed inset-x-0 top-0 z-40"
      style={style}
    >
      <div className="mx-auto flex w-full max-w-[1320px] items-center gap-3 px-4 py-3 sm:px-6">
        <div className="flex w-full items-center gap-3 rounded-full border border-[var(--lp-line)] bg-[rgba(246,240,231,0.82)] px-3 py-2 shadow-[0_10px_30px_-18px_rgba(27,23,18,0.5)] backdrop-blur-md">
        <a
          href="#/"
          onClick={(e) => {
            e.preventDefault();
            window.scrollTo({ top: 0, behavior: "smooth" });
          }}
          className="flex items-center gap-2 text-[15px] font-semibold tracking-[-0.01em]"
          aria-label={s("brand")}
        >
          <BrandMark className="size-6" />
          <span className="hidden sm:inline">{s("brand")}</span>
        </a>

        <nav aria-label={s("nav.aria")} className="ml-4 hidden items-center gap-1 md:flex">
          {LINKS.map((link) => (
            <button
              key={link.id}
              type="button"
              onClick={() => jump(link.id)}
              className="rounded-full px-3 py-1.5 text-[14px] text-[var(--lp-ink-2)] transition-colors hover:bg-[rgba(27,23,18,0.06)] hover:text-[var(--lp-ink)]"
            >
              {s(link.key)}
            </button>
          ))}
        </nav>

        <div className="ml-auto flex items-center gap-2">
          <button
            type="button"
            onClick={() => applyLocale(getLocale() === "zh" ? "en" : "zh")}
            className="rounded-full border border-[var(--lp-line)] px-2.5 py-1.5 text-[13px] font-medium text-[var(--lp-ink)] transition-colors hover:bg-[rgba(27,23,18,0.06)]"
            aria-label={s("nav.lang")}
          >
            {getLocale() === "zh" ? "EN" : "中"}
          </button>
          <a
            href="#/login"
            className="hidden h-9 items-center rounded-full px-3 text-[13px] font-medium text-[var(--lp-ink-2)] transition-colors hover:bg-[rgba(27,23,18,0.06)] hover:text-[var(--lp-ink)] sm:inline-flex"
          >
            {s("nav.login")}
          </a>
          <a
            href="#/app"
            className="inline-flex h-9 items-center rounded-full bg-[var(--lp-ink)] px-4 text-[13px] font-medium text-[var(--lp-paper)] transition-transform duration-200 hover:-translate-y-px active:scale-[0.98]"
          >
            {s("nav.cta")}
          </a>
          <button
            type="button"
            aria-label={open ? s("nav.close") : s("nav.menu")}
            aria-expanded={open}
            onClick={() => setOpen((v) => !v)}
            className="grid size-9 place-items-center rounded-full border border-[var(--lp-line)] md:hidden"
          >
            <span className="relative block h-3 w-4">
              <span className={cn("absolute left-0 h-[1.5px] w-4 bg-current transition-transform duration-300", open ? "top-[5px] rotate-45" : "top-0")} />
              <span className={cn("absolute left-0 top-[5px] h-[1.5px] w-4 bg-current transition-opacity duration-200", open && "opacity-0")} />
              <span className={cn("absolute left-0 top-[10px] h-[1.5px] w-4 bg-current transition-transform duration-300", open ? "top-[5px] -rotate-45" : "")} />
            </span>
          </button>
        </div>
        </div>
      </div>

      {open && (
        <div className="mx-4 mb-2 rounded-2xl border border-[var(--lp-line)] bg-[var(--lp-card)] p-2 shadow-[0_20px_50px_-24px_rgba(27,23,18,0.5)] md:hidden">
          {LINKS.map((link) => (
            <button
              key={link.id}
              type="button"
              onClick={() => jump(link.id)}
              className="block w-full rounded-xl px-3 py-2.5 text-left text-[15px] text-[var(--lp-ink)] hover:bg-[rgba(27,23,18,0.05)]"
            >
              {s(link.key)}
            </button>
          ))}
          <a href="#/login" className="block rounded-xl px-3 py-2.5 text-left text-[15px] text-[var(--lp-ink)] hover:bg-[rgba(27,23,18,0.05)]">{s("nav.login")}</a>
        </div>
      )}
    </header>
  );
}
