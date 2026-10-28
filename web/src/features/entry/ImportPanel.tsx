import { CircleCheck, FileUp } from "lucide-react";
import { useRef, useState } from "react";
import { toast } from "sonner";
import { Group, Row, XButton } from "@/components/app/kit";
import { useLedger } from "@/services/LedgerProvider";
import { type CsvParseResult, decodeBillBytes, parseBillCsv } from "../../logic/csv";

export function ImportPanel({ onDone }: { onDone: () => void }) {
  const { add } = useLedger();
  const inputRef = useRef<HTMLInputElement>(null);
  const [parsed, setParsed] = useState<{ name: string; result: CsvParseResult } | null>(null);
  const [summary, setSummary] = useState<{ imported: number; duplicates: number; failed: number } | null>(null);
  const [busy, setBusy] = useState(false);

  const handleFile = async (file: File) => {
    try {
      setParsed({ name: file.name, result: parseBillCsv(decodeBillBytes(await file.arrayBuffer())) });
      setSummary(null);
    } catch {
      toast.error("文件读取失败，换一个试试");
    }
  };

  const commit = async () => {
    if (!parsed || busy) return;
    setBusy(true);
    try {
      const r = await add(parsed.result.txns);
      setSummary({ imported: r.added, duplicates: r.duplicates, failed: parsed.result.failed });
    } catch {
      toast.error("导入失败了，再试一次");
    } finally {
      setBusy(false);
    }
  };

  if (summary) {
    return (
      <div className="px-4 pb-[max(1.5rem,env(safe-area-inset-bottom))] pt-6" role="status">
        <CircleCheck className="mx-auto size-12 text-positive" strokeWidth={1.25} aria-hidden />
        <h2 className="mt-3 text-center font-serif text-[22px] font-semibold text-ink">导入完成</h2>
        <Group className="mx-0 mt-5 bg-surface-2">
          <Row title="已导入" trailing={<span className="text-positive">{summary.imported}</span>} />
          <Row title="重复跳过" trailing={<span className="text-ink-2">{summary.duplicates}</span>} />
          <Row title="失败 / 跳过" trailing={<span className={summary.failed ? "text-negative" : "text-ink-2"}>{summary.failed}</span>} />
        </Group>
        <XButton variant="primary" className="mt-5 w-full" onClick={onDone}>
          好的
        </XButton>
      </div>
    );
  }

  return (
    <div className="min-h-0 overflow-y-auto overscroll-contain px-4 pb-[max(1.5rem,env(safe-area-inset-bottom))] pt-4">
      <p className="text-[13px] leading-[19px] text-ink-2">支持支付宝、微信导出的 CSV 账单：自动识别表头、跳过说明行、去重。文件只在你的浏览器里解析。</p>
      <input ref={inputRef} type="file" accept=".csv,text/csv" hidden onChange={(e) => e.target.files?.[0] && handleFile(e.target.files[0])} />
      <button
        type="button"
        onClick={() => inputRef.current?.click()}
        className="mt-3 flex h-24 w-full flex-col items-center justify-center gap-1.5 rounded-[14px] border border-dashed border-ink-3/50 bg-surface-2 text-ink-2 transition-transform duration-100 ease-[var(--ease-out)] active:scale-[0.99]"
      >
        <FileUp className="size-6" strokeWidth={1.5} aria-hidden />
        <span className="text-[14px] font-medium text-ink">选择账单 CSV 文件</span>
      </button>

      {parsed && (
        <div className="mt-3 rounded-[14px] bg-surface-2 p-4" role="status">
          <p className="text-[15px] font-medium text-ink">{parsed.name}</p>
          {parsed.result.provider ? (
            <p className="mt-1 text-[13px] leading-[19px] text-ink-2">
              识别为{parsed.result.provider === "alipay" ? "支付宝" : "微信"}账单：{parsed.result.txns.length} 笔可导入，{parsed.result.failed} 行会被跳过
            </p>
          ) : (
            <p className="mt-1 text-[13px] text-negative">{parsed.result.failures[0]}</p>
          )}
          {parsed.result.failures.length > 0 && parsed.result.provider && (
            <ul className="mt-2 space-y-0.5 text-[12px] text-ink-2">
              {parsed.result.failures.map((f) => (
                <li key={f}>· {f}</li>
              ))}
            </ul>
          )}
          {parsed.result.txns.length > 0 && (
            <XButton variant="primary" className="mt-3 w-full" onClick={commit} disabled={busy}>
              确认导入 {parsed.result.txns.length} 笔
            </XButton>
          )}
        </div>
      )}
    </div>
  );
}
