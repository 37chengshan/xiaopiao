import { useLocale } from "@/lib/strings";
import { s } from "../strings";
import { BrandMark } from "@/components/brand/BrandMark";

/** Footer: brand + tagline, then one line of links and the generated-image credit. */
export function Footer() {
  useLocale();
  return (
    <footer className="lp-grain relative border-t border-[var(--lp-line)] bg-[var(--lp-paper-2)] pb-10 pt-14">
      <div className="mx-auto w-full max-w-[1320px] px-4 sm:px-6">
        <div className="flex items-center gap-3">
          <BrandMark className="size-7" />
          <span className="text-[13px] font-medium text-[var(--lp-ink-2)]">{s("footer.tagline")}</span>
        </div>
        <div className="mt-8 flex flex-wrap items-center gap-x-6 gap-y-2 border-t border-[var(--lp-line)] pt-5 text-[13px] text-[var(--lp-ink-2)]">
          <a href="#/app" className="hover:text-[var(--lp-ink)]">
            {s("nav.cta")}
          </a>
          <a href="#/app/settings" className="hover:text-[var(--lp-ink)]">
            {s("footer.privacy")}
          </a>
          <span className="text-[var(--lp-ink-3)]">{s("outro.credit")}</span>
          <span className="ml-auto flex items-center gap-3 text-[var(--lp-ink-3)]">
            <span>{s("footer.rights")}</span>
            <span className="rounded-full border border-[var(--lp-line)] px-2 py-[1px] text-[10px] tracking-[0.06em]">build i18n-2</span>
          </span>
        </div>
      </div>
    </footer>
  );
}
