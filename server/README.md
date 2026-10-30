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

### 新增一个模型供应商

1. 在 `src/providers.mjs` 复制 `commandcode` 的形状：`id`、`baseUrl`、`auth`、`headers`、`supportsTools`、`models`（只放免费/已批准模型）。
2. 需要独立密钥时加 `keyEnv`（如 `EXAMPLE_KEY`），否则回落到 `MODEL_API_KEY`。
3. 部署时设置 `MODEL_PROVIDER=<id>`，并先跑 `npm test`：`test/provider.test.mjs` 会检查描述符形状与免费模型约束。
4. 兼容 OpenAI `/models` + `/chat/completions` 流式的供应商不需要改流式解析；不兼容的再扩展 `agent.mjs`，不要复制第二份循环。

## 数据与 Agent 边界

- 仅四种只读工具：`summarize_period`、`find_peak`、`query_transactions`、`read_circle`，工具在手机 / 浏览器本机执行。
- 最多四轮，最多四个工具 / 轮；结果大小、记录数、上下文大小和执行时限均有限制。
- 不提供模型写入工具。自动入账是用户预先打开的本机偏好，使用本机解析结果，可撤销；CSV 保留确认。
- 只传白名单结构化字段，不传账单原文、CSV、图片、通知日志、备注或卡号。数值单位为整数分。
- 展示实际步骤摘要，不展示模型内部 `reasoning_content`。未知工具、错误参数、超时与截断均失败关闭。
- 免费模型白名单按 `/models` 检查可用性，`/models` 失败时退回静态免费白名单；已输出内容后不拼接另一个模型的回答。
- 路由（`src/router.mjs`）按实测首 token 延迟、连续失败和最近十分钟用量排序，失败分级冷却：429 从 2 分钟起翻倍（上限 30 分钟）、超时/5xx 从 20 秒起、空回答 2 分钟、不合规输出 10 分钟；全部冷却时仍试最早恢复的一个。首 token 预算按实测延迟自适应（9–20 秒），第一次超时只校准预算不冷却。
- 能力标记不是故障：要求回传 `reasoning_content` 才能续跑工具结果的模型，只用于第一轮（我们不保存模型思维过程）；只在无工具时空回答的模型只在有工具时使用。
- 服务端 continuation 保存最多两分钟并绑定当前用户；完成不保留完整对话。API 不记录请求内容、密码、验证码、邮箱正文或授权头。

## 备份与恢复

先停止业务写入或使用 SQLite online backup API：在持有数据库卷的受信环境运行 `DB_PATH=<source> node deploy/backup.mjs <destination>`。备份文件也含敏感数据，应加密另存；只复制主 `.sqlite` 文件可能漏掉 WAL。恢复到隔离目录后做 `PRAGMA integrity_check` 和账号/会话 smoke test；未做 restore test 时不能声称已具备可恢复备份。

## API

`POST /v1/auth/register|login|verify|resend|forgot|reset|logout`；`GET /v1/auth/me`。注册邮箱、用户名、15–128 字符密码。验证码10分钟有效、最多5次尝试；重设密码立即撤销该账号全部会话。原生请求以 `client: native` 获取不透明 bearer；Web 只获取 HttpOnly Cookie。

`POST /v1/chat`：首次传 `{messages:[{role,content}],timezone}`；收到 `tool_calls` 后本机执行，携带 `{runId,results:[{id,data}]}` 继续。SSE 类型为 `status / token / tool_calls / done / error`。关闭连接会中断上游。
