# 圈子动态流 API（server/，v1）

日期：2026-10-29 · 分支 `feat/circle-api` · 实现：`server/src/circle.mjs` + `server/src/app.mjs`

「圈子」是**只在自己的小圈子（家人、好友）里可见**的动态流，不是公开广场。帖子 = 文字 + 可选的一张
「今日小票」卡片，可点赞、可评论。客户端（App / Web）由协调者接入，本文只描述服务端契约。

两条不可违背的规则写在代码注释里，也写在这里：

1. **小票卡片只带结构化数字和短文**：日期、总额、头衔、笔数、小票上那句话，以及作者主动打开时的明细行。
   任何原文类字段（`rawText`、`notification`、`screenshot`、`merchant` …）整条请求 `400 RECEIPT_RAW_TEXT`。
2. **非成员一律 404**：圈子、成员表、feed、帖子、评论、点赞对非成员都返回 `404`，不暴露「存在但无权」。
   403 只留给「是这个圈子的成员，但不是这条内容的作者 / 不是圈主」。

---

## 1. 鉴权

与账本同步一致（见 `docs/API-SYNC.md` §1）：

| 客户端 | 方式 |
|---|---|
| Web | Cookie `xp_session`（HttpOnly, SameSite=Strict, Path=/v1） |
| Android | `Authorization: Bearer <token>`（注册/登录 body 带 `"client": "native"`） |

- 全部接口都要求**已验证邮箱**：未登录 → `401 UNAUTHORIZED`；已登录未验证 → `403 EMAIL_UNVERIFIED`。
- 所有 id 都是服务端生成的 UUID；`POST /v1/circles/:id/invites` 返回的 8 位邀请码是唯一的例外。

---

## 2. 数据结构

### 2.1 `circle`

```json
{ "id": "f75ad2bb-…", "name": "家人", "role": "owner", "memberCount": 2, "createdAt": 1793331895128 }
```

| 字段 | 说明 |
|---|---|
| `name` | 1–20 字（首尾空白会 trim；空 → `400 INVALID_INPUT`） |
| `role` | `owner` \| `member`：**我**在这个圈子里的角色 |
| `memberCount` | 现有成员数（含圈主） |
| `createdAt` | 毫秒时间戳 |

### 2.2 `receipt` 小票卡片（严格白名单）

```json
{
  "date": "2026-10-29",
  "totalCents": 8350,
  "title": "午饭自由人",
  "count": 3,
  "comment": "比昨天省了 12 块。",
  "lines": [
    { "label": "早饭", "amountCents": 1200 },
    { "label": "午饭", "amountCents": 3500 }
  ]
}
```

| 字段 | 必填 | 规则 |
|---|---|---|
| `date` | 是 | `YYYY-MM-DD`，必须是真实存在的日期（`2026-02-31` 拒） |
| `totalCents` | 是 | 整数分，`0 ≤ x ≤ 10^10`（浮点、负数拒） |
| `title` | 是 | 头衔，1–16 字 |
| `count` | 是 | 笔数，整数 `0..1_000_000` |
| `comment` | 否 | 小票上那句话，≤ 60 字，缺省 `""` |
| `lines` | 否 | ≤ 30 条 `{label: 1–16 字, amountCents: 整数分}`；**只有作者主动打开明细时才会带** |

- 默认只露总额和头衔 —— 数据底线：默认卡片里没有明细行。
- 白名单之外的字段 → `400 RECEIPT_FIELD`；原文类字段 → `400 RECEIPT_RAW_TEXT`（`lines[].merchant` 同理）。
- 其它形状问题（字段类型、超长、日期非法）→ `400 RECEIPT_INVALID`。

### 2.3 `post`

```json
{
  "id": "b7ce3682-…",
  "text": "今天三顿都在外面吃，有点过分了。",
  "receipt": { "date": "2026-10-29", "totalCents": 8350, "title": "午饭自由人", "count": 3, "comment": "比昨天省了 12 块。" },
  "createdAt": 1793331895276,
  "author": { "id": "69befd0a-…", "username": "alice" },
  "likeCount": 1,
  "likedByMe": true,
  "commentCount": 2,
  "comments": [ { "id": "ad63de9b-…", "text": "奶茶那笔可以砍掉", "createdAt": 1793331895357,
                  "author": { "id": "4d0beace-…", "username": "bob" } } ]
}
```

- `text` 最多 500 字；`text` 与 `receipt` **至少有一个**，都没有 → `400 POST_EMPTY`。
- `likedByMe` 是**请求者视角**：同一个帖子，B 看到 `true`，A 看到 `false`。
- `comments` 只给最新 3 条预览，**按时间正序**（旧的在前），方便客户端直接渲染；完整列表用 §5。
- `author` 只有 `{id, username}`，**永远不返回邮箱**。

---

## 3. 圈子

### `POST /v1/circles` — 创建并成为圈主

```json
{ "name": "家人" }
```

`201`：

```json
{ "circle": { "id": "f75ad2bb-…", "name": "家人", "role": "owner", "memberCount": 1, "createdAt": 1793331895128 } }
```

一个人最多加入（含自己创建）**10** 个圈子，超过 → `409 CIRCLE_LIMIT`。

### `GET /v1/circles` — 我的圈子

```json
{ "circles": [ { "id": "f75ad2bb-…", "name": "家人", "role": "member", "memberCount": 2, "createdAt": 1793331895128 } ] }
```

按创建时间倒序。只列自己加入的。

### `GET /v1/circles/:id/members` — 成员表

```json
{
  "count": 2,
  "members": [
    { "id": "69befd0a-…", "username": "alice", "role": "owner", "joinedAt": 1793331895128 },
    { "id": "4d0beace-…", "username": "bob", "role": "member", "joinedAt": 1793331895263 }
  ]
}
```

圈主排在最前，其后按加入时间。

### 邀请

`POST /v1/circles/:id/invites`（任意成员都可以生成，空 body）：

```json
{ "code": "8A27S7PA", "expiresAt": 1793936695195, "maxUses": 20 }
```

- `201`；8 位，字母表去掉了 `0 O 1 I`（要口头报给人）。
- 默认 **7 天**过期、**20** 次使用；过期或用满即失效 → 加入时 `400 INVITE_INVALID`。

`POST /v1/circles/join`（body `{ "code": "8A27S7PA" }`，大小写不敏感）：

```json
{ "circle": { "id": "f75ad2bb-…", "name": "家人", "role": "member", "memberCount": 2, "createdAt": 1793331895128 } }
```

- 已是成员 → `200`，**不消耗**使用次数（重复点「加入」是安全的）。
- 一个圈子最多 **50** 人 → `409 CIRCLE_FULL`；自己已有 10 个圈子 → `409 CIRCLE_LIMIT`。
- 邀请码无效 / 过期 / 用尽 / 圈子不存在，统一 `400 INVITE_INVALID`（不区分，避免探测）。

### `POST /v1/circles/:id/leave` — 退出

- 普通成员：`{ "ok": true, "deleted": false }`。
- 圈主：只有圈子里只剩自己时才能退，此时**圈子连同数据一起删除**，返回 `{ "ok": true, "deleted": true }`；
  还有别的成员时 → `409 CIRCLE_OWNER_LEAVE`（先把人移出去，或让对方自己退）。

### `DELETE /v1/circles/:id/members/:userId` — 移出成员

圈主专用，`{ "ok": true }`。

- 不是圈主 → `403 FORBIDDEN`；目标不是成员 → `404 MEMBER_NOT_FOUND`；移出自己 → `400 INVALID_INPUT`。
- 被移出的人立刻失去一切访问权（feed / 点赞 / 评论全部 `404`）。

---

## 4. 动态流

### `GET /v1/circles/:id/feed?before=<cursor>&limit=<n>`

- 时间**倒序**（最新在前），只含未删除的帖子。
- `limit`：1–50，缺省 **20**；越界 / 非数字 → `400 INVALID_INPUT`。
- `before`：上一页的 `nextCursor`；首页不传。

```json
{
  "posts": [ { "id": "b7ce3682-…", "text": "…", "receipt": { "…": "…" }, "createdAt": 1793331895276,
               "author": { "id": "69befd0a-…", "username": "alice" },
               "likeCount": 1, "likedByMe": true, "commentCount": 2,
               "comments": [ { "id": "ad63de9b-…", "text": "奶茶那笔可以砍掉", "createdAt": 1793331895357,
                               "author": { "id": "4d0beace-…", "username": "bob" } } ] } ],
  "nextCursor": "MTc5MzMzMTg5NTI3Ni4x"
}
```

- `nextCursor` 为 `null` 表示没有更多。
- 游标是不透明的 `<createdAt>.<rowid>` base64url：**客户端只负责原样回传**，不要解析、不要自己拼。
  非法游标 → `400 INVALID_INPUT`。
- 新帖子插进来不会让翻页重复或漏项（游标锚在时间 + 行号上）。

### `POST /v1/circles/:id/posts` — 发帖

```json
{ "text": "今天三顿都在外面吃，有点过分了。", "receipt": { "date": "2026-10-29", "totalCents": 8350, "title": "午饭自由人", "count": 3, "comment": "比昨天省了 12 块。" } }
```

`201`：`{ "post": { …见 §2.3… } }`。

### `DELETE /v1/posts/:id` — 删帖（软删）

作者本人 → `{ "ok": true }`，帖子从 feed / 评论列表里消失，评论接口变 `404`。
同一圈子里的其他人 → `403 FORBIDDEN`；非成员或已被删 → `404 POST_NOT_FOUND`。重复删除 → `404`。

---

## 5. 点赞与评论

### `PUT /v1/posts/:id/like` / `DELETE /v1/posts/:id/like`

幂等，两个方向都可以重复调用：

```json
{ "liked": true, "likeCount": 1 }
```

```json
{ "liked": false, "likeCount": 0 }
```

`likeCount` 是**现算**的真实人数（`(post_id,user_id)` 唯一），不是累加器。

### `GET /v1/posts/:id/comments?before=<cursor>&limit=<n>`

时间倒序（最新在前），`limit` 1–50，缺省 **20**：

```json
{
  "comments": [ { "id": "3a8f1fdd-…", "text": "明天开始。", "createdAt": 1793331895368,
                  "author": { "id": "69befd0a-…", "username": "alice" } } ],
  "nextCursor": null
}
```

> feed 里的 3 条预览是正序，这里的完整列表是倒序 —— 两者刻意不同：预览为了直接渲染，列表为了「先看新的」。

### `POST /v1/posts/:id/comments`

```json
{ "text": "奶茶那笔可以砍掉" }
```

`201`：`{ "comment": { "id": "ad63de9b-…", "text": "…", "createdAt": 1793331895357, "author": { "id": "…", "username": "bob" } } }`
文本 1–200 字，空/超长 → `400 INVALID_INPUT`。

### `DELETE /v1/comments/:id`

只有评论作者能删（`{ "ok": true }`，软删）；别人 → `403 FORBIDDEN`；非成员 / 已删 → `404 POST_NOT_FOUND`。

---

## 6. 限制与限流

| 项 | 值 |
|---|---|
| 圈子名 | 1–20 字 |
| 一人最多圈子 | 10 |
| 一圈最多成员 | 50（含圈主） |
| 邀请码 | 8 位，默认 7 天 / 20 次 |
| 帖子文字 | ≤ 500 字；`text` 与 `receipt` 至少一个 |
| 评论文字 | ≤ 200 字 |
| 小票头衔 / 小票句子 / 明细行标签 | 16 / 60 / 16 字 |
| 小票明细行 | ≤ 30 条 |
| feed / comments 分页 | 1–50，缺省 20 |

固定窗口限流（`store.limits`，返回 `429 RATE_LIMITED`）：

| key | 上限 |
|---|---|
| 每 IP 全局 | 300 次/分钟 |
| 建圈 | 20 次/小时/人 |
| 加入 | 30 次/小时/人 |
| 生成邀请 | 60 次/小时/人 |
| 发帖 | 60 次/小时/人 |
| 评论 | 120 次/小时/人 |
| 点赞/取消 | 300 次/小时/人 |
| 拉 feed | 600 次/小时/人 |

**日志与响应里都不出现帖子文字、评论内容、金额和邮箱**：只有用户 id、状态码、耗时。

---

## 7. 错误码

| HTTP | `error` | 何时 |
|---|---|---|
| 400 | `INVALID_INPUT` | body 不是 JSON 对象、名字/文字空或超长、游标非法、`limit` 越界 |
| 400 | `POST_EMPTY` | 帖子既没有文字也没有小票 |
| 400 | `INVITE_INVALID` | 邀请码不存在 / 过期 / 用尽 |
| 400 | `RECEIPT_FIELD` | 小票卡片里有白名单之外的字段 |
| 400 | `RECEIPT_RAW_TEXT` | 小票卡片里出现原文类字段（`rawText` `notification` `screenshot` `merchant` …） |
| 400 | `RECEIPT_INVALID` | 小票字段类型/长度/日期非法 |
| 401 | `UNAUTHORIZED` | 没有/过期会话 |
| 403 | `EMAIL_UNVERIFIED` | 已登录但邮箱未验证 |
| 403 | `FORBIDDEN` | 是成员，但不是作者（删帖/删评论）或不是圈主（移出成员） |
| 404 | `CIRCLE_NOT_FOUND` | 圈子不存在，或我不是成员 |
| 404 | `POST_NOT_FOUND` | 帖子/评论不存在、已删，或我不是成员 |
| 404 | `MEMBER_NOT_FOUND` | 圈主要移出的人不在圈子里 |
| 409 | `CIRCLE_LIMIT` | 自己已经有 10 个圈子 |
| 409 | `CIRCLE_FULL` | 圈子已满 50 人 |
| 409 | `CIRCLE_OWNER_LEAVE` | 圈主还有别的成员时想退出 |
| 429 | `RATE_LIMITED` | 触发限流 |
| 500 | `INTERNAL_ERROR` | 服务端异常 |

成功响应的错误体格式统一为 `{ "error": "<CODE>", "message": "<中文提示>" }`。

---

## 8. 本机验证（curl）

```bash
B=http://127.0.0.1:8793   # 8793 只是示例；8787 常被占用，起服务时换一个空闲端口
J='Content-Type: application/json'

# 0) 注册（原生客户端直接拿 token）并验证邮箱（验证码来自邮件 / 测试夹具的 mail 数组）
REG=$(curl -s -X POST $B/v1/auth/register -H "$J" \
  -d '{"email":"alice@example.test","username":"alice","password":"A long demo passphrase 123","client":"native"}')
A=$(echo "$REG" | python3 -c 'import json,sys;print(json.load(sys.stdin)["token"])')
curl -s -X POST $B/v1/auth/verify -H "$J" -H "Authorization: Bearer $A" -d '{"code":"223737"}'

# 1) 建圈 → 邀请
CID=$(curl -s -X POST $B/v1/circles -H "$J" -H "Authorization: Bearer $A" -d '{"name":"家人"}' \
  | python3 -c 'import json,sys;print(json.load(sys.stdin)["circle"]["id"])')
CODE=$(curl -s -X POST $B/v1/circles/$CID/invites -H "$J" -H "Authorization: Bearer $A" \
  | python3 -c 'import json,sys;print(json.load(sys.stdin)["code"])')

# 2) 另一个人用邀请码加入
curl -s -X POST $B/v1/circles/join -H "$J" -H "Authorization: Bearer $B_TOKEN" -d "{\"code\":\"$CODE\"}"

# 3) 发带小票的帖子
curl -s -X POST $B/v1/circles/$CID/posts -H "$J" -H "Authorization: Bearer $A" -d '{
  "text":"今天三顿都在外面吃，有点过分了。",
  "receipt":{"date":"2026-10-29","totalCents":8350,"title":"午饭自由人","count":3,"comment":"比昨天省了 12 块。"}}'

# 4) 点赞（幂等）、评论
curl -s -X PUT $B/v1/posts/$PID/like -H "Authorization: Bearer $B_TOKEN"
curl -s -X POST $B/v1/posts/$PID/comments -H "$J" -H "Authorization: Bearer $B_TOKEN" -d '{"text":"奶茶那笔可以砍掉"}'

# 5) 拉 feed
curl -s "$B/v1/circles/$CID/feed?limit=20" -H "Authorization: Bearer $B_TOKEN"
```

完整一次跑通的输出（含 401 / 非成员 404 / 原文小票 400 / 软删后空 feed）见 `qa/circle-api/REPORT.md`。
