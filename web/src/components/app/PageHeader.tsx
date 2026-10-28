import { useEffect, useRef, useState, type ReactNode } from "react";
import { cn } from "@/lib/utils";
import { DemoPill } from "./DemoPill";

/**
 * Apple large-title header (docs/DESIGN.md §4): a serif large title in the content, plus a sticky
 * translucent nav row that shows the compact title once the large one scrolls away.
 * Actions (`right`) and the 演示 chip live in the nav row and never move.
 */
export function PageHeader({ title, subtitle, right }: { title: string; subtitle?: string; right?: ReactNode }) {
  const titleRef = useRef<HTMLHeadingElement>(null);
  const [collapsed, setCollapsed] = useState(false);

  useEffect(() => {
    const el = titleRef.current;
    if (!el) return;
    const root = el.closest("main");
    const io = new IntersectionObserver(([e]) => setCollapsed(!e.isIntersecting), {
      root,
      rootMargin: "-44px 0px 0px 0px",
    });
    io.observe(el);
    return () => io.disconnect();
  }, []);

  return (
    <>
      <div
        className={cn(
          "sticky top-0 z-20 flex h-11 items-center justify-end gap-2 px-4 transition-[background-color,box-shadow] duration-200 ease-[var(--ease-out)]",
          collapsed ? "material shadow-[0_1px_0_var(--hairline)]" : "bg-transparent",
        )}
      >
        <span
          aria-hidden={!collapsed}
          className={cn(
            "pointer-events-none absolute inset-x-16 truncate text-center text-[17px] font-semibold text-ink transition-opacity duration-150",
            collapsed ? "opacity-100" : "opacity-0",
          )}
        >
          {title}
        </span>
        <DemoPill />
        {right}
      </div>
      <header className="px-4 pb-3 pt-1">
        <h1 ref={titleRef} className="large-title text-ink">
          {title}
        </h1>
        {subtitle && <p className="mt-1 text-[13px] leading-[18px] text-ink-2">{subtitle}</p>}
      </header>
    </>
  );
}
