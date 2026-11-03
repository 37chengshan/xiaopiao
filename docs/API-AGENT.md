# 账本智能体 · 服务端协议（2026-11-03）

对象：App（Kotlin）与产品版 Web。接口本体仍是 `POST /v1/chat`，工具在**本机**执行，服务端只声明 schema、校验输出、推送事件。比赛版 Web 走 QMuse 云服务，不在此文范围。

依据：`docs/SPEC.md` 末尾「2026-11-02 修订二」（账本智能体段落）、`GLOSSARY.md`（起草、账本智能体）。

## 1. 一轮对话的形状

```
客户端 --POST /v1/chat {messages, timezone}----------------> 服务端
客户端 <--SSE: status / plan / step / draft / token / view-- 服务端
客户端 <--SSE: tool_calls {runId, calls[]}------------------- 服务端
客户端（本机执行工具）--POST /v1/chat {runId, results[]}----> 服务端
客户端 <--SSE: step(done) / token / view / tool_calls / done- 服务端
```

`tool_calls` 仍按原协议：客户端在本机执行，把 `{id, data}` 数组带回来。新增的 `plan` / `step` / `draft` 只是**额外**事件，不认识它们的旧客户端忽略即可（见 §9）。

**文字只在整段校验通过后出现**：模型说的话先全部缓冲（上限 24k 字符），服务端对**整段**做数字溯源与越界检查，通过才用一个 `token` 事件一次性下发；被拒的回答一个字都不会发出，只会换下一个模型重试。进度类事件（`status` / `plan` / `step`）仍然实时推送，所以等待期间界面不是空的。这是刻意的取舍：越界建议和编造的数字几乎总在最后一句，按 64 字闸门放行等于让长回答整段免检。

## 2. 取数工具（只读，本机执行）

服务端只发 schema。所有金额字段单位都是**整数分（cents）**；`get_life_days` / `get_life_top_foods` 是 kcal 与次数，三大营养素是克。响应由客户端按下面的形状返回，字段多余无害，但金额字段必须是整数。

工具、中文步骤文案、提示词清单、draft kind 与 `server/README.md` 里那张表都由 `server/src/tools.mjs` 的同一份注册表生成（`node server/scripts/tools.mjs --write` 重写 README 段，测试会检查两者一致）。下面的表是这份注册表的说明版。

| 工具 | 参数 | 客户端返回 | 说明 |
|---|---|---|---|
| `summarize_period` | `period: today/week/month`、`group_by: day/category` | `{period, unit:"cents", count, total_out, total_in, buckets:[{label,cents}]}` | 原有 |
| `find_peak` | `period`、`dimension: day/category/merchant` | `{label, cents, unit:"cents"}` | 原有 |
| `query_transactions` | `from`、`to`（epoch ms，左闭右开）、`limit ≤ 20` | `{matched_count, truncated, unit:"cents", rows:[{id,cents,merchant,category,direction,occurredAt}]}` | 原有；不含备注原文 |
| `read_circle` | `period`、`scope: me/family` | `{shared, rows?...}`，无授权 `{shared:false}` | 原有 |
| `read_budget` | `period: day/week/month/all`（可省略 = all） | `{period, unit:"cents", configured, totals:[{period,limit_cents,spent_cents,remaining_cents,days_left}], categories:[{label,limit_cents,spent_cents,remaining_cents}], days_left}` | 新增。`configured=false` 表示用户没设预算，模型只能说「没设预算」 |
| `summarize_range` | `from`、`to`、`group_by: day/week/month/category` | `{from,to,group_by,unit:"cents",count,total_out,total_in,buckets:[{label,cents}]}` | 新增。任意区间，用于「上个月」「最近 30 天」 |
| `read_subscriptions` | `limit ≤ 20` | `{unit:"cents",count,yearly_cents,rows:[{merchant,cents,cadence,cycle_days,occurrences,last_charged_at,next_charge_at}]}` | 新增。订阅侦探的既有结果，只读 |
| `read_weekly_report` | `week_offset: 0–12`（0 = 本周） | `{week_label,total_out,count,vs_prev_percent,top_category,top_categories:[{label,cents}],biggest:{label,cents},badges:[{title,reason}],longest_streak,zero_days}` | 新增。`badges` 就是头衔 |
| `get_life_days` | `from`、`to` | `{unit:"kcal",from,to,days:[{day,intake_kcal,burn_kcal,groups:{staple,veg,protein,drink,snack},protein_g,fat_g,carb_g,meals:[{meal,at}],exercise:[{kind,name,minutes}]}],totals:{intake_kcal,burn_kcal,protein_g,fat_g,carb_g}}` | 新增。按天复盘：摄入/消耗、五类食物 kcal、三大营养素克数、餐次时间、运动分钟与类型 |
| `get_life_top_foods` | `from`、`to`、`limit ≤ 20` | `{unit:"count",count,rows:[{name,times}]}` | 新增。常吃食物与次数 |
| `get_meal_spend` | `from`、`to` | `{unit:"cents",meals,linked,unlinked,total_cents,average_cents,by_channel:[{channel:"takeout"/"dine_in"/"unknown",count,cents}]}` | 新增。餐饮支出与饮食记录对账：外卖/堂食金额、单餐均价 |
| `list_categories` | 无 | `{count, rows:[{id,label,direction}]}` | 新增。用于把「吃饭」对上实际分类名 |

**不提供**：卡包/银行卡、账单原文与截图、备注原文。模型问不到，客户端也不必实现；`query_transactions` 的返回里本来就没有 `note`，三个生活工具也只给汇总与食物名+次数。

生活类返回的一条硬规则：**任何工具都不返回热量以外的推断值**，也不返回模型需要"判断"的字段。热量由 App 本机知识库算，工具只搬运（`get_life_days` 的 kcal 来自本机已算好的记录）。

## 3. 展示：`show_view`（原有，未变）

`kind` 是注册表里的固定列表（`server/src/tools.mjs` 的 `VIEWS`，目前 8 种：`spend_summary` / `category_split` / `trend` / `entries` / `peak` / `weekly_report` / `subscriptions` / `draft_list`），prompt 的视图说明、`show_view` 的 schema 枚举与 README 都从这份注册表生成。未知 `kind` 会被服务端丢掉，并由服务端用本轮工具结果合成视图（`view` 事件）。**新工具没有新增视图**：预算、区间、生活记录目前只有文字结论，需要卡片时另开需求。

## 4. 多步规划：`plan` 与 `step`

模型可以调用服务端私有工具 `plan_steps`：

```json
{ "steps": [ { "tool": "read_budget", "label": "正在查预算" },
             { "tool": "summarize_range", "label": "正在对比本月支出" } ] }
```

- `plan_steps` **不会下发给客户端**，服务端直接以 `{"ok":true}` 应答并继续同一轮对话；因此旧客户端不会看到未知工具。
- 若模型没写计划但一条消息里要调 ≥2 个取数工具，服务端按工具顺序自动合成同样的计划。
- 计划每轮（同一个用户提问）只发一次。

事件字段：

```json
{ "type": "plan", "steps": [ { "index": 1, "tool": "read_budget", "label": "正在查预算" } ] }
{ "type": "step", "id": "<tool_call id>", "tool": "read_budget", "label": "正在查预算", "status": "running" }
{ "type": "step", "id": "<tool_call id>", "tool": "read_budget", "label": "正在查预算", "status": "done" }
```

- 顺序固定：`plan` → 每个待执行工具的 `step(running)` → 客户端收到 `tool_calls`；下一次请求进来时先补发上一批的 `step(done)`，再继续。
- `label` 是产品文案（`src/tools.mjs` 的 `TOOL_LABELS`），客户端直接显示，不要自己拼。
- 同一 `id` 的 `done` 只发一次；`show_view` 与 `plan_steps` 不产生 `step`。

## 5. 起草：`draft`

`draft` 是**动作提议**，不是写入。服务端校验格式，通过后：① 发 `draft` 事件；② 同一次 `tool_calls` 里带上该 call。客户端二选一渲染（推荐用事件），然后必须回一个 `{ "id": "<call id>", "data": { "ok": true } }` 让这轮结束。

```json
{ "type": "draft", "draft": { "kind": "record",  "amount_cents": 2500, "direction": "OUT", "category": "吃饭", "note": "牛肉面", "occurred_at": 1762137600000 } }
{ "type": "draft", "draft": { "kind": "budget",  "period": "month", "limit_cents": 300000, "category": "吃饭" } }
{ "type": "draft", "draft": { "kind": "reminder","at": 1762141200000, "text": "记得交房租" } }
```

| kind | 必填 | 可选 | 校验 |
|---|---|---|---|
| `record` | `amount_cents`（正整数分）、`direction`（`OUT`/`IN`） | `category`（≤32）、`note`（≤120）、`occurred_at`（epoch ms） | 非整分、负数、方向非法 → 整条拒绝 |
| `budget` | `period`（`day`/`week`/`month`）、`limit_cents`（正整数分） | `category` | 周期非法 → 整条拒绝 |
| `reminder` | `at`（epoch ms，必须晚于当前时间）、`text`（1–120） | — | 过去时间 → 整条拒绝 |
| `meal` | `meal`（`breakfast`/`lunch`/`dinner`/`snack`）、`at`（epoch ms）、`items[{name ≤40, qty 0–100, unit ≤8}]`（1–12 项） | `price_cents`（正整数分） | 缺 `unit`、`qty` ≤ 0、项里多字段（如 `kcal`）→ 整条拒绝 |
| `activity` | `name`（≤40）、`at`（epoch ms）、且 `minutes`（1–600）或 `sets[{reps 1–100, kg 0–500}]`（1–30 组）二者至少一个 | 两者可以都给 | 都没有、或 `sets` 里带 `kcal` → 整条拒绝 |

```json
{ "type": "draft", "draft": { "kind": "meal", "meal": "lunch", "at": 1762140000000,
  "items": [ { "name": "牛肉面", "qty": 1, "unit": "碗" } ], "price_cents": 2500 } }
{ "type": "draft", "draft": { "kind": "activity", "name": "跑步", "at": 1762140000000, "minutes": 30 } }
{ "type": "draft", "draft": { "kind": "activity", "name": "深蹲", "at": 1762140000000, "sets": [ { "reps": 10, "kg": 60 } ] } }
```

- **`meal` 与 `activity` 没有任何热量字段**：`kcal` 出现在 draft 或 `items[]` 里，整条被丢弃并换模型重试。热量永远由 App 的本机食物/运动知识库计算，模型只提供「吃了什么、多少份、动了多久」。
- 字段白名单：多一个字段（例如 `card_last4`）整条拒绝。服务端**绝不写入账本**；用户在本机确认后，客户端走原有的 `POST /v1/ledger/push`（生活记录写本机 Room 表，不上云）。
- 一次提问最多一个 `draft`（提示词约束）。非法 `draft` 不会以残缺形式出现：服务端丢弃这次模型输出并换一个模型重试；全部失败则返回 `error: MODEL_UNAVAILABLE`。

## 6. 能做与不能做（AI × 饮食健康的边界）

| 允许（数字来自工具或用户这一问给的数） | 禁止 |
|---|---|
| 复盘：这周吃了多少 kcal、五类食物各多少、哪天吃得最晚、常吃什么、外卖 vs 堂食花了多少 | 推荐吃什么、说哪种食物更好、比价 |
| 对账：餐饮支出与饮食记录的关联、单餐均价、一周外卖总额 | 评价吃得好坏、「超标」「不健康」这类指责性判断 |
| 模拟后果：「按每天少一杯奶茶算，一个月省 ¥X、少 Y kcal」，并说明是按你给的数算的 | 减重/增肌计划、目标设定、医疗或营养治疗建议、体重评论 |
| 提醒：到点提醒记录或喝水（`draft.reminder`，用户点确认才生效） | 主动推送、替用户做选择 |

被问到越界问题时，模型按 `prompt.mjs` 的规则礼貌说明「我只帮记录、复盘和算账，吃什么由你决定」。这是提示词约束 + 服务端的数字来源校验（`UNSOURCED_NUMBER`）一起兜底：规则写在提示词里，数字没有工具来源会被服务端直接丢弃。

## 7. 预算与上限

| 项 | 值 | 行为 |
|---|---|---|
| 轮数 | 8（含继续请求） | 到达上限不再调模型，用已有结果收尾 |
| 整轮时长 | 150 s（跨多次 HTTP 请求累计） | 超时同样收尾 |
| 单轮首 token | 9–20 s，按模型实测延迟自适应（`router.mjs`） | 首 token 超时换模型 |
| 单轮总时长 | 首 token 预算 + 12 s | 超时放弃该模型 |
| 每次请求的工具数 | 6 | 超出 `TOOL_LIMIT` |
| 单条工具参数 | 4096 字符 | 超出 `ARGUMENT_LIMIT` |

「收尾」的具体表现：若本轮已有可用数字，服务端自己合成视图（`view` 事件）；有数字但没有匹配视图时发一句「这一轮查得比较多，先把已经查到的结果给你。」；`done` 事件带 `{"truncated": true}`。**不会空着结束，也不会用错误事件代替结果。**

## 8. 数字来源与表达边界（服务端强制）

**每个数字都必须能被追溯**（`server/src/sources.mjs`），而且只在本轮结果的同一份数据内部做推导：

| 允许的来源 | 例子 |
|---|---|
| 本轮某个取数工具里的数字（含分→元换算、取整） | 工具给 `total_out: 5500` → 回「¥55.00」「55」 |
| 数组长度（笔数） | `rows` 有 3 项 → 回「3 笔」；只有 2 项时「3 笔」被拒 |
| **同一字段**的合计 / 均值 / 最大 / 最小 | `rows[{cents:1500}×3]` → 合计 4500 分 → 「¥45.00」 |
| **同一份结果里**两个数的差与比值（百分比，0/1 位小数） | `{limit_cents:10000, spent_cents:4000}` → 「40%」「还差 ¥60.00」 |
| 用户在**本轮或历史消息**里自己打的数字，以及它们之间的和/差/积/商 | 「每天 15 元的奶茶，30 天」→ 「¥450.00」放行，480 不放行 |

刻意不做的事：跨两份结果的任何算术（两个无关数字不能凑出一个新数）；把日期/时间拆成数字（`day:"2026-03-05"` 不是 2026、3、5）。校验回复前先整体剥掉日期时间写法（`2026-11-03`、`11-03`、`11月3日`、`周三`、`14:30`、`3点20分`），所以「11月3日合计 ¥45.00」不会被误判。

所有派生值同时生成「分→元」版本，所以合计 4500 分既能写 `4500` 也能写 `¥45.00`。不算来源的：失败的工具结果（`{error:"TOOL_NOT_ALLOWED"}`）、没有任何数字的结果、上一轮提问的工具结果、模型自己的上一句。命中即丢弃这次回答并换一个模型重试（`UNSOURCED_NUMBER`）；全部模型都不过就返回 `error: MODEL_UNAVAILABLE`，不会把编造的数字展示出来。

**越界内容拦截**（`agent.mjs` 的 `ADVICE_PATTERNS`，最后一道闸，主控制仍是提示词）。覆盖的不只是「建议多吃」，还有口语里的祈使与委婉句式：

- 中文：`建议/推荐/应该/最好/不妨/可以/尽量/记得` + `吃/喝/选/改/换`；`多吃/少吃/多喝/少喝`（过去式「少吃了 3 顿」不算）；`忌口/戒掉/控制饮食/管住嘴/别吃了`；`减肥/减重/增肌/瘦身` 的「计划/方案/目标/食谱」；`你吃得太咸/太少`；`健康有风险`、`可能导致`。
- 英文（App 是全球性的）：`you should eat`、`try eating less`、`cut down on`、`I'd recommend`、`eat more/less`、`reduce your intake`、`weight-loss plan`、`healthier choice`。

命中同样丢弃并换模型（`OUT_OF_SCOPE_ADVICE`）。两类检查都在**展示前**对整段生效，用户看不到被拒的文字。反例由测试守住：复盘句（「你这周吃了 3 次火锅」「午餐花了 ¥45.00」）和拒答句（「我只帮记录、复盘和算账」）不能被误伤。

第一道控制仍是 `prompt.mjs`：复盘、算账、按已有数字模拟后果都可以；推荐吃什么、评价好坏、减重计划、医疗建议一律拒绝并说明「我只帮记录、复盘和算账」。

## 9. 兼容性

| 客户端 | 行为 |
|---|---|
| 旧 App / 旧 Web | 不认识 `plan` / `step` / `draft` 就忽略；`plan_steps` 从不下发，所以不会出现「未知工具」；`show_view` 行为与字段完全没变 |
| 新客户端 | 建议：`plan` 画步骤条，`step` 更新当前步骤，`draft` 渲染确认卡并在用户确认后调用 `/v1/ledger/push`，`done.truncated` 显示「先到这里」 |

## 10. 服务端实现位置

| 文件 | 职责 |
|---|---|
| `server/src/tools.mjs` | 工具目录、`TOOL_LABELS`、`normalizeDraft`、`normalizePlan`、数据类型白名单 |
| `server/src/agent.mjs` | 一轮对话：模型路由 → 流解析 → draft 校验 → 计划应答 → 事件顺序 → 轮数/时长收尾 |
| `server/src/views.mjs` | 无 `show_view` 时的兜底视图（只认有对应 `kind` 的工具结果） |
| `server/src/prompt.mjs` | system prompt 单一来源，包含工具清单与 `draft` 规则 |

测试：`server/test/agent-tools.test.mjs`（schema、事件顺序、轮数/超时收尾、draft 校验、UNSOURCED_NUMBER 覆盖新工具）。
