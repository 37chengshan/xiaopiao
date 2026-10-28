import type { ReactNode } from "react";
import { DemoPill } from "./DemoPill";

export function PageHeader({ title, subtitle, right }: { title: string; subtitle?: string; right?: ReactNode }) {
  return (
    <header className="sticky top-0 z-20 bg-canvas/90 px-4 pb-2 pt-4 backdrop-blur">
      <div className="flex items-center justify-between gap-2">
        <div className="min-w-0">
          <div className="flex items-center gap-2">
            <h1 className="truncate text-[28px] font-extrabold leading-tight tracking-tight text-ink">{title}</h1>
            <DemoPill />
          </div>
          {subtitle && <p className="text-[13px] text-ink-2">{subtitle}</p>}
        </div>
        {right}
      </div>
    </header>
  );
}
