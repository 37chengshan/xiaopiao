import { useLocale } from "@/lib/strings";
import { landingStrings } from "../strings";

export function Footer() {
  useLocale();
  const s = landingStrings;

  return (
    <footer className="border-t border-d-hairline bg-d-canvas text-d-ink">
      <div className="mx-auto flex max-w-[1200px] flex-col gap-3 px-5 py-10 text-[12px] text-d-ink-3 sm:flex-row sm:items-center sm:justify-between lg:px-10">
        <span>{s("foot.copy")}</span>
        <div className="flex items-center gap-5">
          <a href="#privacy" className="transition-colors hover:text-d-ink" data-cursor="link">
            {s("foot.privacy")}
          </a>
        </div>
      </div>
    </footer>
  );
}
