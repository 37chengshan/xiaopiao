# 账本同步 API（server/，v1）

日期：2026-10-29 · 分支 `feat/ledger-sync` · 实现：`server/src/ledger.mjs` + `server/src/app.mjs`

App 与 Web 登录后读写同一份账本。**云端只存结构化字段**：金额（整数分）、方向、商户、分类、时间、来源、备注、
卡后四位；账单原文、截图、标题、小票文字一律不收（产品原则 D9）。客户端为 App/Web，由协调者接入。

---

## 1. 鉴权

两种方式，任选其一，等价：

| 客户端 | 方式 | 说明 |
|---|---|---|
| Web | Cookie `xp_session` | 登录/注册时由服务端 `Set-Cookie`（HttpOnly, SameSite=Strict, Path=/v1），浏览器自动带上 |
| Android | `Authorization: Bearer <token>` | 注册/登录时 body 里带 `"client": "native"`，响应里的 `token` 就是它 |

- **必须已验证邮箱**：未登录 → `401 UNAUTHORIZED`；已登录但没验证 → `403 EMAIL_UNVERIFIED`。
- 浏览器请求会校验 `Origin`（生产只允许配置的那一个 HTTPS 源），原生请求没有 `Origin`。
- 会话 30 天，`/v1/auth/logout` 或改密码会失效。

---

## 2. `POST /v1/ledger/push`

把本机脏数据推上去。服务端按 `updatedAt` 做 last-write-wins。**单次 `txns + categories` 合计 ≤ 500 条**，
body ≤ 512 KB（超过 → `413 LEDGER_PAYLOAD_TOO_LARGE`）。

### 请求

```json
{
  "deviceId": "phone-1",
  "txns": [
    {
      "id": "2f1b3c9e-0001",
      "amountCents": 3500,
      "direction": "OUT",
      "merchant": "老乡鸡",
      "category": "吃饭",
      "occurredAt": 1760000000000,
      "source": "NOTIFICATION",
      "note": "",
      "cardLast4": null,
      "updatedAt": 1760000000500,
      "deleted": false
    }
  ],
  "categories": [
    {
      "id": "custom:1",
      "label": "宠物",
      "iconKey": "paw",
      "hue": "blue",
      "direction": "OUT",
      "hidden": false,
      "sortOrder": 9,
      "updatedAt": 1760000000600,
      "deleted": false
    }
  ]
}
```

### 响应 `200`

```json
{
  "applied": 3,
  "stale":  [{ "id": "2f1b3c9e-0001", "kind": "txn" }],
  "rejected": [
    { "id": "2f1b3c9e-0004", "kind": "txn", "reason": "UNKNOWN_FIELD" },
    { "id": "2f1b3c9e-0005", "kind": "txn", "reason": "RAW_TEXT_FORBIDDEN" }
  ],
  "seq": 4
}
```

| 字段 | 含义 |
|---|---|
| `applied` | 真正写入（含墓碑与同时间戳覆盖）的条数 |
| `stale` | 服务端已有更新的版本，**本次没写**；客户端应丢弃本机这份 |
| `rejected` | 校验不通过、根本没进库；客户端**保留**这条脏数据，修好字段再推 |
| `seq` | 该用户当前的同步序号（本次 push 之后） |

`kind` ∈ `txn` | `category`。`rejected[].id` 在缺少合法 `id` 时是 `null`。

### 事务字段

| JSON | 库列 | 规则 |
|---|---|---|
| `id` | `id` | 客户端生成的字符串，`[A-Za-z0-9][A-Za-z0-9._:-]{0,63}`，和用户组合唯一 |
| `amountCents` | `amount_cents` | 整数分，`> 0` 且 `≤ 10^10`；浮点/负数/0 一律拒 |
| `direction` | `direction` | `OUT` \| `IN` |
| `merchant` | `merchant` | 字符串 ≤ 64，可空（App 会回填分类名） |
| `category` | `category` | 字符串 ≤ 32，可空 |
| `occurredAt` | `occurred_at` | 毫秒时间戳，`0 < x ≤ 2100-01-01` |
| `source` | `source` | 大写+下划线 ≤ 16：`MANUAL` `NOTIFICATION` `VOICE` `IMPORT` `OCR` … |
| `note` | `note` | 字符串 ≤ 200，可空；**只放一句话备注，不放原文** |
| `cardLast4` | `card_last4` | `null` 或 4 位数字字符串 |
| `updatedAt` | `updated_at` | 客户端修改时间（毫秒），冲突裁决只用它 |
| `deleted` | `deleted` | 墓碑标记，缺省 `false`；删除永远不物理删 |

服务端还写 `device_id`（本次 push 的 `deviceId`）和 `seq`，读接口会原样返回。

### 分类字段

`id`、`label`(≤32)、`iconKey`(≤32)、`hue`(≤24)、`direction`(`OUT`/`IN`/`null`)、`hidden`(bool)、
`sortOrder`(0..100000)、`updatedAt`、`deleted`；同样有 `seq`。

### snake_case 别名

为了对着上表直接写代码，`amount_cents` / `occurred_at` / `card_last4` / `updated_at` / `icon_key` /
`sort_order` / `device_id` 也接受；同一条里两种写法同时出现且值不同 → `rejected: ALIAS_CONFLICT`。
返回永远是 camelCase。

### `rejected[].reason`

| reason | 触发 |
|---|---|
| `RAW_TEXT_FORBIDDEN` | 出现 `rawText` `raw` `text` `title` `body` `lines` `items` `receipt` `ocr` `screenshot(s)` `image(s)` `photo(s)` `html` `payload` `attachment` |
| `UNKNOWN_FIELD` | 其它没定义的字段（白名单之外一律拒） |
| `ALIAS_CONFLICT` | 别名与正式名同时出现且值不同 |
| `INVALID` | 该条不是对象 |
| `ID_INVALID` | 缺少 id 或不匹配 `[A-Za-z0-9][A-Za-z0-9._:-]{0,63}` |
| `AMOUNT_INVALID` | 非整数、≤ 0、> 10^10 |
| `DIRECTION_INVALID` | 不是 `OUT`/`IN` |
| `MERCHANT_INVALID` / `CATEGORY_INVALID` / `NOTE_INVALID` | 超长或非字符串 |
| `OCCURRED_AT_INVALID` / `UPDATED_AT_INVALID` | 非整数或超出时间范围 |
| `SOURCE_INVALID` | 不是 `^[A-Z][A-Z0-9_]{0,15}$` |
| `CARD_INVALID` | 不是 `null` 或 4 位数字 |
| `LABEL_INVALID` / `ICON_INVALID` / `HUE_INVALID` / `SORT_INVALID` / `HIDDEN_INVALID` / `DELETED_INVALID` | 分类字段不合法 |

单条被拒不影响同一批里的其它条。

---

## 3. `GET /v1/ledger/changes?since=<seq>&limit=<n>`

拉取 `seq > since` 的所有变更（**含墓碑**），按 `seq` 升序。`since=0` 即全量。

- `since`：非负整数，缺省 `0`；非法（负数/非数字）→ `400 INVALID_INPUT`。
- `limit`：1–500，缺省 `200`。

### 响应 `200`

```json
{
  "txns": [ { "id": "2f1b3c9e-0001", "amountCents": 3500, "direction": "OUT", "merchant": "老乡鸡",
              "category": "吃饭", "occurredAt": 1760000000000, "source": "NOTIFICATION", "note": "",
              "cardLast4": null, "updatedAt": 1760000000500, "deleted": false,
              "deviceId": "phone-1", "seq": 1 } ],
  "categories": [ { "id": "custom:1", "label": "宠物", "iconKey": "paw", "hue": "blue", "direction": "OUT",
                    "hidden": false, "sortOrder": 9, "updatedAt": 1760000000600, "deleted": false, "seq": 3 } ],
  "nextSince": 3,
  "hasMore": false
}
```

- `nextSince`：本页最后一条的 `seq`；空页时原样返回 `since`。**客户端持久化它**，下次从这个值继续。
- `hasMore`：还有更多（继续用新的 `nextSince` 拉，直到 `false`）。
- 两个数组是同一页里按 `seq` 合并后拆开的，所以 `nextSince` 对两边都成立；`deleted: true` 的行要按删除处理。

---

## 4. 冲突规则（last-write-wins）

对每个 `(user, id)`：

1. 服务端没有 → 写入，分配新 `seq`。
2. 来件 `updatedAt > 服务端` → 覆盖，分配新 `seq`。
3. 来件 `updatedAt == 服务端` → **后到的赢**，覆盖并分配新 `seq`。
4. 来件 `updatedAt < 服务端` → 不写，进 `stale`（不分配 `seq`）。

删除也是普通写入：`deleted: true` + 更大的 `updatedAt` 覆盖旧行，保留 `seq` 供别的设备拉到墓碑；
之后用更大的 `updatedAt` 再推 `deleted: false` 就能恢复（墓碑不是永久封禁）。

`seq` 每个用户独立递增，只由成功写入分配；被拒/过期的条目不消耗 `seq`。

---

## 5. 客户端推荐流程

```
登录（Web: cookie / App: client:"native" 拿 token）
  ↓
若本机有 dirty 行（新建/改/删）
  → POST /v1/ledger/push { deviceId, txns, categories }
     ├─ applied：这些行变干净
     ├─ stale：服务端更新 → 用 changes 拉回来覆盖本机，标干净
     └─ rejected：保留 dirty，按 reason 修字段（原文类字段直接丢弃，不上报）
  ↓
GET /v1/ledger/changes?since=<本地 nextSince>&limit=200
  → 按 seq 应用（txns / categories，含 deleted）
  → 持久化 nextSince；hasMore 为 true 就继续拉
  ↓
空闲时重复（App 启动 + 前台每 30–60 s；Web 打开时 + 手动刷新）
```

要点：

- **先 push 再 pull**：push 的 `seq` 是权威的，pull 能立刻把别人的改动盖回来。
- `nextSince` 只在本机持久化（App: DataStore；Web: IndexedDB/localStorage），**不要**用服务器时间。
- `stale` 行要保留服务端版本的本地副本，否则下次 push 又会撞一次。
- 离线队列里的行保持 `dirty`，联网后整批推；单批超过 500 条就分批。

---

## 6. 错误码

| HTTP | `error` | 何时 |
|---|---|---|
| 400 | `INVALID_INPUT` | body 不是 JSON 对象、`deviceId` 缺失/超长、`txns`/`categories` 不是数组、`since`/`limit` 非法 |
| 400 | `LEDGER_TOO_MANY` | `txns + categories > 500` |
| 401 | `UNAUTHORIZED` | 没有/过期会话，或 `Origin` 不被允许 |
| 403 | `EMAIL_UNVERIFIED` | 已登录但邮箱未验证 |
| 413 | `LEDGER_PAYLOAD_TOO_LARGE` | body > 512 KB |
| 429 | `RATE_LIMITED` | 触发限流（见下） |
| 500 | `INTERNAL_ERROR` | 服务端异常 |

限流（沿用 `store.limits` 的固定窗口）：

- 每 IP 全局 300 次/分钟；
- `push` 每用户 300 次/小时，`changes` 每用户 900 次/小时。

日志里只有用户 id、条数、状态码与耗时，**不打印 merchant / note / 金额**。

---

## 7. 本机验证（curl）

```bash
B=http://127.0.0.1:8931   # 8787 可能被别的进程占用；用任意空闲端口起 server/src/index.mjs 或测试夹具
J='Content-Type: application/json'

# 1) 注册（原生客户端直接拿 token；Web 用 cookie）
REG=$(curl -s -X POST $B/v1/auth/register -H "$J" \
  -d '{"email":"demo@example.test","username":"demo","password":"A long demo passphrase 123","client":"native"}')
TOKEN=$(echo "$REG" | python3 -c 'import json,sys;print(json.load(sys.stdin)["token"])')

# 2) 验证邮箱（验证码来自邮件 / 测试夹具的 mail 数组）
curl -s -X POST $B/v1/auth/verify -H "$J" -H "Authorization: Bearer $TOKEN" -d '{"code":"164861"}'

# 3) push
curl -s -X POST $B/v1/ledger/push -H "$J" -H "Authorization: Bearer $TOKEN" -d '{
  "deviceId":"phone-1",
  "txns":[{"id":"2f1b3c9e-0001","amountCents":3500,"direction":"OUT","merchant":"老乡鸡",
           "category":"吃饭","occurredAt":1760000000000,"source":"NOTIFICATION","updatedAt":1760000000500}],
  "categories":[]}'
# => {"applied":1,"stale":[],"rejected":[],"seq":1}

# 4) pull
curl -s "$B/v1/ledger/changes?since=0&limit=200" -H "Authorization: Bearer $TOKEN"
```

完整运行记录（含 stale / rejected / 墓碑 / 分页 / 401/403）见 `qa/sync-v1/REPORT.md`。
