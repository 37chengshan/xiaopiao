import { FileUp } from "lucide-react";
import { useRef, useState } from "react";
import { toast } from "sonner";
import { useLedger } from "@/services/LedgerProvider";
import { type CsvParseResult, decodeBillBytes, parseBillCsv } from "../../logic/csv";
import { addDays } from "../../logic/time";
import { startOfDay } from "../../domain/types";

function sampleCsv(now: number): string {
  const d = (back: number, hm: string) => {
    const t = new Date(addDays(startOfDay(now), -back));
    return `${t.getFullYear()}-${`${t.getMonth() + 1}`.padStart(2, "0")}-${`${t.getDate()}`.padStart(2, "0")} ${hm}`;
  };
  return `导出信息：
示例账单（虚构数据，仅用于体验导入）
------------------------------------------------------------------------------------
交易时间,交易分类,交易对方,对方账号,商品说明,收/支,金额,收/付款方式,交易状态,交易订单号,商家订单号,备注,
${d(1, "12:21:08")},餐饮美食,美团外卖,***,黄焖鸡米饭,支出,28.50,花呗,交易成功,SAMPLE0001,S1,,
${d(1, "15:40:12")},餐饮美食,喜茶,***,多肉葡萄,支出,19.00,余额,交易成功,SAMPLE0002,S2,,
${d(2, "08:12:30")},交通出行,上海地铁,***,乘车码,支出,4.00,余额,交易成功,SAMPLE0003,S3,,
${d(3, "20:01:00")},日用百货,盒马,***,生鲜,支出,67.30,花呗,交易成功,SAMPLE0004,S4,,
${d(3, "20:30:00")},其他,某某,***,转账,不计收支,10.00,余额,交易成功,SAMPLE0005,S5,,
${d(4, "09:00:00")},餐饮美食,示例餐厅,***,订单,支出,abc,余额,交易成功,SAMPLE0006,S6,,
`;
}

export function ImportPanel({ onDone }: { onDone: () => void }) {
  const { add, now } = useLedger();
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
      <div className="px-4 pb-6 pt-5 text-center" role="status">
        <div className="text-5xl" aria-hidden>
          📥
        </div>
        <h2 className="mt-2 text-xl font-extrabold text-ink">导入完成</h2>
        <div className="mt-4 grid grid-cols-3 gap-2">
          {[
            ["导入", summary.imported, "text-good"],
            ["重复跳过", summary.duplicates, "text-ink-2"],
            ["失败/跳过", summary.failed, "text-stamp"],
          ].map(([label, n, cls]) => (
            <div key={label as string} className="rounded-2xl bg-card py-3">
              <div className={`text-2xl font-extrabold ${cls}`}>{n}</div>
              <div className="text-xs text-ink-2">{label}</div>
            </div>
          ))}
        </div>
        <button onClick={onDone} className="mt-5 h-12 w-full rounded-2xl bg-coral font-bold text-white">
          好的
        </button>
      </div>
    );
  }

  return (
    <div className="px-4 pb-[max(1.5rem,env(safe-area-inset-bottom))] pt-4">
      <p className="text-sm text-ink-2">支持支付宝、微信导出的 CSV 账单（自动识别表头、跳过说明行、去重）。文件只在你的浏览器里解析。</p>
      <input ref={inputRef} type="file" accept=".csv,text/csv" hidden onChange={(e) => e.target.files?.[0] && handleFile(e.target.files[0])} />
      <button onClick={() => inputRef.current?.click()} className="mt-3 flex h-24 w-full flex-col items-center justify-center gap-1 rounded-2xl border-2 border-dashed border-hairline bg-card text-ink-2 transition hover:border-coral">
        <FileUp className="size-6" aria-hidden />
        <span className="text-sm font-semibold">选择账单 CSV 文件</span>
      </button>
      <button
        onClick={() => {
          setParsed({ name: "示例账单.csv", result: parseBillCsv(sampleCsv(now)) });
          setSummary(null);
        }}
        className="mt-2 w-full text-center text-[13px] font-semibold text-coral"
      >
        没有账单？用示例账单试试
      </button>

      {parsed && (
        <div className="mt-4 rounded-2xl bg-card p-4" role="status">
          <p className="text-sm font-semibold text-ink">{parsed.name}</p>
          {parsed.result.provider ? (
            <p className="mt-1 text-[13px] text-ink-2">
              识别为{parsed.result.provider === "alipay" ? "支付宝" : "微信"}账单：{parsed.result.txns.length} 笔可导入，{parsed.result.failed} 行会被跳过
            </p>
          ) : (
            <p className="mt-1 text-[13px] text-stamp">{parsed.result.failures[0]}</p>
          )}
          {parsed.result.failures.length > 0 && parsed.result.provider && (
            <ul className="mt-2 space-y-0.5 text-[12px] text-ink-2">
              {parsed.result.failures.map((f) => (
                <li key={f}>· {f}</li>
              ))}
            </ul>
          )}
          {parsed.result.txns.length > 0 && (
            <button onClick={commit} disabled={busy} className="mt-3 h-11 w-full rounded-2xl bg-coral font-bold text-white disabled:opacity-50">
              确认导入 {parsed.result.txns.length} 笔
            </button>
          )}
        </div>
      )}
    </div>
  );
}
