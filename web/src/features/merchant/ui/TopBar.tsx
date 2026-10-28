import { Link } from "@tanstack/react-router";
import { ChevronLeft, Info } from "lucide-react";
import { IconButton } from "@/components/app/kit";
import { copy } from "../copy";
import { DataSheet } from "./DataSheet";

/**
 * Compact merchant top bar (replaces the large title). Left: a clear way back to the consumer app.
 * Centre: shop name. Right: the one 演示数据 chip (merchant data is always synthetic) and the data sheet.
 */
export function TopBar() {
  return (
    <div className="sticky top-0 z-20 flex h-[calc(48px+env(safe-area-inset-top,0px))] items-center gap-2 px-2 pt-[env(safe-area-inset-top,0px)]">
      <div aria-hidden className="material edge-fade-bottom absolute inset-x-0 -bottom-2 top-0" />
      <Link
        to="/me"
        aria-label={copy.backToUserLabel}
        className="relative inline-flex h-11 shrink-0 items-center gap-0.5 rounded-full pl-1 pr-2.5 text-[15px] font-medium text-clay transition-opacity duration-100 active:opacity-50"
      >
        <ChevronLeft className="size-5" strokeWidth={2.25} aria-hidden />
        {copy.backToUser}
      </Link>
      <p className="relative min-w-0 flex-1 truncate text-center text-[15px] font-semibold text-ink">{copy.shopName}</p>
      <DataSheet>
        <IconButton icon={Info} label={copy.dataInfo} className="relative mr-1" />
      </DataSheet>
    </div>
  );
}
