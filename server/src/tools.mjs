/**
 * The assistant's tool catalogue, in one place so the prompt, the agent loop and the tests cannot drift apart.
 *
 * Three kinds of tool, and only these three:
 * - data tools: read-only queries the *device* answers from the local ledger, budget, subscription scan and life
 *   log. The server only declares them; every number the model may speak comes from these results.
 * - `show_view`: presentation only, the numbers must already have come from a data tool.
 * - `draft`: an action proposal (record / budget / reminder) that is validated here and pushed to the client as a
 *   card. The server never writes a ledger row; the person confirms on the device and the existing
 *   `POST /v1/ledger/push` does the write.
 *
 * Not reachable on purpose: card wallet / statement text / note bodies. `query_transactions` and `read_life_logs`
 * say so in their descriptions, and the ledger sync route refuses raw text separately.
 */

const enumString = values => ({ type: 'string', enum: values });
const period = enumString(['today', 'week', 'month']);
const schema = properties => ({ type: 'object', properties, additionalProperties: false });
const int = (minimum, maximum) => ({ type: 'integer', minimum, maximum });
const text = maximum => ({ type: 'string', maxLength: maximum });

/** epoch-ms bounds shared with the ledger sync route so a garbage timestamp cannot pin a query forever. */
export const TIME_MIN = 1;
export const TIME_MAX = 4_102_444_800_000;

const definition = (name, description, parameters) => ({ type: 'function', function: { name, description, parameters } });

/** Short, human step labels. The client shows these verbatim, so they are product copy, not debug output. */
export const TOOL_LABELS = Object.freeze({
  summarize_period: '正在汇总账本',
  find_peak: '正在查找最大支出',
  query_transactions: '正在查询明细',
  read_circle: '正在检查共享权限',
  read_budget: '正在查预算',
  summarize_range: '正在对比区间支出',
  read_subscriptions: '正在找规律扣款',
  read_weekly_report: '正在看周报和头衔',
  read_life_logs: '正在汇总生活记录',
  list_categories: '正在读取分类',
  show_view: '正在生成视图',
  draft: '正在起草',
  plan_steps: '正在拆步骤',
});

/** Read-only queries. A tool result from any of these licenses numbers in the answer. */
export const DATA_TOOLS = Object.freeze([
  'summarize_period', 'find_peak', 'query_transactions', 'read_circle',
  'read_budget', 'summarize_range', 'read_subscriptions', 'read_weekly_report', 'read_life_logs', 'list_categories',
]);

/** Server-side tools: never forwarded to the client, so an old client needs no change to keep working. */
export const SERVER_TOOLS = Object.freeze(['plan_steps']);

export const DRAFT_KINDS = Object.freeze(['record', 'budget', 'reminder']);

export const toolDefinitions = [
  definition('summarize_period', '汇总本机账本一个周期的总额、笔数与分组；只返回统计结果，不上传账单原文。金额单位是分（cents）。',
    schema({ period, group_by: enumString(['day', 'category']) })),
  definition('find_peak', '查本机账本中某周期最大的一天/一类/一家商户。金额单位是分（cents）。',
    schema({ period, dimension: enumString(['day', 'category', 'merchant']) })),
  definition('query_transactions',
    '按时间查询本机结构化记录，最多 20 条。返回金额（分）、商户、分类、方向、时间；不读取备注原文、卡包或账单截图。',
    schema({ from: int(TIME_MIN, TIME_MAX), to: int(TIME_MIN, TIME_MAX), limit: int(1, 20) })),
  definition('read_circle', '仅查询明确授权共享的圈子数据；没有授权返回未共享。金额单位是分（cents）。',
    schema({ period, scope: enumString(['me', 'family']) })),
  definition('read_budget',
    '读取本机预算：各周期的总额度、已用、剩余，以及每个分类的月度额度、已用、剩余。金额单位是分（cents）。没有设预算时返回 configured=false。不给建议、不评判。',
    schema({ period: enumString(['day', 'week', 'month', 'all']) })),
  definition('summarize_range',
    '汇总任意起止时间（epoch 毫秒，左闭右开）的收支，按天/周/月/分类分组。金额单位是分（cents）。用于「上个月」「最近 30 天」「和上上周比」。',
    schema({
      from: int(TIME_MIN, TIME_MAX), to: int(TIME_MIN, TIME_MAX),
      group_by: enumString(['day', 'week', 'month', 'category']),
    })),
  definition('read_subscriptions',
    '读取本机订阅侦探的结果：规律扣款的商户、金额（分）、间隔天数、最近一次、下次预计、出现次数。只用来判断有没有订阅，取消路径由用户自己操作。',
    schema({ limit: int(1, 20) })),
  definition('read_weekly_report',
    '读取本机已生成的周报与头衔：周区间标签、支出合计（分）、笔数、与上周比较的百分比、前三分类、最大一笔、获得的头衔。',
    schema({ week_offset: int(0, 12) })),
  definition('read_life_logs',
    '汇总本机生活记录（吃/练/动）的每日热量与次数：摄入 kcal、消耗 kcal、净值、按天或按类型分组。不返回备注原文、不返回单条食物的明细。',
    schema({
      from: int(TIME_MIN, TIME_MAX), to: int(TIME_MIN, TIME_MAX),
      kind: enumString(['eat', 'exercise', 'move', 'all']), group_by: enumString(['day', 'kind']),
    })),
  definition('list_categories', '列出本机账本的分类（id、名称、收支方向）。用于把用户说的「吃饭」对应到实际分类；不返回金额。', schema({})),
];

/** Presentation, not data: the numbers must already be in a data tool result from this turn. */
const viewKinds = ['spend_summary', 'category_split', 'trend', 'entries', 'peak', 'weekly_report', 'subscriptions', 'draft_list'];
const viewRow = {
  type: 'object',
  properties: {
    label: { type: 'string' }, merchant: { type: 'string' }, category: { type: 'string' },
    cents: { type: 'integer' }, occurred_at: { type: 'integer' }, cadence: { type: 'string' },
  },
  additionalProperties: false,
};
export const VIEW_KINDS = Object.freeze(viewKinds);
toolDefinitions.push(definition('show_view',
  '用取数工具返回的数字渲染一个界面视图。先取数，再调用一次；数字必须与工具结果一致，单位是分。',
  schema({
    kind: enumString(viewKinds),
    period: enumString(['today', 'week', 'month']),
    title: { type: 'string' },
    total_cents: { type: 'integer' },
    count: { type: 'integer' },
    rows: { type: 'array', items: viewRow },
    points: { type: 'array', items: viewRow },
    label: { type: 'string' },
    dimension: enumString(['day', 'category', 'merchant']),
    week_label: { type: 'string' },
    top_category: { type: 'string' },
  })));

/** A plan is a promise about next steps, not an action: the server consumes it and emits `plan`. */
toolDefinitions.push(definition('plan_steps',
  '开始一次需要多步查询的提问时，先写一个简短计划（1–6 步），每步说明要调哪个取数工具、给用户看的一句话（例如「正在查预算」）。调用后系统会替你记下并继续。',
  schema({
    steps: {
      type: 'array', minItems: 1, maxItems: 6,
      items: {
        type: 'object',
        properties: { tool: enumString(DATA_TOOLS), label: text(24) },
        required: ['tool', 'label'],
        additionalProperties: false,
      },
    },
  })));

/** The action proposal. Fields are a strict whitelist: an unknown key is a bug, not a new capability. */
export const draftDefinition = definition('draft',
  '起草一个要让用户确认的操作，不直接执行。kind=record 记一笔（金额分、方向、分类，可选备注与时间）；kind=budget 设预算（分类或 total、周期、额度分）；kind=reminder 设提醒（触发时间 epoch 毫秒、文案）。用户在本机点确认后才会写入。',
  schema({
    kind: enumString(DRAFT_KINDS),
    amount_cents: int(1, 10_000_000_000),
    direction: enumString(['OUT', 'IN']),
    category: text(32),
    note: text(120),
    occurred_at: int(TIME_MIN, TIME_MAX),
    period: enumString(['day', 'week', 'month']),
    limit_cents: int(1, 10_000_000_000),
    at: int(TIME_MIN, TIME_MAX),
    text: text(120),
  }));
toolDefinitions.push(draftDefinition);

const FIELDS = {
  record: ['kind', 'amount_cents', 'direction', 'category', 'note', 'occurred_at'],
  budget: ['kind', 'category', 'period', 'limit_cents'],
  reminder: ['kind', 'at', 'text'],
};
const REQUIRED = {
  record: ['amount_cents', 'direction'],
  budget: ['period', 'limit_cents'],
  reminder: ['at', 'text'],
};

const isCents = value => Number.isSafeInteger(value) && value > 0 && value <= 10_000_000_000;
const isTime = value => Number.isSafeInteger(value) && value >= TIME_MIN && value <= TIME_MAX;
const isText = (value, max) => typeof value === 'string' && value.trim().length > 0 && value.length <= max;

/**
 * Returns the draft the client should render, or `null` when the model produced anything that is not exactly a
 * draft of one of the three kinds. Callers treat `null` as a bad answer (another model may do better), never as
 * a partially usable card: a half-valid record is worse than no record.
 */
export function normalizeDraft(raw) {
  if (!raw || typeof raw !== 'object' || Array.isArray(raw)) return null;
  const kind = raw.kind;
  if (!DRAFT_KINDS.includes(kind)) return null;
  const allowed = FIELDS[kind];
  if (Object.keys(raw).some(key => !allowed.includes(key))) return null;
  if (REQUIRED[kind].some(key => raw[key] === undefined)) return null;
  if (raw.kind === 'record') {
    if (!isCents(raw.amount_cents)) return null;
    if (raw.direction !== 'OUT' && raw.direction !== 'IN') return null;
    if (raw.category !== undefined && !isText(raw.category, 32)) return null;
    if (raw.note !== undefined && !(typeof raw.note === 'string' && raw.note.length <= 120)) return null;
    if (raw.occurred_at !== undefined && !isTime(raw.occurred_at)) return null;
    return {
      kind, amount_cents: raw.amount_cents, direction: raw.direction,
      ...(raw.category !== undefined ? { category: raw.category.trim() } : {}),
      ...(raw.note ? { note: raw.note.slice(0, 120) } : {}),
      ...(raw.occurred_at !== undefined ? { occurred_at: raw.occurred_at } : {}),
    };
  }
  if (raw.kind === 'budget') {
    if (!isCents(raw.limit_cents)) return null;
    if (!['day', 'week', 'month'].includes(raw.period)) return null;
    if (raw.category !== undefined && !isText(raw.category, 32)) return null;
    return {
      kind, period: raw.period, limit_cents: raw.limit_cents,
      ...(raw.category !== undefined ? { category: raw.category.trim() } : {}),
    };
  }
  if (!isTime(raw.at)) return null;
  // A reminder in the past is already useless; refuse it instead of showing a card the person must not accept.
  if (raw.at <= Date.now()) return null;
  if (!isText(raw.text, 120)) return null;
  return { kind: 'reminder', at: raw.at, text: raw.text.trim().slice(0, 120) };
}

/** A plan step list the client can render: 1–6 steps, each naming a real data tool. */
export function normalizePlan(raw) {
  if (!raw || typeof raw !== 'object' || Array.isArray(raw)) return null;
  const steps = raw.steps;
  if (!Array.isArray(steps) || steps.length < 1 || steps.length > 6) return null;
  const out = [];
  for (const step of steps) {
    if (!step || typeof step !== 'object' || Array.isArray(step)) return null;
    const keys = Object.keys(step);
    if (keys.some(key => key !== 'tool' && key !== 'label')) return null;
    if (!DATA_TOOLS.includes(step.tool)) return null;
    if (!isText(step.label, 24)) return null;
    out.push({ tool: step.tool, label: step.label.trim().slice(0, 24) });
  }
  return out;
}
