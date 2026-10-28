# 小票机 Agent 框架（v1）

目标：让 AI 页像 GPT 一样对话，同时**只能**靠工具读取真实（本机）数据，不编数字。
范式：ReAct / native tool-use 循环（Planner 就是模型本身，Executor 在 App 内；参考 Anthropic tool-use 与 planner-executor 模式）。
落地位置：`domain/ai/ToolSpec.kt`（工具契约）+ `ui/chat/AgentEngine.kt`（本机执行器 / 演示对话）。

## 1. 角色与边界（硬约束，写进 system prompt）

- 你是「小票机」：记账 App 里的助手。只做四件事：**记账、查账、解释数字、提醒**。
- 不推荐买什么、不比价、不下单、不给理财/收益结论；订阅只告诉用户怎么取消，不代操作。
- 只陈述**用户自己的账本**与**圈子里成员已授权的数据**；没授权的金额不许猜，显示「未共享」。
- 任何演示/模拟数字必须带「演示数据」字样。
- 不确定就说不确定，并给一个可执行的下一步（比如「要不要记一笔？」）。
- 中文口语，短句，最多 3 句 + 一个数字；不要 emoji 堆砌（每段 ≤1 个）。

## 2. System Prompt（可直接用）

```
你是「小票机」，一个只服务于这个记账 App 的助手。你的工作只有四件：
1) 把用户的一句话变成账目草稿；2) 查询本机账本；3) 解释数字（同比/环比/占比/最值）；
4) 提醒（订阅到期、超支、家人共享变化）。

规则：
- 只使用工具返回的数据。没有工具结果就不要给数字。
- 不做消费建议、不比价、不推荐商品、不给理财或收益结论。
- 订阅类只说明如何取消，不代为操作。
- 圈子数据只使用对方已共享的字段；未共享说「未共享」，绝不推断。
- 演示数据必须标注「演示数据」。
- 回答最多 3 句，先给结论再给一个数字，最后给一个可点的下一步。
- 时间基准：今天 = {today}，用户时区 Asia/Shanghai。
```

## 3. 工具（本轮实现的 6 个，全部本机执行）

| name | 作用 | 参数（JSON） | 返回 |
|---|---|---|---|
| `query_transactions` | 查流水 | `{from, to, category?, direction?, limit?}` | `[{id, merchant, cents, category, occurredAt, source}]` |
| `summarize_period` | 汇总 | `{period: today\|week\|month, group_by?: day\|category}` | `{total_out, total_in, count, buckets[]}` |
| `find_peak` | 极值 | `{period, dimension: day\|category\|merchant}` | `{label, cents, at}` |
| `read_circle` | 圈子 | `{scope: me\|family, period}` | `[{name, cents\|null, posts, shared}]`（null = 未共享） |
| `draft_transactions` | 一句话解析 | `{text}` | `[{merchant, category, cents, occurredAt, confidence}]`，**不入账** |
| `commit_transactions` | 落账 | `{drafts[], confirm_token}` | `{added, skipped}`（必须用户点确认后才可调用） |

工具说明统一遵循 Anthropic 的建议：**名字像函数、描述写清何时用/何时别用、参数给例子、返回保持紧凑**（大结果只回摘要 + 引用 id）。

## 4. 循环协议

```
user → model
model → tool_use(name, args)        # 可以连续多个
app   → tool_result(name, ok, data) # 只回本机真实数据
model → …（最多 4 步）→ final_answer
```

- 工具失败：`tool_result.ok=false`，模型换一种查法或直接说明「这次没查到」，**不重试超过 2 次**。
- 超时：单工具 800ms 本机、云端 6s；超时按失败处理。
- 流式：App 侧事件 `tool_start` / `tool_end` / `token` / `card`；UI 依次渲染（当前已实现前三种 + 图表卡）。
- 打断：用户发送新消息时取消上一个 turn（coroutine cancel），已发出的 `tool_result` 不做回滚（只读）。

## 5. 演示对话流（mock，本机规则引擎驱动，接口与真实 agent 一致）

**A. 记账**
> 用户：午饭 32，骑车 2
> `draft_transactions(text)` → 2 条 → 确认卡（可改分类/删单条）→ 用户确认 → `commit_transactions` → 「记下了 2 笔，共 ¥34」

**B. 今日/本周解释**
> 用户：今天花了多少
> `summarize_period{period:today}` → 「今天到现在一共 ¥136.83，6 笔。」
> 用户：那这周呢
> `summarize_period{period:week}` + `find_peak{week,day}` → 答 + 图表卡

**C. 家人/圈子**
> 用户：家里人这周花了多少
> `read_circle{scope:family, period:week}` → 逐人金额或「未共享」→ 末尾固定一句「这些都是演示数据」

**D. 订阅提醒**
> 系统：每月 30 日 21:30 触发
> `query_transactions{category:SUBSCRIPTION, period:month}` → 「视频会员 ¥15.8 下周二扣款，要取消的话我告诉你在哪一步操作」＋ 只给路径，不代操作

## 6. 评估与回归

- 工具层：JVM 单测（`summarize_period` 边界、`find_peak` 平局、`read_circle` 脱敏）。
- 对话层：把上面 4 条 mock 流写成断言（输入 → 期望工具序列 + 关键数字），规则引擎改动必须跑。
- 上线前用真实用户句式（≥50 条/语言）过一遍 `draft_transactions` 的命中率。

## 7. 之后接云（不在本轮）

- 只把 **system prompt + 工具 JSON + 用户这句话** 发给云函数；账本数据由工具在校验后按需返回摘要，不整表上传。
- 模型：`deepseek-v4.1-flash`；key 只在 QMuse 云函数 secret；每日上限，超限回落到本机规则。
