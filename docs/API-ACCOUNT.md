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
