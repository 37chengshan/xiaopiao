import { useLedger } from "@/services/LedgerProvider";

/** The single, quiet demo-data marker for a page (docs/DESIGN.md §1.4). Don't repeat it inside cards. */
export function DemoPill() {
  const { mode } = useLedger();
  if (mode !== "demo") return null;
  return (
    <span
      className="inline-flex h-6 items-center rounded-full bg-fill px-2.5 text-[11px] font-medium tracking-[0.02em] text-ink-2"
      title="当前展示的是虚构的演示数据"
    >
      演示数据
    </span>
  );
}
