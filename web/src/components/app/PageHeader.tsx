import { useEffect, useRef, useState, type ReactNode } from "react";
import { cn } from "@/lib/utils";
import { DemoPill } from "./DemoPill";

/**
 * Apple large-title header (docs/DESIGN.md §4): a serif large title in the content, plus a sticky
 * translucent nav row that shows the compact title once the large one scrolls away. Content scrolls
 * under the nav; its lower edge fades out instead of ending in a hairline.
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
      <div className="sticky top-0 z-20 flex h-[calc(44px+env(safe-area-inset-top,0px))] items-center justify-end gap-2 px-4 pt-[env(safe-area-inset-top,0px)]">
        <div
          aria-hidden
          className={cn(
            "material edge-fade-bottom absolute inset-x-0 -bottom-3 top-0 transition-opacity duration-200 ease-[var(--ease-out)]",
            collapsed ? "opacity-100" : "opacity-0",
          )}
        />
        <span
          aria-hidden={!collapsed}
          className={cn(
            "pointer-events-none absolute inset-x-20 bottom-0 flex h-11 items-center justify-center truncate text-[17px] font-semibold text-ink transition-opacity duration-150 ease-[var(--ease-out)]",
            collapsed ? "opacity-100" : "opacity-0",
          )}
        >
          {title}
        </span>
        <div className="relative flex items-center gap-2">
          <DemoPill />
          {right}
        </div>
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
