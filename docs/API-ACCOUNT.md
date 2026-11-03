# Android 账号修复配套接口（2026-10-30）

来源：tt 的注册错误、密码规则不一致、个人资料不可编辑反馈。基于已部署的防御版本开发；发布已获 tt 明确批准；当前生产 release 为 4f5d7f11725d，包含 8 位新密码规则与 HTML 邮件。

- `GET /v1/auth/policy`：公开返回 passwordMin=8、passwordMax=128、usernameMin=3、usernameMax=30、profileEditing=true。客户端据此展示提示与校验；旧服务缺少此接口时保留核实过的 8 字符规则，资料保存保持不可用。
- 注册/重设密码的非法邮箱、密码和用户名返回现有安全错误码，并附 `field`（email/password/username）。不返回提交值。客户端确认密码字段只在本机比较，不上传。
- `POST /v1/auth/profile`：已登录且已验证的账号更新 username；3–30 ASCII 字母、数字、下划线，以字母开头，规范化小写、唯一性检查。返回更新后的 user，账号 id 不变，账本归属不变。
- `POST /v1/auth/email-request`：email 和 currentPassword；必须已登录、已验证，校验当前密码及邮箱占用，再通过既有 relay 向新邮箱发送验证邮件。旧邮箱在确认前不变。
- `POST /v1/auth/email-confirm`：6 位 code；验证当前用户的待修改邮箱，最多 5 次尝试、10 分钟有效、单次消费；修改成功后清除待处理验证码并撤销其它会话，保留当前会话。错误尝试在事务外累计，不能通过回滚绕过上限。

新增 SQLite `email_changes` 表（user_id 外键级联删除、email、expires），原有表/账目不改；过期记录由既有清理器清除。沿用原认证并发、IP/账号/地址/全局邮件限额、同目的发信互斥、固定发信端点和脱敏观测。不修改 Worker、DNS、安全组或全区设置。

验收：89/89 服务端测试通过，npm check 通过；5 项新回归覆盖公开规则、字段定位、资料身份不变、邮箱重新认证和验证、撤销其它会话、验证码尝试上限。新增发信测试全部使用内存 mailbox，不向外部收件人发送邮件。生产发布后仍需真实账号验收，不能把本地测试等同上线。

发布/回滚：经 tt 针对此账号接口批准后，使用 DEPLOY.md 的 source-only archive 与 update-private.sh，先在线备份 DB，保留旧 release 和 unit。新增表为兼容性增量，回滚 API 不恢复旧 DB、不丢弃新账目。发布范围为同一 API 服务，不需要再次安装 nginx 配置。

补充登录兼容性：新密码仍遵守 15–128 字符；登录只验证已有密码的原值（非空且最多 128 字符），不以新密码长度拒绝历史短密码。邮箱/登录标识规范化大小写、全角字符与中文句号，密码不 trim、不规范化。回归包含历史 10 位密码退出后重登，以及中文句号邮箱。tt 提供的账号仅做存在性与验证状态只读检查，未尝试密码、改密或变更资料。

2026-10-30 最新裁决：tt 明确要求新密码至少 8 个字符，覆盖前次保留 15 位的决定。注册和重设密码为 8–128 位，登录不按新密码长度拒绝历史凭据。验证码邮件增加固定品牌 HTML 排版，同时保留 text 备用；relay 仅增可选 html 字段，上限及 IP/secret、固定目标、关闭日志等防御保持。真实收件箱收到含 HTML 的邮件后才标为邮件验收通过。

生产验收记录：policy.passwordMin=8；自有收件箱实际收到固定品牌 HTML 及纯文字备用内容；八位新密码 reset→native login 与中文句号邮箱匹配通过。自有 QA 账号还完成私有圈子的小票快照发布/读取/清理和退出。测试未改动 tt 私人账号。Worker 模块逐字节读回匹配，日志及 workers.dev/preview 继续关闭。

## Avatar（2026-11-01）

头像只有一个位置：用户对象里的 `avatar` 字段。凡是返回 `publicUser` 的接口都带它（`/v1/auth/me`、注册、登录、验证、资料修改、邮箱确认），圈子成员表（`GET /v1/circles/:id/members`）的每个人也带同一个字段。

```json
"avatar": null
"avatar": { "preset": "p3" }
"avatar": { "url": "/v1/avatars/<userId>?v=<updatedAtMillis>" }
```

- `preset` 是内置档位 `p1`…`p12`，客户端自己画，服务器只存一个 id。
- `url` 是照片，`v` 是写入时间戳：换一张头像就是换一个 URL，缓存不可能显示旧脸。URL 不带域名，客户端拼 `apiBase`。
- 两种形态互斥：上传 `preset` 会清掉已存的照片，上传照片会清掉 `preset`。没有第三态。

`PUT /v1/auth/avatar`（已登录 + 已验证邮箱；Bearer 或 cookie；每账号 30 次/小时限流，body 上限 384 KB 只对这一条路由生效，其它路由的限额不变）：

- `{ "preset": "p1" }`，必须匹配 `^p(1[0-2]|[1-9])$`，否则 `400 AVATAR_INVALID`。
- `{ "image": "<base64>" }`，标准 base64（不接受 `data:` 前缀）。服务器按**魔术字节**判定类型，只收 JPEG（`FF D8 FF`）、PNG、WebP；GIF、BMP、SVG、随机字节一律 `400 AVATAR_INVALID`，不采信客户端声明的 mime。解码后 > 256 KiB → `413 AVATAR_TOO_LARGE`（body 本身超限也是同一个码，客户端只需处理一种失败）。
- 成功 `200 { "user": publicUser }`，`avatar` 是刚写入的形态。

`DELETE /v1/auth/avatar` → 清掉照片与档位，返回 `{ "user": publicUser }`（`avatar: null`）。

`GET /v1/avatars/:userId`（已登录 + 已验证）：

- 只有**本人**或**与自己同属至少一个圈子的人**能取到，其它情况一律 `404 AVATAR_NOT_FOUND`，从不用 403——「这个人有没有头像」本身就是圈子内的私事。踢出/退圈后立刻失效。
- 没有照片（含只有 `preset`）也是 `404 AVATAR_NOT_FOUND`。
- 命中的响应是图片本体：`Content-Type` 用存下来的类型、`Cache-Control: private, max-age=86400`、`X-Content-Type-Options: nosniff`。没有 `Set-Cookie` 之类的其它副作用。

存储：新增 SQLite 表 `avatars(user_id PRIMARY KEY → users ON DELETE CASCADE, preset, mime, bytes BLOB, updated_at)`，与既有表一致的级联删除，不新建目录、不改其它表。

验收（2026-11-01）：`npm test --prefix server` 97/97 通过（新增 8 项覆盖档位设置/清除、JPEG/PNG/WebP 接收、GIF 与随机字节拒绝、恰好 256 KiB 接受与超限 413、本人/圈友可取和对陌生人 404、退圈即失效、成员表带头像、未验证 403），`npm run check` 通过；另用真实进程（`XP_DEV_MAIL=1`、临时 DB）跑通 注册→验证→设档位→传照片→取图（`image/png`、`private, max-age=86400`、`nosniff`）→超限 413→GIF 400。生产未部署，未触碰 tt 账号。

## Feedback 一键反馈给开发者（2026-11-01）

App 的「反馈给开发者」把用户**自己看过、自己删改过**的一小段文字发给开发者：设置页是一般反馈，识别失败的通知条目可带一条通知原文（`kind=capture_miss`）。发出去的全部内容在发送前就显示在弹窗里，点「发送」才上传。

`POST /v1/feedback`，**登录可选**：带有效 Bearer/cookie 就记在这个账号名下，否则匿名；带了一个失效 token 也按匿名收，**不返回 401**——App 收到 401 会清掉登录态，为一张过期 cookie 丢掉整条反馈不值得。

Body 是**严格白名单**，多一个键就是 `400 INVALID_INPUT`（客户端 bug，不是新字段）：

```json
{ "kind": "capture_miss" | "general", "text": "1..3000 字符（去空白后非空）",
  "appVersion": "≤32（可省略→存空串）", "device": "≤120（可省略→存空串）" }
```

- 成功 `201 { "ok": true, "id": "<uuid>" }`。id 只用来对日志，不回显任何提交内容。
- 失败只用既有码：`400 INVALID_INPUT`（kind 不认识、text 空或超长、appVersion/device 超长或不是字符串、未知键、body 不是 JSON 对象或超过 16 KB）、`429 RATE_LIMITED`（带 `Retry-After`）。
- body 上限 **16 KB**（`readJson` 在解析前按字节计，超限直接 400，不落到字符校验）。其它路由的限额不变。
- 限流：**每发送者 10 次/小时**（登录按账号 id，匿名按 ip_hash），**全局 300 次/小时**。与既有路由一致，被拒绝的请求也占配额。

存储：新增 SQLite 表

```
feedback(id TEXT PK, user_id TEXT NULL → users ON DELETE CASCADE,
         kind, text, app_version, device, created_at INTEGER, ip_hash TEXT)
```

`ip_hash = hash(客户端 IP)`，即会话用的那个 sha256 助手；**原始 IP 不入表**（既有 `limits` 表的 `global:<ip>` 键是旧行为，本接口的限流键同样只存 `feedback:<ip_hash>`）。删除账号时该账号的反馈随之一并删除，匿名行不受影响。

隐私边界：

- 反馈正文（可能含一条通知原文）**不写任何日志**，`console.log`/`security_summary` 都不碰；错误响应也不回显提交值。
- **没有 HTTP 读取接口**。开发者只在服务器上读：

```bash
DB_PATH=/var/lib/xiaopiao/api.sqlite node scripts/feedback.mjs [--since=<epochMs>] [--limit=50]
```

每行一个 JSON 对象（`id/userId/kind/text/appVersion/device/createdAt/ipHash`，最新在前），stdout 输出；`--limit` 上限 500，`DB_PATH` 指错会直接报错而不新建空库。`GET /v1/feedback` 仍是 404。

验收（2026-11-01）：`npm test --prefix server` 122/122 通过，新增 11 项覆盖匿名/登录接受、`user_id` 仅登录时写入、失效 token 按匿名收、kind/空文本/超长/未知键拒绝与边界（3000 字、32/120 字符）恰好通过、错误响应不回显正文、16 KB 上限、10 次/小时后 429 与 `Retry-After`、按账号而非 IP 计数、`ip_hash` 等于 `hash(127.0.0.1)` 且明文 IP 不出现在 feedback 行与 feedback 限流键、无 HTTP 读取、脚本读取排序与 `--since/--limit`；`npm run check` 通过。另用真实进程（`XP_DEV_MAIL=1`、`/tmp` 临时 DB）跑通 匿名 201 → 未知键/坏 kind/17 KB 各 400 → `GET` 404 → 真实注册+验证账号 201 且行内 `userId` 非空 → 过期 token 201 匿名 → 第 11 次 429 带 `Retry-After` → `scripts/feedback.mjs` 读懂全部行、`--since` 过滤、`--limit` 越界报错、`DB_PATH` 不存在不建库；服务端 stdout 只出现 dev mail 一行，没有任何反馈正文。生产未部署，未触碰 tt 账号。

## 密码服务加固与认证防御（2026-11-01）

代码已就位，**未部署**；生产仍为 2026-10-30 的 release。现有账号不受影响：强度规则只作用于**正在设置的**新密码，登录路径不做强度筛查，旧哈希原样可验证。

### 密码强度（只对新密码生效）

注册、重设密码、改密这三处的新密码必须同时满足：8–128 字符（下限沿用 2026-10-30 的裁决），且不在内置常见密码表（约 160 条，含 `password123`、`12345678`、`qwertyui`、`woaini520`、`xiaopiao` 等），不是同一字符重复（`aaaaaaaa`），不是纯数字或纯字母的严格连号（`01234567`、`abcdefgh`、`hgfedcba`），不等于用户名或邮箱本地部分（忽略大小写）。

不通过返回 `400 PASSWORD_WEAK`，带 `field`：`password`（注册/重设）或 `newPassword`（改密）。文案「密码太简单，换一个更难猜的（至少 8 位，别用常见词或账号名）」由客户端直接展示，无需映射新错误码。长度问题仍是 `400 INVALID_INPUT` + 同一个 `field`。`/v1/auth/policy` 的响应字段不变，客户端不必改。

### 哈希格式与自动升级

新哈希：`scrypt1$<logN>$<r>$<p>$<salt b64url>$<key b64url>`，当前参数 `17/8/1`（N=2^17、r=8，每次派生 128 MiB、约 0.3 秒）。带 pepper 时前缀为 `scrypt1p$`。参数写在哈希串里，所以以后提高成本不需要停机迁移：`verify` 按**行内**参数派生，登录成功后若该行格式或成本落后于当前设置，就地静默重写（重写失败不影响这次登录）。旧的 `<salt>:<hex>` 旧格式继续可验证，并在下次登录时自动升级。

无法解析的行（截断、空串、未知算法）按登录失败处理，返回 401 而不是 500：旧实现会在长度不等时抛错。

### 可选 pepper

`PASSWORD_PEPPER` 环境变量非空时，密码先做 `HMAC-SHA256(pepper, password)` 再进 scrypt；拿到数据库备份但没有该变量无法离线爆破。**默认关闭**，不设置时行为与今天完全一致。变体记录在哈希前缀里，所以「先上线后加 pepper」不会锁死已有账号；反过来，已经在写 `scrypt1p$` 行之后再移除该变量，那些账号将无法登录（只能离线重置）——因此启动时若发现库里有 peppered 行而变量缺失，只打一行警告、不阻断启动。

### 派生并发闸门

同时最多 2 个 scrypt 派生，另有 8 个排队位；超出直接 `503 SERVER_BUSY`（`Retry-After: 5`）。systemd 单元 `MemoryMax=768M`，此前 3 路并发（认证工作池上限）就已经吃掉 384 MiB；现在最坏 2×128 MiB。实测 8 个并发注册请求下进程峰值 RSS 319 MB，其中 5 个由既有认证工作池（上限 3）以 503 拒绝、3 个全部成功（第 3 个排队而非失败）。

### 失败计数与锁定（`guard`）

| 维度 | 阈值 | 锁定 | 说明 |
|---|---|---|---|
| 账号 `a:<userId>` | 15 分钟内 5 次失败 | 60 秒起，每次翻倍，上限 15 分钟 | 忘密码的人损失几十秒 |
| 来源 `i:<hash(ip)>` | 1 小时内 20 次失败 | 1 小时 | 撞库换账号也照样被打到 |

- 覆盖三条会校验密码的路径：`/v1/auth/login`、`/v1/auth/email-request`（当前密码校验）、`/v1/auth/password`。验证码相关路径仍由既有的「一码 5 次、10 分钟」上限约束。
- 未知标识符只累积来源键，所以探测不存在的账号也会计入来源预算；已知账号同时累积账号键与来源键。
- 一次成功即清掉两个键（含已累积的 strikes 与剩余锁定），误输几次不会长期叠加。
- 锁定生效时返回 `429 RATE_LIMITED` + `Retry-After`，且在派生之前就拒绝（一次行读取，不烧 128 MiB）。
- 已知取舍：知道用户名的人最多能把该账号锁 15 分钟。所以来源预算才是主防线，账号锁短且封顶。
- `security_summary` 新增 `guard: { tracked, locked, failures }` 三个聚合整数；不记录任何 key、账号或 IP。状态行在 24 小时后由既有清理器删除。

### 改密接口（不需要邮箱验证码）

`POST /v1/auth/password`，已登录即可（**不要求已验证邮箱**），body `{ "currentPassword": string, "newPassword": string }`：

- 校验当前密码 → 校验新密码强度 → 新旧不得相同（`400 PASSWORD_UNCHANGED`，`field: newPassword`）。
- 成功 `200 { "ok": true }`：撤销该账号其它所有会话（当前这条保留）、删除未用完的**重置**码（验证码与换邮箱码不动）。不发 `Set-Cookie`。
- 失败：未登录 `401`；当前密码错 `400 INVALID_CURRENT_PASSWORD`（`field: currentPassword`）；每账号 5 次/小时限流；多余请求体字段与其它 auth 路由一致地忽略。
- 设计原因：tt 要求「拿当前密码就能改，不再走邮件」。当前密码是最强的所有权证明，同时改密后立刻踢掉其它会话，泄露时能在几秒内止损。
- App 目前没有改密 UI，接口先就位；客户端下次迭代接入即可。

### 未改变的既有行为

登录不做强度筛查（历史短/弱密码照常登录）；登录的密码长度仍只做「非空且 ≤128」上限检查；同一账号最多 10 个会话；`/v1/auth/reset` 成功后撤销全部会话；验证码 10 分钟有效、5 次尝试、单次消费。新增表 `auth_guard(key, failures, strikes, window_start, locked_until, updated_at)`，无外键、可安全回滚代码（旧代码不读这张表）。

验收（2026-11-01）：`npm test --prefix server` 142/142 通过（新增 20 项：`password.test.mjs` 6、`guard.test.mjs` 6、`auth-hardening.test.mjs` 8），`npm run check` 通过。真实进程（`XP_DEV_MAIL=1`、临时 DB）验收：弱密码 `password123`／`01234567`／与用户名相同 各 `400 PASSWORD_WEAK` 且用户表仍为空；强密码 201 且行内为 `scrypt1$17$8$1$…`；连续 5 次错密码 → 401×5，第 6 次（正确密码）`429` + `Retry-After: 60`，`auth_guard` 里账号键与来源键各 1 条；手工写入旧格式哈希后登录 200 且该行被就地改写为 `scrypt1$…`；`POST /v1/auth/password` 200 → 未用完的重置码清零、其它会话 401、当前会话仍 200、旧密码 401、新密码 200；`PASSWORD_PEPPER=…` 启动后新账号为 `scrypt1p$…` 且能登录、纯 `scrypt1$` 账号照常登录；去掉该变量重启后 peppered 账号 401 且 stdout 出现一行警告（不含任何用户信息）；8 个并发注册峰值 RSS 319 MB。生产未部署，未触碰 tt 账号。

## 微信小程序登录与双向绑定（2026-11-03）

依据：`docs/SPEC.md` 末尾「2026-11-02 修订二」（账号：没有账号的人可以只用微信登录小程序；三端都支持微信 ⇄ 邮箱双向绑定；App/Web 一侧通过扫小程序码完成绑定）。**代码已就位，未部署。**

个人主体拿不到开放平台登录，唯一能拿到的身份是**小程序**的 `openid`。所以这一节只围绕三件事：小程序自己登录、App/Web 生成小程序码把微信挂到已有账号、以及解绑的边界。

### 账号形态

`users` 表：`email` 与 `password` 改为可空，新增 `wx_openid`（唯一）。老库在启动时重建一次（`store.mjs` 的 `migrateWeChatUsers`），账本/会话/圈子等子表不受影响。

`publicUser` 多一个字段 `wechat: boolean`；`email` 可能为 `null`（微信建号、还没挂邮箱时）。客户端判断「有哪些登录方式」请看这两个字段，不要假设 `email` 一定存在。

`verified` 的含义从「邮箱已验证」放宽为「已经证明持有一种真实登录方式」：微信建号时直接为 `1`（拿到 openid 就是证明），之后挂邮箱时由验证码证明，保持 `1`。这样 `/v1/chat`、账本同步、圈子、头像等既有「需已验证」的接口对微信账号一样可用。

微信建号的用户名自动生成：`wx_` + 10 位十六进制，撞了就重试。用户之后可以用 `POST /v1/auth/profile` 改名。

### 环境变量（tt 设置）

| 变量 | 作用 |
|---|---|
| `WX_MP_APPID` | 小程序 AppID（`wx36cf3bfafff4d03c`） |
| `WX_MP_SECRET` | 小程序 AppSecret。只放服务器 env，代码、测试、日志里都没有 |

两者缺一，微信相关接口一律 `503 WECHAT_UNAVAILABLE`（不会假装成功）。测试用假值 + 注入假客户端，不触网。

### 接口

| 方法 | 路径 | 身份 | 说明 |
|---|---|---|---|
| POST | `/v1/auth/wechat` | 无 | 小程序登录：`{code, client:"native"}` |
| POST | `/v1/auth/wechat/bind-ticket` | 已登录 | 生成一次性绑定票据 + 小程序码 |
| GET | `/v1/auth/wechat/bind-ticket/:ticket` | 已登录（须是票据所属账号） | 轮询票据状态：`pending` / `scanned` / `bound` / `rejected` / `expired` |
| POST | `/v1/auth/wechat/bind` | 无（小程序侧） | `{ticket, code, nickname?}` **扫码登记**，不绑定 |
| POST | `/v1/auth/wechat/bind-status` | 无（小程序侧） | `{ticket}` → 只返回状态枚举，没有账号数据、没有会话 |
| POST | `/v1/auth/wechat/bind-ticket/:ticket/confirm` | 已登录（票据所属账号） | App 侧确认，**这一步才写入 openid** |
| POST | `/v1/auth/wechat/bind-ticket/:ticket/reject` | 已登录（票据所属账号） | App 侧拒绝，票据作废 |
| DELETE | `/v1/auth/wechat` | 已登录 | 解绑微信 |
| POST | `/v1/auth/email-attach` | 已登录、账号无邮箱 | 给微信账号挂邮箱：`{email}` |
| POST | `/v1/auth/email-attach/confirm` | 同上 | `{code}` 确认 |
| POST | `/v1/auth/password` | 已登录 | 账号**没有密码**时接受只有 `{newPassword}`（见下） |

#### 1. `POST /v1/auth/wechat`

```json
{ "code": "<wx.login 的 code>", "client": "native" }
```

- 服务端用 `jscode2session` 换 `openid`。`session_key` 读完即丢：**不下发、不落库、不写日志**，我们不解密微信用户数据。
- 已绑定：直接登录。未绑定：新建只有微信的账号并登录。
- 返回与 native 登录一致：`200 {user, token}` + `Set-Cookie`。小程序必须带 `client:"native"` 才拿得到 Bearer（小程序没有 Origin，也没有 `sec-fetch-site`）。
- 错误：`400 WECHAT_CODE_INVALID`（code 无效/已用过）、`503 WECHAT_UNAVAILABLE`。
- 限流：同 IP 60 次/15 分钟，另受全局认证工作池约束。

#### 2. 绑定票据：扫码登记 + App 确认（两步）

```
App                    服务端                    小程序
 |-- bind-ticket ------>|                         |
 |<- id/scene/QR 图片 --|                         |
 |   （显示二维码）      |<---- bind {ticket,code} |  扫码
 |                      |  status=scanned         |
 |-- GET bind-ticket -->|                         |
 |<- status=scanned + 昵称/头像 --------------     |
 |   （弹窗：绑定这个微信？）                      |
 |-- POST .../confirm ->|  写 openid, status=bound
 |                      |<---- bind-status -------|  （可选，只读状态）
 |                      |<---- POST /v1/auth/wechat {新 code} → 会话
```

```json
POST /v1/auth/wechat/bind-ticket   {}            →  201
{ "id": "<20 字符票据>", "scene": "b:<票据>", "expiresAt": 1762143000000,
  "mime": "image/png", "image": "<小程序码 PNG 的 base64>" }
```

- 票据 **20 个字符**（15 字节 base64url）、5 分钟有效、**一次性**、只存 sha256 摘要。长度是硬约束：微信 `getwxacodeunlimit` 的 `scene` 上限 **32 字符**，`b:` + 20 = 22 才装得下；服务端在生成二维码前还会校验 `scene.length <= 32`，装不下就报错而不是发一张注定 503 的请求。
- 小程序码 `scene=b:<票据>`、`page=pages/bind/bind`。`access_token` 只在服务端缓存（提前 100 分钟刷新），不经过客户端。
- 二维码只用来传票据，票据本身不带账号信息，泄露也换不到登录态。
- 限流：每账号 10 次/小时；账号已绑微信 → `409 WECHAT_ALREADY_BOUND`。

```json
GET /v1/auth/wechat/bind-ticket/<票据>   →  200
{ "status": "pending",  "expiresAt": 1762143000000 }
{ "status": "scanned",  "wechat": { "nickname": "阿票" } }
{ "status": "bound",    "user": { ...publicUser } }
{ "status": "rejected" }
{ "status": "expired" }
```

- 只有生成票据的那个账号能查；别人拿着票据查一律 `400 WECHAT_TICKET_INVALID`（与「不存在」同一个回答，不泄漏票据是否有效）。
- 建议 App 2 秒轮询；看到 `scanned` 就弹确认框，`bound` 后直接用返回的 `user` 刷新账号。
- `wechat.nickname` 是**扫码方自报的**，只当文字显示在确认弹窗里（「这台手机自称是 阿票，绑定它？」），**不写进账号**；服务端会去掉控制字符与零宽/双向字符、长度上限 32，超出或清空后为空就不下发。**不接受任何头像 URL**：App 一旦加载任意地址就会把用户 IP 泄漏给第三方，头像只能走用户自己的 `/v1/auth/avatar`。

```json
POST /v1/auth/wechat/bind
{ "ticket": "<票据>", "code": "<小程序自己的 wx.login code>", "nickname": "阿票" }
  →  202 { "status": "scanned", "poll": "/v1/auth/wechat/bind-status" }
```

- 小程序打开 `pages/bind/bind?scene=b:<票据>` 后，带着票据和**它自己**的 `wx.login` code 调这里。这一步只**登记**：记录 openid 与昵称/头像，状态变 `scanned`，**账号什么都没变，也不下发会话**。
- 同一个 openid 重复调是幂等的；换另一个微信来抢已经 `scanned` 的票据 → `400 WECHAT_TICKET_INVALID`。
- 该 openid 已属于另一个账号 → `409 WECHAT_IN_USE`（先在那一边解绑，不自动合并）。

```json
POST /v1/auth/wechat/bind-ticket/<票据>/confirm  {}  → 200 {user}
POST /v1/auth/wechat/bind-ticket/<票据>/reject   {}  → 200 {status:"rejected", user}
```

- 只有票据所属账号、且状态是 `scanned` 才能确认；确认在**一个事务**里完成「挂 openid + 票据置 bound」，两次并发确认只有一次成功，另一次 `400 WECHAT_TICKET_INVALID`。
- 写入时撞上唯一索引（另一台设备刚好把同一个微信绑了别的账号）→ `409 WECHAT_IN_USE`，不是 500。
- 拒绝把票据置 `rejected` 并清掉登记的信息，之后不能确认；账号不变。
- **为什么要这一步**：二维码是给「本人手机」看的，但任何人都可能把别人的码扫走。只有持有已登录 App 的人点确认，微信身份才会被挂上——扫码本身不足以证明是账号主人在操作。

小程序侧拿到会话的方式（二选一，**推荐前者**）：确认成功后 `POST /v1/auth/wechat/bind-status {ticket}` 会变成 `bound`，此时重新 `wx.login` 拿一个新 code 调 `POST /v1/auth/wechat`（这时 openid 已绑在该账号上，返回的就是那个账号的会话）。不要用票据本身换会话：票据不证明 openid。

#### 3. 解绑 `DELETE /v1/auth/wechat`

- 前提：账号**还有邮箱和密码**（能独立登录）。否则 `409 WECHAT_LAST_LOGIN`：接口不会把账号弄成没法登录。
- 账号本来就没绑 → `409 WECHAT_NOT_BOUND`。
- 成功后清空 `wx_openid` 与该账号的未用票据，返回 `{user}`（`wechat:false`）。会话不受影响。
- 限流：每账号 5 次/小时。

#### 4. 给微信账号挂邮箱

复用既有验证码流程（同一张 `codes` 表、同一个 `email_changes` 表、同一套发信限额与「同账号同目的互斥」）：

```json
POST /v1/auth/email-attach          { "email": "me@example.test" }  → 200 {ok:true}
POST /v1/auth/email-attach/confirm  { "code": "123456" }            → 200 {user}
```

- 只用于**还没有邮箱**的账号；已有邮箱走既有的 `/v1/auth/email-request`，否则 `409 EMAIL_ALREADY_SET`。
- 验证码证明归属，确认时同时把 `verified` 置 1，并撤销其它会话（保留当前）。
- 验证码 10 分钟、5 次尝试、单次消费，与既有规则一致。

#### 5. 给微信账号设第一个密码

账号 `password` 为空时，`POST /v1/auth/password` 只收 `{newPassword}`：会话就是所有权证明。但仍要求账号**已有邮箱**，否则 `400 EMAIL_REQUIRED`——没有邮箱的密码忘了就永远找不回来，不如不给设。

有密码的账号行为完全不变（当前密码 + 强度 + 撤销其它会话）。

### 邮箱已被占用：拒绝，不合并

小程序里给微信账号挂一个**已经属于别的账号**的邮箱，返回 `409 EMAIL_BOUND_ELSEWHERE`，文案：「这个邮箱已经在另一个账号上；请在那个账号里添加微信（App / Web 扫小程序码）」。

理由：

1. 合并意味着把两个账号的账本、圈子、会话、头像并到一起，还要决定冲突与删除语义——这不是一次验证码能授权的操作，误操作代价不可逆。
2. 反向路径已经存在且更安全：在**拥有该邮箱的那个账号**里扫小程序码绑定微信，邮箱和微信就都归到那一个账号上，没有任何数据搬迁。
3. 拒绝是可解释的：用户被告知去哪一端操作，而不是「系统出错」。

### 新增错误码

| HTTP | code | 文案 |
|---|---|---|
| 400 | `WECHAT_CODE_INVALID` | 微信登录凭证无效，请重新进入小程序 |
| 400 | `WECHAT_TICKET_INVALID` | 这个绑定码已过期或已用过，请重新生成 |
| 400 | `EMAIL_REQUIRED` | 先绑定一个邮箱，再设置密码 |
| 409 | `WECHAT_ALREADY_BOUND` | 这个账号已经绑定了另一个微信，请先解绑 |
| 409 | `WECHAT_IN_USE` | 这个微信已经绑在另一个账号上，请先在那边解绑 |
| 409 | `WECHAT_NOT_BOUND` | 这个账号还没有绑定微信 |
| 409 | `WECHAT_LAST_LOGIN` | 这是这个账号唯一的登录方式，先设好邮箱和密码再解绑 |
| 409 | `EMAIL_BOUND_ELSEWHERE` | 这个邮箱已经在另一个账号上；请在那个账号里添加微信（App / Web 扫小程序码） |
| 409 | `EMAIL_ALREADY_SET` | 这个账号已经有邮箱了，请用「更换邮箱」 |
| 503 | `WECHAT_UNAVAILABLE` | 微信登录暂不可用，请改用邮箱登录 |

### 需要前端配合

1. App/Web：设置页加「绑定微信」→ 调 `bind-ticket` → 显示 `image`（PNG）→ 轮询：`pending` 显示「等待扫码」，`scanned` 弹「绑定微信「昵称」？」→ 用户点确认调 `.../confirm`（点拒绝调 `.../reject`）→ `bound` 后刷新账号；账号对象新增 `wechat` 字段，`email` 可能是 `null`。
2. 小程序：`wx.login` 拿 code → `POST /v1/auth/wechat`（带 `client:"native"`）；扫码页读 `scene=b:<票据>` → `wx.login` → `POST /v1/auth/wechat/bind`（可带 `nickname`/`avatarUrl`）→ 显示「等待 App 确认」→ 轮询 `POST /v1/auth/wechat/bind-status {ticket}` → `bound` 后重新 `wx.login` 并调 `POST /v1/auth/wechat` 取会话；`rejected` 显示「已取消」。「绑定邮箱」页复用验证码 UI 调 `email-attach` / `email-attach/confirm`。
3. 老客户端遇到 `email: null` / 未知字段 `wechat` 不应崩溃（Android `AccountUser` 目前读 `email` 为必填，建议改成可空）。

### 验收（2026-11-03，未部署）

`npm test --prefix server` 193/193 通过，其中 18 项微信测试：新用户建号（id 是 UUID）、重复登录不重复建号、无效 code、`session_key` 不出服务端、票据长度与 `scene ≤ 32`（断言真实值，不靠 mock）、扫码登记不下发会话、App 确认后才写入 openid、拒绝作废票据、他人扫码但 App 不确认则不绑定、过期票据、他人轮询/他人确认、并发确认只成功一次、换微信抢已登记票据被拒、同一 openid 竞争时 409 而非 500、票据限流、邮箱探测先限流再查占用、邮箱被占拒绝、挂邮箱+设首个密码+用新密码登录、无邮箱不能设密码、解绑保护与解绑成功、未配置时 503；另有老库迁移测试（可空列、数据与子表外键保留、可重复执行）。`npm run check` 通过。
