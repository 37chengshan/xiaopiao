# 小票 Agent API

Node 24.14+，独立部署到已批准的华为云 / 阿里云 Linux 主机。认证、验证码与 SSE Agent 共用一个 API；不替代 QMuse 云服务身份或表权限。

## 本机

```sh
npm ci --prefix server
npm test --prefix server
# 私密环境已在 ~/.config/agentmail/env，当前终端没有加载时：
. "$HOME/.config/agentmail/env"
# 确认发信箱后设置 AGENTMAIL_INBOX_ID；模型密钥设置 MODEL_API_KEY。
npm start --prefix server
```

本机默认监听 `127.0.0.1:8787`，Web dev 将 `/v1` 转发到该地址。数据库默认在启动目录 `data/xiaopiao.sqlite`；不要提交数据库、环境文件或任何密钥。

| 服务端环境变量 | 用途 |
|---|---|
| `MODEL_API_KEY` | 已选 commandcode 模型 API 密钥，不能使用 OCX 给产品转发 |
| `MODEL_PROVIDER` | 可选，默认 `commandcode`；取值见 `src/providers.mjs` 的 `providers` 表 |
| `AGENTMAIL_API_KEY` | AgentMail API 密钥，仅服务端可读 |
| `AGENTMAIL_INBOX_ID` | 明确选择的现有发信箱地址，必须支持真实发送 |
| `APP_ORIGIN` | Web 的精确 origin；生产必须为 HTTPS |
| `NODE_ENV` | 生产设 `production`，启用 Secure Cookie |
| `TRUST_PROXY` | 仅私有网关后设 `1`；Compose 已配置覆盖客户端 IP 头，API 不直接暴露。默认忽略所有转发 IP 头 |
| `HOST`, `PORT` | 本机默认 `127.0.0.1:8787`；容器为 `0.0.0.0:8787` |
| `DB_PATH` | SQLite 路径；容器为 `/app/data/xiaopiao.sqlite` |
| `PASSWORD_PEPPER` | 可选。设置后密码先 HMAC 再 scrypt，新哈希带 `scrypt1p$` 前缀；一旦启用必须长期保留，丢失则这些账号无法登录（启动会警告）。不设置时行为与不启用完全一致 |
| `WX_MP_APPID` | 微信小程序 AppID（`wx36cf3bfafff4d03c`）。与下一项缺一，微信相关接口返回 `503 WECHAT_UNAVAILABLE` |
| `WX_MP_SECRET` | 小程序 AppSecret。只放服务器 env，不进代码、测试或日志 |
| `LIFE_VISION_MODEL` | 拍照识别用的视觉模型 id。不设置 = `/v1/life/recognize` 整体关闭（`503 RECOGNIZE_UNAVAILABLE`） |
| `LIFE_VISION_API_KEY` | 视觉模型 key；不设置时回落到 `MODEL_API_KEY` |
| `LIFE_VISION_BASE_URL` | 可选。默认与文本供应商同一个 base URL（`https://api.commandcode.ai/provider/v1`） |

缺少邮件配置时注册、重发、找回密码会明确失败，不伪造发送成功。缺少模型配置时返回错误事件，客户端退回本机统计。登录不会自动同步账本。

## 客户端连接

Android 构建时设置公开地址（不是密钥）：

```sh
./gradlew :app:assembleDebug -Pxiaopiao.apiBaseUrl=https://<approved-api-host>
```

Release 仅使用 HTTPS。Android 会话令牌和本机会话历史分别用 Android Keystore 加密。Web 使用 HttpOnly、SameSite=Strict Cookie，不在 localStorage 保存会话令牌。

Web 是 hash 路由，登录页是 `#/login`；开发期任意 loopback 端口（`127.0.0.1:*` / `localhost:*`）的 Origin 都接受，生产仍只接受 `APP_ORIGIN` 这一个 HTTPS origin。

Web 默认访问同源 `/v1`；需要独立同站域名时，可在构建环境设置公开的 `VITE_API_BASE_URL`，并将 `APP_ORIGIN` 设置为实际 Web origin。跨站 QMuse 默认域名与独立 API 的 Cookie 不兼容；须使用同站自定义域名 / 同源网关，或另行实现正式的跨站账号交换协议，不能放宽 Cookie 冒充已联通。现有 QMuse 云服务账本仍使用原有身份；自建账号的账本迁移、冲突解决与同步尚未实现。

## 生产部署（本地准备，未自动执行）

1. 核对目标主机、现有端口/服务、磁盘、域名与邮件发信能力，获得批准后操作。
2. 将密钥通过私密通道写入主机 `/etc/xiaopiao/server.env`，权限 `600`；不要把本机 shell 文件或凭据目录整体上传。
3. 构建 Web：`npm run check --prefix web`、`npm run build --prefix web`。独立部署 Web 需验收 QMuse runtime 注入；不要把独立部署等同于比赛发布。
4. 从 `server/deploy` 执行 `APP_DOMAIN=<approved-domain> docker compose up -d --build`。仅 gateway 暴露 80/443，API 无主机端口映射，SSE 关闭代理缓冲。
5. 验收 HTTPS、注册、真实投递、验证、重置、注销、模型流式、停止和断网降级后才算上线。

运行态为单机 SQLite：适用于当前单实例；多副本前必须迁移共享数据库与限流。邮件 API 由 AgentMail 提供，已用发信箱自身完成实际验证码收取与注册验证；外部邮箱的投递仍需上线验收。

## 代码结构

| 文件 | 职责 |
|---|---|
| `src/index.mjs` | 读取环境变量、装配、监听 |
| `src/app.mjs` | 认证、限流、SSE、continuation |
| `src/agent.mjs` | 一轮对话：选模型 → 调供应商 → 解析流 → 有界工具循环 |
| `src/router.mjs` | 免费模型智能路由：延迟/失败/用量排序、分级冷却、能力标记 |
| `src/providers.mjs` | 供应商描述符（端点、鉴权头、工具能力、免费模型白名单） |
| `src/prompt.mjs` | system prompt 单一来源（所有供应商共用） |
| `src/sanitize.mjs` | 丢掉模型自带的工具协议标记，含跨 chunk 半截标签 |
| `src/tools.mjs` | 工具目录（schema、中文步骤文案）、`draft` / `plan_steps` 的格式校验 |
| `src/wechat.mjs` | 小程序 `jscode2session`、`access_token` 缓存、`getUnlimited`，以及绑定票据与解绑规则 |
| `src/life.mjs` | 拍照识别的图片校验（魔术字节、像素尺寸）、视觉模型调用与输出白名单 |

### 新增一个模型供应商

1. 在 `src/providers.mjs` 复制 `commandcode` 的形状：`id`、`baseUrl`、`auth`、`headers`、`supportsTools`、`models`（只放免费/已批准模型）。
2. 需要独立密钥时加 `keyEnv`（如 `EXAMPLE_KEY`），否则回落到 `MODEL_API_KEY`。
3. 部署时设置 `MODEL_PROVIDER=<id>`，并先跑 `npm test`：`test/provider.test.mjs` 会检查描述符形状与免费模型约束。
4. 兼容 OpenAI `/models` + `/chat/completions` 流式的供应商不需要改流式解析；不兼容的再扩展 `agent.mjs`，不要复制第二份循环。

## 数据与 Agent 边界

- 工具注册表只有一个：`src/tools.mjs`。下面的表、模型看到的 schema、服务端的中文步骤文案、提示词里的工具清单与 draft kind 都从这里生成；返回形状见 `docs/API-AGENT.md`。
- 数据与视图工具在手机 / 浏览器本机执行，服务端只声明 schema。`node scripts/tools.mjs --write` 重写这张表，测试会检查它与注册表一致。

<!-- tools:begin -->
| 工具 | 类型 | 用途 |
|---|---|---|
| `summarize_period` | data | 一个周期的总额/笔数，按天或按分类分组；金额是分 |
| `find_peak` | data | 某周期最大的一天、一类或一家商户；金额是分 |
| `query_transactions` | data | 按时间查最多 20 条结构化记录；不含备注原文、卡包或截图 |
| `read_circle` | data | 只读明确授权共享的圈子数据；没授权就是「未共享」 |
| `read_budget` | data | 各周期与各分类的额度、已用、剩余；金额是分 |
| `summarize_range` | data | 任意起止时间的收支，按天/周/月/分类分组；金额是分 |
| `read_subscriptions` | data | 订阅侦探的结果：商户、金额、间隔、次数；金额是分 |
| `read_weekly_report` | data | 本地生成的周报与头衔 |
| `get_life_days` | data | 按天的摄入/消耗 kcal、五类食物 kcal、蛋白脂肪碳水克数、餐次时间、运动分钟与类型 |
| `get_life_top_foods` | data | 常吃的食物与次数 |
| `get_meal_spend` | data | 餐饮支出与饮食记录的关联：外卖/堂食金额（分）、单餐均价 |
| `list_categories` | data | 本机分类清单，用来对应用户说的分类名 |
| `show_view` | view | 用取数结果渲染一个界面视图 |
| `plan_steps` | plan | 多步查询前先写 1–6 步计划 |

起草 kind：record（金额分、方向、可选分类/备注/时间）、budget（周期与额度分，可选分类）、reminder（触发时间 epoch 毫秒、文案 1–120）、meal（餐次、时间、items[{name,qty,unit}]，可选 price_cents）、activity（名称、时间，minutes 或 sets[{reps,kg}] 至少一个）

视图 kind：`spend_summary`（支出合计）、`category_split`（分类占比）、`trend`（按天走势）、`entries`（最近明细）、`peak`（最大一笔）、`weekly_report`（周报）、`subscriptions`（规律扣款）、`draft_list`（待确认草稿）

金额一律整数分（cents）；生活类汇总单位是 kcal，克数用 g。数据和视图工具在设备本机执行，服务端只声明 schema。
<!-- tools:end -->
- 最多八轮、每次请求最多六个工具，整轮 150 s 上限；到上限用已有结果收尾（合成视图或一句说明 + `done.truncated`），不空着结束。
- 「先计划再逐步查」：模型可调服务端私有工具 `plan_steps`，服务端只发 `plan` / `step` 事件，**不下发该工具**，旧客户端不受影响。
- 不提供模型写入工具。要记一笔 / 设预算 / 设提醒 / 记一顿 / 记运动时只能 `draft` 起草：服务端校验字段后推给客户端，用户本机确认才生效。`meal` / `activity` 里没有热量字段——模型给 `kcal` 会被整条拒绝，热量由 App 本机知识库算。自动入账是用户预先打开的本机偏好，可撤销；CSV 保留确认。
- 只传白名单结构化字段，不传账单原文、CSV、图片、通知日志、备注原文或卡包。数值单位为整数分；生活记录汇总单位是 kcal。
- 回答里的每个数字都必须能追溯到本轮工具结果或用户自己说的话（分→元、取整、两者之间的和/差/积算在内），失败的工具结果不算来源；命中即换模型重试。越界内容（推荐吃什么、减重计划、医疗口气）同样在展示前丢弃，见 `docs/API-AGENT.md` §8。
- 展示实际步骤摘要，不展示模型内部 `reasoning_content`。未知工具、错误参数、超时与截断均失败关闭。
- 免费模型白名单按 `/models` 检查可用性，`/models` 失败时退回静态免费白名单；已输出内容后不拼接另一个模型的回答。
- 路由（`src/router.mjs`）按实测首 token 延迟、连续失败和最近十分钟用量排序，失败分级冷却：429 从 2 分钟起翻倍（上限 30 分钟）、超时/5xx 从 20 秒起、空回答 2 分钟、不合规输出 10 分钟；全部冷却时仍试最早恢复的一个。首 token 预算按实测延迟自适应（9–20 秒），第一次超时只校准预算不冷却。
- 能力标记不是故障：要求回传 `reasoning_content` 才能续跑工具结果的模型，只用于第一轮（我们不保存模型思维过程）；只在无工具时空回答的模型只在有工具时使用。
- 服务端 continuation 保存最多两分钟并绑定当前用户；完成不保留完整对话。API 不记录请求内容、密码、验证码、邮箱正文或授权头。

## 备份与恢复

先停止业务写入或使用 SQLite online backup API：在持有数据库卷的受信环境运行 `DB_PATH=<source> node deploy/backup.mjs <destination>`。备份文件也含敏感数据，应加密另存；只复制主 `.sqlite` 文件可能漏掉 WAL。恢复到隔离目录后做 `PRAGMA integrity_check` 和账号/会话 smoke test；未做 restore test 时不能声称已具备可恢复备份。

## API

`POST /v1/auth/register|login|verify|resend|forgot|reset|logout`；`GET /v1/auth/me`。注册邮箱、用户名、15–128 字符密码。验证码10分钟有效、最多5次尝试；重设密码立即撤销该账号全部会话。原生请求以 `client: native` 获取不透明 bearer；Web 只获取 HttpOnly Cookie。

`POST /v1/auth/password`（`{currentPassword,newPassword}`，已登录即可、不要邮箱验证码）：改密成功后撤销其它会话与未用完的重置码，当前会话保留。新密码与注册/重设同一条强度规则（8–128，拒常见词/连号/重复/账号名），拒绝为 `400 PASSWORD_WEAK`。详见 `docs/API-ACCOUNT.md` 的密码服务加固一节。

`POST /v1/chat`：首次传 `{messages:[{role,content}],timezone}`；收到 `tool_calls` 后本机执行，携带 `{runId,results:[{id,data}]}` 继续。SSE 类型为 `status / plan / step / draft / token / view / tool_calls / done / error`，`done` 可能带 `truncated:true`。文字只在整段校验（数字溯源 + 越界检查）通过后用一个 `token` 事件下发，进度事件仍实时。关闭连接会中断上游。完整工具表、事件字段与兼容说明见 `docs/API-AGENT.md`。

`POST /v1/auth/wechat`（小程序 `wx.login` 的 code 换会话；没绑定就建一个只有微信的账号）、`POST /v1/auth/wechat/bind-ticket` + `GET .../bind-ticket/:id`（App/Web 生成与轮询 20 字符一次性票据和小程序码）、`POST /v1/auth/wechat/bind`（小程序扫码**登记**，202，不绑定）、`POST /v1/auth/wechat/bind-status`（小程序只读状态，票据在 body）、`POST .../bind-ticket/:id/confirm|reject`（App 持有者确认才写入 openid）、`DELETE /v1/auth/wechat`（解绑，要求账号还有邮箱和密码）、`POST /v1/auth/email-attach[/confirm]`（给微信账号挂邮箱）。详见 `docs/API-ACCOUNT.md` 的微信一节；需要 `WX_MP_APPID` / `WX_MP_SECRET`。

`POST /v1/life/recognize`（`{image:"<base64>"}`，JPEG/WebP、长边 ≤512px、≤200 KB；返回 `{items:[{name,portion_text,grams_estimate,confidence}]}`，最多 6 项，**不返回热量**）。每账号 6 次/分钟、60 次/天。图片不落盘、不记日志。详见 `docs/API-LIFE.md`；需要 `LIFE_VISION_MODEL`。

`POST /v1/ledger/push`（`{deviceId,txns,categories}`，≤500 条，LWW）、`GET /v1/ledger/changes?since=&limit=`（含墓碑）。只收结构化字段，账单原文一律拒收；鉴权同上，且要求已验证邮箱。完整字段、错误码与客户端流程见 `docs/API-SYNC.md`。

`POST /v1/feedback`（`{kind,text,appVersion,device}`，登录可选，body ≤16 KB，每发送者 10 次/小时 + 全局 300 次/小时）。正文不写日志、没有读取接口，只有服务器上的 `node scripts/feedback.mjs [--since=<ms>] [--limit=50]` 能读；详见 `docs/API-ACCOUNT.md` 的 Feedback 一节。
