import { useEffect, useRef, useState, type ReactNode } from "react";
import { cn } from "@/lib/utils";
import { DemoPill } from "./DemoPill";

/**
 * Compact top bar for tab roots (2026-10-28: no large titles under a bottom nav).
 * Small left-aligned title, actions and the 演示数据 chip on the right; the bar turns to glass once content scrolls under it.
 * `subtitle` is kept for API compatibility and intentionally not rendered (less text, docs/DESIGN.md round 2).
 */
export function PageHeader({ title, right }: { title: string; subtitle?: string; right?: ReactNode }) {
  const ref = useRef<HTMLDivElement>(null);
  const [scrolled, setScrolled] = useState(false);

  useEffect(() => {
    const main = ref.current?.closest("main");
    if (!main) return;
    const on = () => setScrolled(main.scrollTop > 4);
    on();
    main.addEventListener("scroll", on, { passive: true });
    return () => main.removeEventListener("scroll", on);
  }, []);

  return (
    <div ref={ref} className="sticky top-0 z-20 flex h-[calc(52px+env(safe-area-inset-top,0px))] items-center justify-between gap-3 px-4 pt-[env(safe-area-inset-top,0px)]">
      <div
        aria-hidden
        className={cn("material edge-fade-bottom absolute inset-x-0 -bottom-3 top-0 transition-opacity duration-200 ease-[var(--ease-out)]", scrolled ? "opacity-100" : "opacity-0")}
      />
      <h1 className="relative min-w-0 truncate text-[22px] font-semibold leading-[28px] tracking-[-0.015em] text-ink">{title}</h1>
      <div className="relative flex shrink-0 items-center gap-2">
        <DemoPill />
        {right}
      </div>
    </div>
  );
}
