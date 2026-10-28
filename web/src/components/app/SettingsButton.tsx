import { Link } from "@tanstack/react-router";
import { Settings } from "lucide-react";
import { t } from "@/lib/strings";

/** Top-right gear on every tab root; opens the standalone settings page (docs/APP-DESIGN.md §2). */
export function SettingsButton() {
  return (
    <Link
      to="/settings"
      aria-label={t("nav.settings")}
      className="press relative grid size-8 shrink-0 place-items-center rounded-full bg-fill text-ink before:absolute before:-inset-1.5 before:content-['']"
    >
      <Settings className="size-[17px]" strokeWidth={1.9} aria-hidden />
    </Link>
  );
}
