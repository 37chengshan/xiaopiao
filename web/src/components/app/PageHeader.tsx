import { useEffect, useRef, useState, type ReactNode } from "react";
import { useRouter } from "@tanstack/react-router";
import { ChevronLeft } from "lucide-react";
import { t } from "@/lib/strings";
import { cn } from "@/lib/utils";
import { IconButton } from "./kit";
import { SettingsButton } from "./SettingsButton";

/**
 * Compact top bar for tab roots (2026-10-28: no large titles under a bottom nav).
 * Small left-aligned title and actions on the right, always ending in the settings gear (or a back button when `back`);
 * the bar turns to glass once content scrolls under it.
 * `subtitle` is kept for API compatibility and intentionally not rendered (less text, docs/DESIGN.md round 2).
 */
export function PageHeader({ title, right, back = false }: { title: string; subtitle?: string; right?: ReactNode; back?: boolean }) {
  const router = useRouter();
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
      {back && (
        <IconButton
          icon={ChevronLeft}
          label={t("nav.back")}
          className="relative -ml-1"
          onClick={() => (router.history.length > 1 ? router.history.back() : void router.navigate({ to: "/ledger" }))}
        />
      )}
      <h1 className="relative min-w-0 flex-1 truncate text-[22px] font-semibold leading-[28px] tracking-[-0.015em] text-ink">{title}</h1>
      <div className="relative flex shrink-0 items-center gap-2">
        {right}
        {!back && <SettingsButton />}
      </div>
    </div>
  );
}
