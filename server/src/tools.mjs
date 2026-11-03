/**
 * The assistant's tool registry: the one list every other surface is derived from.
 *
 * Four kinds of tool, and only these four:
 * - `data`: read-only queries the *device* answers from the local ledger, budget, subscription scan and life log.
 *   The server only declares them; every number the model may speak must appear in one of their results.
 * - `view`: presentation only (`show_view`), filled with numbers a data tool already returned.
 * - `plan`: `plan_steps`, consumed by the server and never forwarded, so an old client never sees it.
 * - `draft`: an action proposal (record / budget / reminder / meal / activity) validated here and pushed to the
 *   client as a card. The server never writes anything; the person confirms on the device and the existing sync
 *   routes do the write.
 *
 * The prompt (`prompt.mjs`), the definitions sent to the model, the step labels, the data-tool set, the draft
 * kinds and the README table (`scripts/tools.mjs`) all come from `TOOLS` / `DRAFT_SPECS`, so a tool or a draft kind
 * cannot exist in one place and be missing in another.
 *
 * Not reachable on purpose: card wallet / statement text / note bodies. `query_transactions` and the life tools say
 * so in their descriptions, and the ledger sync route refuses raw text separately.
 */

const enumString = values => ({ type: 'string', enum: values });
const period = enumString(['today', 'week', 'month']);
const schema = properties => ({ type: 'object', properties, additionalProperties: false });
const int = (minimum, maximum) => ({ type: 'integer', minimum, maximum });
const text = maximum => ({ type: 'string', maxLength: maximum });

/** epoch-ms bounds shared with the ledger sync route so a garbage timestamp cannot pin a query forever. */
export const TIME_MIN = 1;
export const TIME_MAX = 4_102_444_800_000;

const range = () => schema({ from: int(TIME_MIN, TIME_MAX), to: int(TIME_MIN, TIME_MAX) });

/** Presentation payload of `show_view`. A closed list, because the server validates `kind` against it. */
const VIEW_KINDS = ['spend_summary', 'category_split', 'trend', 'entries', 'peak', 'weekly_report', 'subscriptions', 'draft_list'];
const viewRow = {
  type: 'object',
  properties: {
    label: { type: 'string' }, merchant: { type: 'string' }, category: { type: 'string' },
    cents: { type: 'integer' }, occurred_at: { type: 'integer' }, cadence: { type: 'string' },
  },
  additionalProperties: false,
};

/**
 * Every tool the model can see. `label` is product copy the client shows verbatim ("正在查预算"), `summary` is the
 * one-line explanation used by the prompt and the README, `description` is what the model itself reads.
 */
const BASE_TOOLS = [
  {
    kind: 'data', name: 'summarize_period', label: '正在汇总账本',
    summary: '一个周期的总额/笔数，按天或按分类分组；金额是分',
    description: '汇总本机账本一个周期的总额、笔数与分组；只返回统计结果，不上传账单原文。金额单位是分（cents）。',
    parameters: schema({ period, group_by: enumString(['day', 'category']) }),
  },
  {
    kind: 'data', name: 'find_peak', label: '正在查找最大支出',
    summary: '某周期最大的一天、一类或一家商户；金额是分',
    description: '查本机账本中某周期最大的一天/一类/一家商户。金额单位是分（cents）。',
    parameters: schema({ period, dimension: enumString(['day', 'category', 'merchant']) }),
  },
  {
    kind: 'data', name: 'query_transactions', label: '正在查询明细',
    summary: '按时间查最多 20 条结构化记录；不含备注原文、卡包或截图',
    description: '按时间查询本机结构化记录，最多 20 条。返回金额（分）、商户、分类、方向、时间；不读取备注原文、卡包或账单截图。',
    parameters: schema({ from: int(TIME_MIN, TIME_MAX), to: int(TIME_MIN, TIME_MAX), limit: int(1, 20) }),
  },
  {
    kind: 'data', name: 'read_circle', label: '正在检查共享权限',
    summary: '只读明确授权共享的圈子数据；没授权就是「未共享」',
    description: '仅查询明确授权共享的圈子数据；没有授权返回未共享。金额单位是分（cents）。',
    parameters: schema({ period, scope: enumString(['me', 'family']) }),
  },
  {
    kind: 'data', name: 'read_budget', label: '正在查预算',
    summary: '各周期与各分类的额度、已用、剩余；金额是分',
    description: '读取本机预算：各周期的总额度、已用、剩余，以及每个分类的月度额度、已用、剩余。金额单位是分（cents）。没有设预算时返回 configured=false。不给建议、不评判。',
    parameters: schema({ period: enumString(['day', 'week', 'month', 'all']) }),
  },
  {
    kind: 'data', name: 'summarize_range', label: '正在对比区间支出',
    summary: '任意起止时间的收支，按天/周/月/分类分组；金额是分',
    description: '汇总任意起止时间（epoch 毫秒，左闭右开）的收支，按天/周/月/分类分组。金额单位是分（cents）。用于「上个月」「最近 30 天」「和上上周比」。',
    parameters: schema({
      from: int(TIME_MIN, TIME_MAX), to: int(TIME_MIN, TIME_MAX),
      group_by: enumString(['day', 'week', 'month', 'category']),
    }),
  },
  {
    kind: 'data', name: 'read_subscriptions', label: '正在找规律扣款',
    summary: '订阅侦探的结果：商户、金额、间隔、次数；金额是分',
    description: '读取本机订阅侦探的结果：规律扣款的商户、金额（分）、间隔天数、最近一次、下次预计、出现次数。只用来判断有没有订阅，取消路径由用户自己操作。',
    parameters: schema({ limit: int(1, 20) }),
  },
  {
    kind: 'data', name: 'read_weekly_report', label: '正在看周报和头衔',
    summary: '本地生成的周报与头衔',
    description: '读取本机已生成的周报与头衔：周区间标签、支出合计（分）、笔数、与上周比较的百分比、前三分类、最大一笔、获得的头衔。',
    parameters: schema({ week_offset: int(0, 12) }),
  },
  {
    kind: 'data', name: 'get_life_days', label: '正在汇总每天的吃和动',
    summary: '按天的摄入/消耗 kcal、五类食物 kcal、蛋白脂肪碳水克数、餐次时间、运动分钟与类型',
    description: '按天读本机生活记录：摄入 kcal、消耗 kcal、五类食物（staple 主食 / veg 蔬果 / protein 蛋白 / drink 饮品 / snack 零食）的 kcal、蛋白脂肪碳水克数、餐次时间、运动分钟与类型。用来复盘「这周吃得怎么样」「哪天吃得最晚」。单位 kcal 与克；不返回备注原文，也不返回单个食物的明细。',
    parameters: range(),
  },
  {
    kind: 'data', name: 'get_life_top_foods', label: '正在数常吃的食物',
    summary: '常吃的食物与次数',
    description: '读本机生活记录里最常吃的食物与次数（不含热量明细、不含备注）。用来回答「我常吃什么」。',
    parameters: schema({ from: int(TIME_MIN, TIME_MAX), to: int(TIME_MIN, TIME_MAX), limit: int(1, 20) }),
  },
  {
    kind: 'data', name: 'get_meal_spend', label: '正在对餐饮支出',
    summary: '餐饮支出与饮食记录的关联：外卖/堂食金额（分）、单餐均价',
    description: '对账：把餐饮类支出与饮食记录关联起来，外卖/堂食各花多少（分）、单餐均价（分）、多少顿有对应账单。不读备注原文。',
    parameters: range(),
  },
  {
    kind: 'data', name: 'list_categories', label: '正在读取分类',
    summary: '本机分类清单，用来对应用户说的分类名',
    description: '列出本机账本的分类（id、名称、收支方向）。用于把用户说的「吃饭」对应到实际分类；不返回金额。',
    parameters: schema({}),
  },
  {
    kind: 'view', name: 'show_view', label: '正在生成视图',
    summary: '用取数结果渲染一个界面视图',
    description: '用取数工具返回的数字渲染一个界面视图。先取数，再调用一次；数字必须与工具结果一致，单位是分。',
    parameters: schema({
      kind: enumString(VIEW_KINDS),
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
    }),
  },
];

/** `plan_steps` is built last: its enum is the data-tool list, which only exists once `BASE_TOOLS` is complete. */
const planTool = () => ({
  kind: 'plan', name: 'plan_steps', label: '正在拆步骤',
  summary: '多步查询前先写 1–6 步计划',
  description: '开始一次需要多步查询的提问时，先写一个简短计划（1–6 步），每步说明要调哪个取数工具、给用户看的一句话（例如「正在查预算」）。调用后系统会替你记下并继续。',
  parameters: schema({
    steps: {
      type: 'array', minItems: 1, maxItems: 6,
      items: {
        type: 'object',
        properties: {
          tool: enumString(BASE_TOOLS.filter(tool => tool.kind === 'data').map(tool => tool.name)),
          label: text(24),
        },
        required: ['tool', 'label'],
        additionalProperties: false,
      },
    },
  }),
});

export const TOOLS = Object.freeze([...BASE_TOOLS, planTool()]);

const byKind = kind => TOOLS.filter(tool => tool.kind === kind).map(tool => tool.name);
export const DATA_TOOLS = Object.freeze(byKind('data'));
/** Server-side tools: never forwarded to the client, so an old client needs no change to keep working. */
export const SERVER_TOOLS = Object.freeze(byKind('plan'));
export const TOOL_LABELS = Object.freeze(Object.fromEntries(TOOLS.map(tool => [tool.name, tool.label])));
export const toolDefinitions = TOOLS.map(tool => ({
  type: 'function',
  function: { name: tool.name, description: tool.description, parameters: tool.parameters },
}));

const isCents = value => Number.isSafeInteger(value) && value > 0 && value <= 10_000_000_000;
const isPrice = value => Number.isSafeInteger(value) && value > 0 && value <= 10_000_000;
const isTime = value => Number.isSafeInteger(value) && value >= TIME_MIN && value <= TIME_MAX;
const isText = (value, maximum) => typeof value === 'string' && value.trim().length > 0 && value.length <= maximum;
const sameKeys = (value, allowed) => !Object.keys(value).some(key => !allowed.includes(key));

const MEALS = Object.freeze(['breakfast', 'lunch', 'dinner', 'snack']);
const mealItem = {
  type: 'object',
  properties: { name: { type: 'string', maxLength: 40 }, qty: { type: 'number', exclusiveMinimum: 0, maximum: 100 }, unit: { type: 'string', maxLength: 8 } },
  required: ['name', 'qty', 'unit'],
  additionalProperties: false,
};
const setItem = {
  type: 'object',
  properties: { reps: int(1, 100), kg: { type: 'number', minimum: 0, maximum: 500 } },
  required: ['reps'],
  additionalProperties: false,
};

/**
 * The action proposal. Each kind validates itself against a strict field whitelist: an unknown key is a bug, not a
 * new capability, and a half-valid card is worse than no card.
 *
 * `meal` and `activity` deliberately have no calorie field anywhere — the app's own food and MET knowledge base
 * computes every number, so a model that volunteers `kcal` is rejected rather than trusted.
 */
export const DRAFT_SPECS = Object.freeze({
  record: {
    hint: '金额分、方向、可选分类/备注/时间',
    fields: ['kind', 'amount_cents', 'direction', 'category', 'note', 'occurred_at'],
    required: ['amount_cents', 'direction'],
    validate(raw) {
      if (!isCents(raw.amount_cents)) return null;
      if (raw.direction !== 'OUT' && raw.direction !== 'IN') return null;
      if (raw.category !== undefined && !isText(raw.category, 32)) return null;
      if (raw.note !== undefined && !(typeof raw.note === 'string' && raw.note.length <= 120)) return null;
      if (raw.occurred_at !== undefined && !isTime(raw.occurred_at)) return null;
      return {
        kind: 'record', amount_cents: raw.amount_cents, direction: raw.direction,
        ...(raw.category !== undefined ? { category: raw.category.trim() } : {}),
        ...(raw.note ? { note: raw.note.slice(0, 120) } : {}),
        ...(raw.occurred_at !== undefined ? { occurred_at: raw.occurred_at } : {}),
      };
    },
  },
  budget: {
    hint: '周期与额度分，可选分类',
    fields: ['kind', 'category', 'period', 'limit_cents'],
    required: ['period', 'limit_cents'],
    validate(raw) {
      if (!isCents(raw.limit_cents)) return null;
      if (!['day', 'week', 'month'].includes(raw.period)) return null;
      if (raw.category !== undefined && !isText(raw.category, 32)) return null;
      return {
        kind: 'budget', period: raw.period, limit_cents: raw.limit_cents,
        ...(raw.category !== undefined ? { category: raw.category.trim() } : {}),
      };
    },
  },
  reminder: {
    hint: '触发时间 epoch 毫秒、文案 1–120',
    fields: ['kind', 'at', 'text'],
    required: ['at', 'text'],
    validate(raw, { now }) {
      if (!isTime(raw.at)) return null;
      // A reminder in the past is already useless; refuse it instead of showing a card the person must not accept.
      if (raw.at <= now) return null;
      if (!isText(raw.text, 120)) return null;
      return { kind: 'reminder', at: raw.at, text: raw.text.trim().slice(0, 120) };
    },
  },
  meal: {
    hint: '餐次、时间、items[{name,qty,unit}]，可选 price_cents',
    fields: ['kind', 'meal', 'at', 'items', 'price_cents'],
    required: ['meal', 'at', 'items'],
    validate(raw) {
      if (!MEALS.includes(raw.meal)) return null;
      if (!isTime(raw.at)) return null;
      if (!Array.isArray(raw.items) || raw.items.length < 1 || raw.items.length > 12) return null;
      const items = [];
      for (const item of raw.items) {
        // The item object is a whitelist too: `{name, qty, unit, kcal}` is a model that misunderstood the task.
        if (!item || typeof item !== 'object' || Array.isArray(item)) return null;
        if (!sameKeys(item, ['name', 'qty', 'unit'])) return null;
        if (!isText(item.name, 40)) return null;
        if (typeof item.qty !== 'number' || !Number.isFinite(item.qty) || item.qty <= 0 || item.qty > 100) return null;
        if (!isText(item.unit, 8)) return null;
        items.push({ name: item.name.trim().slice(0, 40), qty: Math.round(item.qty * 100) / 100, unit: item.unit.trim().slice(0, 8) });
      }
      if (raw.price_cents !== undefined && !isPrice(raw.price_cents)) return null;
      return {
        kind: 'meal', meal: raw.meal, at: raw.at, items,
        ...(raw.price_cents !== undefined ? { price_cents: raw.price_cents } : {}),
      };
    },
  },
  activity: {
    hint: '名称、时间，minutes 或 sets[{reps,kg}] 至少一个',
    fields: ['kind', 'name', 'at', 'minutes', 'sets'],
    required: ['name', 'at'],
    validate(raw) {
      if (!isText(raw.name, 40) || !isTime(raw.at)) return null;
      if (raw.minutes === undefined && raw.sets === undefined) return null;
      if (raw.minutes !== undefined && !(Number.isSafeInteger(raw.minutes) && raw.minutes >= 1 && raw.minutes <= 600)) return null;
      let sets;
      if (raw.sets !== undefined) {
        if (!Array.isArray(raw.sets) || raw.sets.length < 1 || raw.sets.length > 30) return null;
        sets = [];
        for (const set of raw.sets) {
          if (!set || typeof set !== 'object' || Array.isArray(set)) return null;
          if (!sameKeys(set, ['reps', 'kg'])) return null;
          if (!(Number.isSafeInteger(set.reps) && set.reps >= 1 && set.reps <= 100)) return null;
          if (set.kg !== undefined && !(typeof set.kg === 'number' && Number.isFinite(set.kg) && set.kg >= 0 && set.kg <= 500)) return null;
          sets.push({ reps: set.reps, ...(set.kg !== undefined ? { kg: Math.round(set.kg * 10) / 10 } : {}) });
        }
      }
      return {
        kind: 'activity', name: raw.name.trim().slice(0, 40), at: raw.at,
        ...(raw.minutes !== undefined ? { minutes: raw.minutes } : {}), ...(sets ? { sets } : {}),
      };
    },
  },
});

export const DRAFT_KINDS = Object.freeze(Object.keys(DRAFT_SPECS));

export const draftDefinition = {
  type: 'function',
  function: {
    name: 'draft',
    description: '起草一个要让用户确认的操作，不直接执行。kind=record 记一笔（金额分、方向、分类，可选备注与时间）；'
      + 'kind=budget 设预算（可选分类、周期、额度分）；kind=reminder 设提醒（时间 epoch 毫秒、文案）；'
      + 'kind=meal 记一顿（餐次、时间、食物 items[{name,qty,unit}]、可选 price_cents）；'
      + 'kind=activity 记运动（name、minutes 或 sets[{reps,kg}]、时间）。不要给热量，热量由 App 本机知识库算。'
      + '用户在本机点确认后才会写入。',
    parameters: schema({
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
      meal: enumString(MEALS),
      items: { type: 'array', minItems: 1, maxItems: 12, items: mealItem },
      price_cents: int(1, 10_000_000),
      name: text(40),
      minutes: int(1, 600),
      sets: { type: 'array', minItems: 1, maxItems: 30, items: setItem },
    }),
  },
};
toolDefinitions.push(draftDefinition);

/**
 * Returns the draft the client should render, or `null` when the model produced anything that is not exactly a
 * draft of one of the registered kinds. Callers treat `null` as a bad answer (another model may do better), never
 * as a partially usable card.
 */
export function normalizeDraft(raw, { now = Date.now() } = {}) {
  if (!raw || typeof raw !== 'object' || Array.isArray(raw)) return null;
  const spec = DRAFT_SPECS[raw.kind];
  if (!spec) return null;
  if (!sameKeys(raw, spec.fields)) return null;
  if (spec.required.some(key => raw[key] === undefined)) return null;
  return spec.validate(raw, { now });
}

/** A plan step list the client can render: 1–6 steps, each naming a real data tool. */
export function normalizePlan(raw) {
  if (!raw || typeof raw !== 'object' || Array.isArray(raw)) return null;
  const steps = raw.steps;
  if (!Array.isArray(steps) || steps.length < 1 || steps.length > 6) return null;
  const out = [];
  for (const step of steps) {
    if (!step || typeof step !== 'object' || Array.isArray(step)) return null;
    if (!sameKeys(step, ['tool', 'label'])) return null;
    if (!DATA_TOOLS.includes(step.tool)) return null;
    if (!isText(step.label, 24)) return null;
    out.push({ tool: step.tool, label: step.label.trim().slice(0, 24) });
  }
  return out;
}

/** Prompt fragments, so the instructions cannot list a tool or a draft kind that does not exist. */
export function promptToolLines() {
  return TOOLS.filter(tool => tool.kind === 'data').map(tool => `- ${tool.name}：${tool.summary}`);
}

export function promptDraftLine() {
  return DRAFT_KINDS.map(kind => `${kind}（${DRAFT_SPECS[kind].hint}）`).join('、');
}

/** The generated half of `server/README.md`, kept in sync by `scripts/tools.mjs` and asserted by a test. */
export function renderToolDoc() {
  const rows = TOOLS.map(tool => `| \`${tool.name}\` | ${tool.kind} | ${tool.summary} |`);
  return [
    '| 工具 | 类型 | 用途 |',
    '|---|---|---|',
    ...rows,
    '',
    `起草 kind：${promptDraftLine()}`,
    '',
    `金额一律整数分（cents）；生活类汇总单位是 kcal，克数用 g。数据和视图工具在设备本机执行，服务端只声明 schema。`,
  ].join('\n');
}
