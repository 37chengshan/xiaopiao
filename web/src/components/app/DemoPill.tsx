import { useLedger } from "@/services/LedgerProvider";

export function DemoPill() {
  const { mode } = useLedger();
  if (mode !== "demo") return null;
  return (
    <span className="inline-flex items-center rounded-full bg-coral-soft px-2.5 py-0.5 text-[11px] font-semibold text-coral" title="当前展示的是虚构的演示数据">
      演示数据
    </span>
  );
}
