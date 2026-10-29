import { motion } from "framer-motion";
import { useEffect, useState } from "react";
import { setLocale, useLocale } from "@/lib/strings";
import { Pill } from "../components/Pill";
import { landingToast } from "../components/LandingToast";
import { landingStrings } from "../strings";
import { BrandMark } from "@/components/brand/BrandMark";

/**
 * Floating capsule nav (§L.2). Hides on scroll down, returns on scroll up — a 200ms y translation.
 * "Sign in" is a placeholder: it shows a toast and never fakes an account.
 */
export function TopNav() {
  const locale = useLocale();
  const s = landingStrings;
  const [hidden, setHidden] = useState(false);

  useEffect(() => {
    let last = window.scrollY;
    const onScroll = () => {
      const y = window.scrollY;
      if (y < 96) setHidden(false);
      else if (y > last + 6) setHidden(true);
      else if (y < last - 6) setHidden(false);
      last = y;
    };
    window.addEventListener("scroll", onScroll, { passive: true });
    return () => window.removeEventListener("scroll", onScroll);
  }, []);

  const links = [
    { href: "#features", label: s("nav.features") },
    { href: "#merchant", label: s("nav.merchant") },
    { href: "#privacy", label: s("nav.privacy") },
  ];

  return (
    <div className="fixed inset-x-0 top-3 z-50 flex justify-center px-4">
      <motion.nav
        aria-label={s("nav.aria")}
        animate={{ y: hidden ? -76 : 0 }}
        transition={{ duration: 0.2, ease: [0.23, 1, 0.32, 1] }}
        className="flex h-[52px] w-full max-w-[1000px] items-center gap-1 rounded-full border border-d-hairline bg-d-canvas/80 pr-2 pl-4 backdrop-blur-xl"
      >
        <a href="#top" className="flex items-center gap-2 text-d-ink" data-cursor="link">
          <BrandMark className="size-[22px]" />
          <span className="text-[14px] font-semibold tracking-[-0.01em]">{s("nav.brand")}</span>
        </a>

        <div className="mx-2 hidden items-center gap-1 lg:flex">
          {links.map((link) => (
            <a
              key={link.href}
              href={link.href}
              className="rounded-full px-3 py-1.5 text-[13px] font-medium text-d-ink-2 transition-colors hover:text-d-ink"
              data-cursor="link"
            >
              {link.label}
            </a>
          ))}
        </div>

        <div className="ml-auto flex items-center gap-1.5">
          <button
            type="button"
            onClick={() => setLocale(locale === "zh" ? "en" : "zh")}
            aria-label={s("nav.lang")}
            className="h-9 rounded-full px-3 text-[13px] font-medium text-d-ink-2 transition-colors hover:bg-d-fill hover:text-d-ink"
            data-cursor="link"
          >
            {locale === "zh" ? "EN" : "中文"}
          </button>
          <Pill
            variant="grey"
            size="sm"
            onClick={() => landingToast(s("soon.login"))}
            className="hidden sm:inline-flex"
          >
            {s("nav.login")}
          </Pill>
          <Pill href="#/app" size="sm" cursorLabel={s("hero.cursor")}>
            {s("nav.cta")}
          </Pill>
        </div>
      </motion.nav>
    </div>
  );
}
