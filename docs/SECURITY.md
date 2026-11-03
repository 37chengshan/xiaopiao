# 今日小票后端安全检查与防御

2026-10-30，工作分支 `deploy/huawei`。tt 已在本会话明确批准，提交 `89fcf19` 的 API、systemd 与专用 nginx 已上线，生产 release 为 **`e5873e0ee535`**，前一 release `f84e8fc06f26` 保留。邮件 relay 保持原状态。本报告不是第三方渗透测试，不表示所有平台权限已经验收。

## 服务与审计范围

| 服务 | 已确认状态 | 本轮处理 |
|---|---|---|
| ECS nginx 8443 | Cloudflare 回源、专用主机名、Origin CA TLS；公网 health=200 | 按真实客户端 IP 的频率/连接预算，上传/发送超时 |
| Node API 127.0.0.1:8787 | Cookie/native Bearer、精确 Origin、账本/圈子用户归属、Agent | 统一资源防御与解析、上游边界修复 |
| SQLite | 参数化业务查询、WAL、owner 作用域、在线备份 | 限流键容量、过期索引、会话容量 |
| 邮件 relay Worker | 精确 route、IP+secret、固定 AgentMail 目的地；日志/preview/workers.dev 关闭 | 保留 Worker，API 新增邮件预算和发送互斥 |
| QMuse 云服务 | 此 checkout 声明三张表，无自定义云函数；Runtime facade 设置 owner ACL；产物校验通过 | 实际表权限无法从仓库证明，列为高优先级未决项 |
| SSH/Tailscale/华为云管理 agent | 监听和运行单元已只读盘点 | 保留现状，未改其它服务 |

源码审查覆盖 `server/src`、`server/deploy`、`server/test` 以及 Web 云服务声明和访问 facade。本 checkout 无服务端第三方 npm 依赖。没有扫描其它域名、读取用户账单或在生产执行攻击流量。

## 已上线策略

| 控制 | 阈值和行为 |
|---|---|
| API 活跃请求 | 全实例 64、同 IP 8；超出 503 + Retry-After=5 |
| 认证任务 | 全实例 3；读 body 前占位；断开连接仍需等实际任务结束才释放 |
| 频率 | 同 IP 300/min、全实例 3000/min；保留认证、账号、账本、圈子专用限流 |
| 限流状态 | 最多 20,000 keys；耗尽时失败关闭，health 可读；增加过期索引 |
| JSON | 普通 96,000 bytes、ledger 512,000 bytes；最多读取 15s；深度 24、节点 10,000；拒绝错误 MIME、压缩编码、非法 UTF-8 |
| 未登录 ledger/chat | 读 body 前拒绝 |
| 邮件 | 单账号/地址 4/h、全实例 100/h；同账号同目的互斥；上游失败仅删除对应 code digest |
| 会话 | 单账号最多 10；超出淘汰最早到期者 |
| 模型 | 全实例 4 个活跃请求、200 次 chat 请求/h（含工具续轮）；保留每用户 40/h 和单活跃请求；整次最多 120s |
| 模型上游 | 禁止重定向；discovery 512,000 bytes、错误体 4096 bytes；SSE 单帧 256,000 chars、总流 2,000,000 bytes；工具 ID/名称有界 |
| 流式输出 | 开始输出后仍执行截止时间；慢客户端写缓冲满即中止；整次超时发送明确错误事件 |
| HTTP | Node 128 sockets、16KiB headers、64 headers、每 socket 最多 100 requests；nginx 10 req/s，burst=60，同 IP 12 连接 |
| 进程 | 保留非 root、CPU/内存/文件系统隔离；新增私有设备、内核/控制组保护、空 capability set、TasksMax=64、NOFILE=1024、禁 core dump |
| 观测 | 生产每分钟最多一条累计 security_summary：拒绝类型计数、活跃数量；无 IP、账号、URL、正文、会话、验证码、密钥 |

资源预算触发后主动拒绝请求，不能保证分布式攻击下仍可用。阈值针对小规格单实例；共用 NAT 可能触发同 IP 预算，尚未经过线上负载标定。聚合摘要使用原有 systemd journal，无新增管理入口、外部埋点或通知。网关错误日志关闭，诊断使用应用聚合摘要；不要开启正文/debug 日志。

body 按 bytes 累计，收齐后统一 UTF-8 解码，修复中文跨 chunk 被破坏的问题；URL 解析纳入异常边界，畸形路径返回 400。既有跨账号账本、圈子隔离、验证码单次消费、会话失效、可信代理和敏感字段拒绝用例继续运行。

## 2026-11-01 新增：密码服务与认证锁定（代码已就位，未部署）

下面五项都在代码里，测试全绿，但**尚未发布**，生产仍是 `e5873e0ee535`。上线方式与回滚同 `docs/DEPLOY.md`；新表 `auth_guard` 不被旧代码读取，回滚代码不需要恢复数据库。

| 控制 | 阈值和行为 |
|---|---|
| 新密码强度 | 8–128 且非常见词、非单字符重复、非纯连号、不等于用户名/邮箱本地部分；只作用于注册/重设/改密，登录不做筛查；拒绝为 400 `PASSWORD_WEAK` + `field` |
| 哈希 | `scrypt1$17$8$1$…`，成本写在串内；旧 `<salt>:<hex>` 继续可验证并在下次登录时就地升级；无法解析的行按 401 处理，不再 500 |
| 派生并发 | 2 个 scrypt 槽 + 8 个排队位，超出 503 `SERVER_BUSY`；单次派生 128 MiB，最坏 256 MiB（单元 `MemoryMax=768M`） |
| 账号锁定 | 15 分钟内 5 次失败 → 60 秒，翻倍，封顶 15 分钟；成功即清零；429 + `Retry-After` |
| 来源锁定 | 1 小时内 20 次失败 → 1 小时；覆盖 login / email-request / password 三条校验密码的路径 |
| 观测 | `security_summary` 增加 `guard: { tracked, locked, failures }` 三个计数，仍不含 IP、账号、key |

实测：8 个并发注册请求下进程峰值 RSS 319 MB（此前 3 路并发即可达 384 MiB 的派生内存）；其中 5 个由既有认证工作池（上限 3）以 503 拒绝，未放大的闸门没有增加任何 4xx/5xx 面。可选 `PASSWORD_PEPPER` 默认关闭，设置后新哈希带 `scrypt1p$` 前缀；缺失变量且库里有 peppered 行时只在启动时打一行警告，不阻断启动。

## 2026-11-03 新增：微信身份、绑定票据、拍照识别（代码已就位，未部署）

生产仍为旧 release。三项新能力都在代码里并有回归测试，但没有部署，也没有用真实 AppSecret / 视觉模型验收。阈值是本项目的工程选择。

### 威胁点与对策

| 威胁 | 对策 | 代码位置 |
|---|---|---|
| 绑定票据被重放 | 票据 20 字符随机（15 字节 base64url）、只存 sha256 摘要；绑定成功即 `status='bound'`，重放命中同一条即 `400`；「挂 openid」与「消费票据」在同一个 `BEGIN IMMEDIATE` 事务里，两次并发确认只有一次 `changes=1` | `wechat.mjs` `confirm` |
| 票据被第三方截获后轮询 | 轮询与确认都要求**生成票据的账号**在线，别人的 401/400 与「票据不存在」同码同文案；票据本身不含账号 id、不含 openid；小程序侧的 `bind-status` 只返回状态枚举 | `app.mjs` `ticketPoll`、`wechat.mjs` `scanStatus` |
| 票据进日志 | 小程序查状态改为 `POST /v1/auth/wechat/bind-status`，票据在 body 里；URL 里只有固定路径。库里只存 `sha256` 摘要，限流键也是 `hash(ticket)`，`security_summary` 只有计数（测试断言票据不出现在 limits 键与快照里） | `app.mjs`、`wechat.mjs` |
| 扫码方冒名 | 昵称是**扫码方自报**的：去控制字符与零宽/双向字符、≤32，只作为文字显示在确认弹窗，不写进账号；服务端**不再接收头像 URL**（App 加载任意地址会把用户 IP 泄漏给第三方） | `wechat.mjs` `sanitizeNickname`/`status` |
| 票据被第三方拿去绑定自己的微信 | **扫码不再等于绑定**：小程序扫码只把票据置为 `scanned` 并带上昵称/头像，必须由已登录的 App 持有者点确认才写入 openid；App 不确认或超时则永不绑定，拒绝直接把票据作废 | `wechat.mjs` `claim`/`confirm`/`reject` |
| 票据长度导致绑定整体不可用 | 微信 `getwxacodeunlimit` 的 `scene` 上限 32 字符，上一版 43 字符票据会 100% 失败；票据改为 20 字符（`b:` + 20 = 22），`bindCode` 在发请求前断言 `scene.length <= 32`，测试断言真实长度而不是只跑 mock | `wechat.mjs` `LIMITS`/`bindCode`、`test/bounded.test.mjs` |
| 上游响应无界读取 | 模型、微信、视觉三处统一走 `src/bounded.mjs`：先看 `Content-Length`，再按字节上限边读边算，任何路径都 `cancel()` 释放 socket；`!response.ok` 也取消 body | `bounded.mjs` |
| 用 email-attach 探测邮箱是否注册 | 先扣限流（每账号 5 次/小时、每地址 4 次/小时）再查占用，连续探测在第 5 次即 `429`；文案与占用结果不增加额外信息量 | `app.mjs` `email-attach` |
| openid 枚举 | `session_key` 不落库不下发；`/v1/auth/wechat` 只接受 `wx.login` 的一次性 code（微信侧也校验），不提供任何「查某个 openid 是否已注册」的接口；绑定冲突只返回 `WECHAT_IN_USE`/`WECHAT_ALREADY_BOUND`，不回显对方账号 id、邮箱或用户名 | `wechat.mjs`、`app.mjs` |
| session_key 泄漏 | 只在 `openidFor()` 内解析 `openid`，其余字段（含 `session_key`、`unionid`）读出即丢；错误体只做 `errcode` 分类，原文不转发、不写日志；测试用 `session_key` 标记断言它不出现在任何响应里 | `wechat.mjs` `openidFor` |
| 微信接口不可用时的降级 | 缺少 `WX_MP_APPID`/`WX_MP_SECRET` 时直接 `503 WECHAT_UNAVAILABLE`，不会走假成功；微信上游超时 8 s、错误体上限 64 KB、`redirect: 'error'` | `wechat.mjs` |
| 解绑把账号锁死 | 解绑前要求「有邮箱 **且** 有密码」，否则 `409 WECHAT_LAST_LOGIN`；微信账号第一次设密码要求先挂邮箱（否则忘了无法找回） | `app.mjs` 解绑与 `password` 分支 |
| 邮箱被两个账号争用（合并提权） | 拒绝而非合并：`409 EMAIL_BOUND_ELSEWHERE`，提示去拥有邮箱的一端扫码绑定。合并会牵动账本/圈子/会话，不是一次邮件验证码能授权的操作 | `app.mjs` `email-attach` |
| 同一 openid 被两个账号同时认领 | 确认前先查占用，写入被唯一索引拦下时映射为 `409 WECHAT_IN_USE`（不是 500），事务回滚后票据状态不变 | `wechat.mjs` `confirm` |
| 老库迁移期间的数据丢失 | `users` 重建时 `PRAGMA foreign_keys=OFF`（否则 `DROP TABLE` 会级联删除所有子表行），事务内完成复制/替换/重命名，结束后重新开启并跑 `PRAGMA foreign_key_check`；有老库夹具测试覆盖账本与会话行 | `store.mjs` `migrateWeChatUsers` |
| 拍照识别被当作免费模型代理 | 每账号 6 次/分钟、60 次/天，每来源 30 次/分钟，全实例 2 个并发视觉调用（独立池，不挤占 chat），另有既有的同 IP 300 次/分钟 | `app.mjs`、`defense.mjs` |
| 图片成为持久化隐私风险 | 不落盘、不入库、不写日志、不回显；请求体上限 280 KB 只对这一条路由；解码后再校验 200 KB 与长边 512 px；函数返回即丢弃 buffer | `life.mjs` |
| 伪造图片骗过校验 | 类型只看魔术字节（只收 JPEG/WebP，PNG/GIF/文本一律 400）；像素尺寸从 SOF/VP8X/VP8L 头解析，读不出即 400，避免小文件解码成大图 | `life.mjs` `sniff`/`dimensions` |
| 模型输出注入客户端 | 视觉模型输出走白名单（`name/portion_text/grams_estimate/confidence`，上限 40/24/5000/1），热量等额外字段直接丢弃；认不出就是 `items: []` + 200，不会 500 | `life.mjs` `parseItems` |
| 越界建议/编造数字从长回答漏出 | 文字先整段缓冲，对**完整回复**做数字溯源与越界检查后才一次性下发；被拒的回答一个字都不发，只换模型。进度事件仍实时 | `agent.mjs` `attempt` |

### 与既有控制的关系

未改动：认证工作池、scrypt 派生闸门、`guard` 账号/来源锁定、邮件限额与互斥、限流键容量、SSE 截止时间、模型白名单与冷却。新增的 `wechat_bind_tickets` 表按既有清理器（`expires`）每分钟清过期行；`vision` 池加入 `security_summary` 的 `pools` 计数，仍是纯整数。

### 仍需在上线前后确认

1. 真实 AppSecret 写入 `/etc/xiaopiao/server.env`（`600`）后，`jscode2session` 与 `getwxacodeunlimit` 各跑一次真实调用；确认 `access_token` 缓存命中、错误码映射正确。当前没有在生产调用过微信接口。
2. 小程序码 `page=pages/bind/bind` 必须是**已发布版本里存在的页面**，否则 `getUnlimited` 会报 41030；需要小程序侧先有这个页面。用真实 AppID 跑一次 `getwxacodeunlimit` 确认 22 字符的 `scene` 被接受（本地只验证了长度约束）。
3. 视觉模型：选定 `LIFE_VISION_MODEL` 并确认它对中文菜品名的输出稳定；未设置时接口整体 503，属于预期状态。
4. 老库迁移要在生产库的副本上跑一次并比对行数（`users`/`sessions`/`ledger_txns`/`circle_members`），再在真实库执行；在线备份按 `DEPLOY.md` 保留。

## 未决项

1. **QMuse 实际表权限：高优先级。** `transactions`、`merchant_revenue`、`receipt_prefs` 含个人数据。需在 QMuse 云服务「数据表权限设置」核对 READ/EDIT/DELETE 仅创建者，SUBMIT 仅业务允许的身份（包括明确允许的访客）。平台文档的新表默认 READ=ALL；不能假定线上仍为默认值，也不能用 ownerId 查询证明隔离。随后用两个独立身份验证：各自读写自己的行，交叉读/改/删失败，篡改 ownerId 不能获得他人行。本轮没有修改平台权限，未宣称隔离通过。
2. **备份仍与 ECS 同盘。** 备份重开和 quick_check 通过，不构成异机灾备。只读盘点磁盘使用约 13%。异机/对象存储资源需另行选择和批准。
3. **Cloudflare 全区 SSL 仍为 full。** 原站证书存在，前轮已查证书链；full 不等同严格验证原站证书。未改全区或别的域名，主机名级 strict 可另行准备。
4. 应用配额不能替代网络层 DDoS 防护；没有购买 WAF、扩大云权限、改安全组、安装新 daemon 或改变 SSH/Tailscale。主机与 Node/nginx 补丁/CVE 核验不属于本轮已完成结论。

## 验证与上线

增加 13 项防御回归，与既有测试合计 81 项：UTF-8 分块、chunked 限额、恶意 URL、无鉴权大上传、慢 body 超时、并发拒绝和恢复、断开不能绕过认证配额、邮件总量、限流状态容量、模型并发/超时、上游恶意流、圈子默认 body 限额和会话上限。最终执行结果见 deployment-work/progress.md。QMuse 产物校验通过。

上线前 nginx 经目标机同版本 `nginx -t` 校验通过，unit 经 `systemd-analyze verify` 通过。第一次 stdin 传 nginx 配置因二次读取而失败，改用受限临时文件后完整校验通过。取得批准后已替换生产配置，API restart 和 nginx reload 完成。

本次线上验收：health=200；精确 Origin=200，外源=403；无凭据 ledger/chat=401；非法 JSON/MIME=400；未授权 relay=403。复用自有邮箱 QA 账号完成真实收码/重设密码、native 登录、真实免费模型 SSE 回答、ledger push/changes、圈子 create/list/leave、logout 和退出后 401。没有向外部收件人发信。QA 活跃会话、未删除测试交易、残留测试圈子均为 0。

第一次 forgot 请求返回 MAIL_UNAVAILABLE；未更改代码或放宽防护。同一服务 mount namespace 内复用运行配置，固定 relay 发送到自有 inbox 返回 200，随后完整链路复验通过；首次失败的具体原因未证实。直接在主机原 namespace 的诊断 fetch 曾超时，不能代表应用 namespace 的状态；没有修改主机 DNS。

线上 29 个 runtime 文件与批准 archive 逐字节匹配。私密 env 和 relay key 的部署前后校验一致。新增 systemd 隔离项通过 `systemctl show` 确认；API NRestarts=0，API/nginx/backup timer 均 enabled/active。已看到仅含计数的 security_summary，完成验收后 request/auth/chat 活跃数量为 0。部署前后 DB 备份均 quick_check=ok；备份仍在同盘。

source archive SHA256：`e5873e0ee5357d95675bdb195aacc414fb7f77cb2ae2bc0888862f15a211bed5`。配置回滚副本位于 `/var/backups/xiaopiao/config-before-e5873e0ee535/`，部署前在线 DB 备份 `/var/lib/xiaopiao/backups/pre-e5873e0ee535.sqlite`，验收后备份 `/var/lib/xiaopiao/backups/api-20261030T071810-5084.sqlite`。

本次已按以下流程上线；后续更新仍需对应批准：

1. 按 DEPLOY.md 准备 source-only archive、SHA256 和 release ID，备份 DB 与专用 nginx/unit。
2. `server/deploy/update-private.sh` 更新 API/unit：在线备份、原子 symlink、restart，health 失败恢复旧代码/unit；保留私密 env 和 resolver。
3. 单独安装候选 nginx，先 `nginx -t` 再 reload；失败恢复专用配置。代码更新脚本不会自动安装 nginx 配置。
4. 顺序验收公网 health、Origin、native 登录/邮件/ledger/logout、错误 JSON 和无凭据拒绝；检查聚合摘要与服务状态。重启不与账号验收重叠，生产不做洪泛测试，仅向自有 AgentMail inbox 发信。
5. 观察误限流与延迟，必要时恢复前一 release 和旧 unit/nginx。新增过期索引向后兼容，普通回滚不恢复旧 DB、不删除新账目。

实现依据：[Node HTTP 超时与写缓冲](https://nodejs.org/docs/latest-v24.x/api/http.html)、[nginx request limiting](https://nginx.org/en/docs/http/ngx_http_limit_req_module.html)。阈值是本项目的工程选择，不是官方安全保证。
