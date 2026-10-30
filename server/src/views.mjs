/**
 * Deterministic fallback: when a turn produced real tool numbers but the model answered in prose instead of
 * calling show_view, the server renders the matching view itself. The card carries exactly the numbers the
 * tools returned, so the user still gets a designed view and the model's text stays a caption.
 * Mirrors the catalogue in web `services/views.ts` and Android `ChatViews.kt`.
 */
const KNOWN = new Set(['summarize_period', 'find_peak', 'query_transactions', 'read_circle']);

const asObject = value => (typeof value === 'object' && value !== null && !Array.isArray(value) ? value : null);
const int = value => (Number.isSafeInteger(value) && Math.abs(value) <= 1_000_000_000 ? value : undefined);

function bucketsToRows(buckets) {
  if (!Array.isArray(buckets)) return undefined;
  const rows = buckets
    .slice(0, 40)
    .map(item => {
      const row = asObject(item);
      if (!row) return null;
      const cents = int(row.cents);
      const label = typeof row.label === 'string' ? row.label.slice(0, 60) : '';
      if (cents === undefined && !label) return null;
      return { label, cents };
    })
    .filter(Boolean);
  return rows.length ? rows : undefined;
}

export function viewFromToolResults(messages) {
  const calls = new Map();
  for (const message of messages) {
    if (message.role !== 'assistant' || !Array.isArray(message.tool_calls)) continue;
    for (const call of message.tool_calls) {
      if (!KNOWN.has(call.function?.name)) continue;
      calls.set(call.id, { name: call.function.name, arguments: call.function.arguments });
    }
  }
  // Newest data result wins: it answers what the user just asked.
  for (let i = messages.length - 1; i >= 0; i--) {
    const message = messages[i];
    if (message.role !== 'tool') continue;
    const call = calls.get(message.tool_call_id);
    if (!call) continue;
    let result;
    let args = {};
    try { result = asObject(JSON.parse(message.content)); } catch { continue; }
    if (!result) continue;
    try { args = asObject(JSON.parse(call.arguments || '{}')) ?? {}; } catch { args = {}; }
    const period = ['today', 'week', 'month'].includes(args.period) ? args.period : undefined;
    if (call.name === 'summarize_period') {
      const total = int(result.total_out);
      const count = int(result.count);
      const rows = bucketsToRows(result.buckets);
      if (args.group_by === 'day' && rows) return { kind: 'trend', title: '按天走势', period, points: rows };
      if (args.group_by === 'category' && rows) return { kind: 'category_split', title: '分类占比', period, rows, total_cents: total };
      if (total !== undefined) return { kind: 'spend_summary', title: '支出合计', period, total_cents: total, count };
      return null;
    }
    if (call.name === 'find_peak') {
      const cents = int(result.cents);
      if (cents === undefined || typeof result.label !== 'string') return null;
      return { kind: 'peak', title: '最大的一笔', label: result.label.slice(0, 80), total_cents: cents, dimension: args.dimension };
    }
    if (call.name === 'query_transactions') {
      const rows = Array.isArray(result.rows)
        ? result.rows.slice(0, 20).map(row => {
          const item = asObject(row);
          if (!item) return null;
          const cents = int(item.cents);
          const merchant = typeof item.merchant === 'string' ? item.merchant.slice(0, 80) : '';
          if (cents === undefined && !merchant) return null;
          return { merchant, cents, category: typeof item.category === 'string' ? item.category.slice(0, 40) : undefined };
        }).filter(Boolean)
        : [];
      return rows.length ? { kind: 'entries', title: '最近明细', rows } : null;
    }
    return null;
  }
  return null;
}
