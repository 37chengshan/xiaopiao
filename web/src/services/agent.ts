import type { Txn } from "@/domain/types";
import { agentUrl } from "./account";
import { parseView, type ViewSpec } from "./views";

export interface AgentEvent { type: "status" | "token" | "view" | "done" | "error"; text?: string; message?: string; view?: ViewSpec }
interface Call { id: string; function: { name: string; arguments: string } }
/** Only whitelisted structured fields leave the device, at the model's explicit request. */
export function executeLocalTool(name: string, args: Record<string, unknown>, txns: Txn[], now = Date.now()) {
  const today = new Date(now); today.setHours(0, 0, 0, 0);
  const start = new Date(today);
  const period = args.period ?? "week";
  if (period === "week") start.setDate(start.getDate() - (start.getDay() + 6) % 7);
  else if (period === "month") start.setDate(1);
  else if (period !== "today") return { error: "INVALID_PERIOD" };
  const end = new Date(today); end.setDate(end.getDate() + 1);
  const rows = txns.filter((t) => t.occurredAt >= +start && t.occurredAt < +end);
  const dimension = args.dimension ?? args.group_by ?? "category";
  if (!["day", "category", "merchant"].includes(String(dimension))) return { error: "INVALID_DIMENSION" };
  const buckets = new Map<string, number>();
  for (const t of rows.filter((t) => t.direction === "OUT")) {
    const d = new Date(t.occurredAt);
    const label = dimension === "day" ? `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}` : dimension === "category" ? t.category : t.merchant.slice(0, 80);
    buckets.set(label, (buckets.get(label) ?? 0) + t.amountCents);
  }
  const ranked = [...buckets].map(([label, cents]) => ({ label, cents })).sort((a,b) => b.cents - a.cents);
  switch (name) {
    case "summarize_period": return { period, unit: "cents", count: rows.length, total_out: rows.filter(t => t.direction === "OUT").reduce((s,t) => s + t.amountCents, 0), total_in: rows.filter(t => t.direction === "IN").reduce((s,t) => s + t.amountCents, 0), buckets: ranked.slice(0, 31) };
    case "find_peak": return { ...(ranked[0] ?? { label: null, cents: null }), unit: "cents" };
    case "query_transactions": {
      const from = typeof args.from === "number" ? args.from : +start, to = typeof args.to === "number" ? args.to : +end;
      if (!Number.isSafeInteger(from) || !Number.isSafeInteger(to) || from >= to) return { error: "INVALID_RANGE" };
      const limit = Math.max(1, Math.min(20, Number(args.limit) || 20));
      const matched = txns.filter(t => t.occurredAt >= from && t.occurredAt < to).sort((a,b) => b.occurredAt - a.occurredAt);
      return { unit: "cents", matched_count: matched.length, truncated: matched.length > limit, rows: matched.slice(0, limit).map(t => ({ id: t.id, cents: t.amountCents, merchant: t.merchant.slice(0, 80), category: t.category, direction: t.direction, occurredAt: t.occurredAt })) };
    }
    case "read_circle": return { shared: false, message: "没有可用的已授权共享数据，不得推断" };
    default: return { error: "TOOL_NOT_ALLOWED" };
  }
}
export async function runAgent(messages: { role: "user" | "assistant"; content: string }[], txns: Txn[], signal: AbortSignal, onEvent: (event: AgentEvent) => void) {
  let payload: object = { messages: messages.slice(-24), timezone: Intl.DateTimeFormat().resolvedOptions().timeZone };
  // A view may only render numbers a data tool already returned in this turn.
  let dataSeen = false;
  for (let round = 0; round < 4; round++) {
    const response = await fetch(agentUrl(), { method: "POST", credentials: "include", headers: { "Content-Type": "application/json" }, body: JSON.stringify(payload), signal });
    if (!response.ok) { const body = await response.json().catch(() => ({})); throw new Error(body.message ?? "AI 暂时无法连接"); }
    if (!response.body) throw new Error("没有收到回复");
    const reader = response.body.getReader();
    const decoder = new TextDecoder();
    let buffer = "", next: object | null = null, done = false;
    try {
      while (true) {
        const chunk = await reader.read();
        if (chunk.done) break;
        buffer += decoder.decode(chunk.value, { stream: true });
        let boundary: RegExpExecArray | null;
        while ((boundary = /\r?\n\r?\n/.exec(buffer))) {
          const frame = buffer.slice(0, boundary.index); buffer = buffer.slice(boundary.index + boundary[0].length);
          const data = frame.split(/\r?\n/).filter(l => l.startsWith("data:")).map(l => l.slice(5).trimStart()).join("\n");
          if (!data) continue;
          const event = JSON.parse(data);
          if (event.type === "error") throw new Error(event.message ?? "回答中断，请重试");
          if (event.type === "tool_calls") {
            const results = (event.calls as Call[]).map(call => {
              if (call.function.name === "show_view") {
                let args: unknown = null;
                try { args = JSON.parse(call.function.arguments || "{}"); } catch { args = null; }
                const spec = parseView(args);
                if (!spec) return { id: call.id, data: { error: "INVALID_VIEW" } };
                if (!dataSeen) return { id: call.id, data: { error: "NO_DATA_FIRST", message: "先调用取数工具" } };
                onEvent({ type: "view", view: spec });
                return { id: call.id, data: { ok: true, rendered: spec.kind } };
              }
              dataSeen = true;
              onEvent({ type: "status", text: ({ summarize_period: "正在汇总账本", find_peak: "正在查找最大支出", query_transactions: "正在查询账本", read_circle: "正在检查共享权限" } as Record<string, string>)[call.function.name] ?? "正在查询" });
              let result;
              try { result = executeLocalTool(call.function.name, JSON.parse(call.function.arguments || "{}"), txns); } catch { result = { error: "INVALID_TOOL_ARGUMENTS" }; }
              return { id: call.id, data: result };
            });
            next = { runId: event.runId, results };
          } else { onEvent(event); if (event.type === "done") done = true; }
        }
        if (buffer.length > 256_000) throw new Error("回复内容过长");
      }
    } finally { await reader.cancel().catch(() => {}); reader.releaseLock(); }
    if (done) return;
    if (!next) throw new Error("连接中断，请重新发送");
    payload = next;
  }
  throw new Error("查询步骤较多，请缩小范围后重试");
}
