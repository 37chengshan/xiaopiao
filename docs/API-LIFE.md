# 拍照识别 · `POST /v1/life/recognize`（2026-11-03）

依据：`docs/LIFE-SPEC.md` §8、§9。只做一件事：把一张饭菜照片变成「这是什么、大概多少份」。**热量永远不由模型给**，由 App 本机知识库计算。

## 1. 请求

```
POST /v1/life/recognize
Authorization: Bearer <session>            （必须；Cookie 亦可）
Content-Type: application/json
```

```json
{ "image": "<标准 base64，不带 data: 前缀>" }
```

**只支持 base64 JSON，不支持 multipart**：与头像接口同一条路（`readJson` 的字节上限、UTF-8 校验、深度限制全部复用），少一套解析就少一类漏洞。客户端把照片压缩后用 base64 放进 JSON。

| 限制 | 值 | 超出的结果 |
|---|---|---|
| 请求体 | ≤ 280,000 字节（base64 后） | `413 IMAGE_TOO_LARGE` |
| 解码后的图片 | ≤ 200 KB | `413 IMAGE_TOO_LARGE` |
| 长边 | ≤ 512 px | `413 IMAGE_TOO_LARGE` |
| 格式 | 只收 **JPEG / WebP**（按魔术字节判定） | `400 IMAGE_INVALID` |

客户端声明的 mime 不被采信：类型由文件头决定，像素尺寸由 SOF / VP8X / VP8L 头解析。像素尺寸读不出来（截断、伪文件）同样 `400 IMAGE_INVALID`。

## 2. 响应

```json
{ "items": [ { "name": "牛肉面", "portion_text": "一碗", "grams_estimate": 550, "confidence": 0.72 } ] }
```

| 字段 | 说明 |
|---|---|
| `name` | 简体中文常见菜名，≤ 40 字 |
| `portion_text` | 份量描述，≤ 24 字，可能缺省 |
| `grams_estimate` | 推断出的克数（1–5000 的整数），可能缺省 |
| `confidence` | 0–1，两位小数，可能缺省 |

- 最多 6 项；`items: []` 且 **HTTP 200** 表示「没认出来」——这是可行动的结果（客户端显示「没认出来，手动搜一下」），不是错误。
- 模型输出是散文、JSON 被截断、字段越界、或顺手给了热量/价格，都会被过滤：认不出就是空数组，**绝不 500**。
- App 用 `name` 去匹配本机知识库：匹配上按知识库算热量，没匹配上让用户手选或手填。

## 3. 错误码

| HTTP | code | 中文文案 | 客户端下一步 |
|---|---|---|---|
| 401 | `UNAUTHORIZED` | 请重新登录 | 回登录 |
| 403 | `EMAIL_UNVERIFIED` | 请先验证邮箱 | 去验证 |
| 400 | `IMAGE_INVALID` | 图片格式不支持，请换一张 JPEG/WebP 照片 | 手动搜 |
| 413 | `IMAGE_TOO_LARGE` | 图片不能超过 200 KB，长边不超过 512px | 重新压缩后重试一次，再失败就手动搜 |
| 429 | `RATE_LIMITED` | 操作太频繁，请稍后再试（带 `Retry-After`） | 提示稍后再拍，或手动搜 |
| 503 | `RECOGNIZE_UNAVAILABLE` | 识别暂时不可用，手动搜一下 | 直接手动搜，不重试刷屏 |
| 504 | `RECOGNIZE_TIMEOUT` | 没认出来，手动搜一下 | 直接手动搜 |

服务端对视觉模型的调用上限 20 s；超时、上游 429/5xx、上游返回非 JSON 都会落到上表里明确的一项，不会有「一直转圈」。

## 4. 限流

| 维度 | 阈值 | 备注 |
|---|---|---|
| 每账号每分钟 | 6 次 | 拒绝也占配额（与其他路由一致） |
| 每账号每天 | 60 次 | 按 24 小时滑动窗口的限流键（`limits.expires`） |
| 每来源（IP）每分钟 | 30 次 | 防止一个账号之外的多账号轮换 |
| 并发 | 全实例 2 个视觉调用 | 超出 `503 SERVER_BUSY`（`Retry-After: 5`） |

超过账号额度返回 `429 RATE_LIMITED`，文案是中文，客户端可直接展示。

## 5. 隐私

- 图片**不落盘、不写数据库、不写日志、不回显**。请求体只存在于内存，函数返回后即丢弃；错误响应也不包含图片内容。
- `security_summary` 只记拒绝类型计数，不记账号、IP、图片、模型输出。
- 视觉模型 key 只在服务端环境变量里，App/Web 代码里没有。
- 首次使用要单独授权（App 负责），文案写明「照片只用于这一次识别，服务器不保存」；不授权也能手动或语音记。

## 6. 服务端配置（tt 设置）

| 变量 | 作用 |
|---|---|
| `LIFE_VISION_MODEL` | 视觉模型 id。**不设置 = 这个接口整体关闭**（返回 `503 RECOGNIZE_UNAVAILABLE`），不会静默用文本模型硬撑 |
| `LIFE_VISION_API_KEY` | 视觉模型 key；不设置时回落到 `MODEL_API_KEY` |
| `LIFE_VISION_BASE_URL` | 可选，默认与文本供应商同一个 base URL（`https://api.commandcode.ai/provider/v1`） |

调用格式是 OpenAI 兼容的 `/chat/completions`，`stream: false`，图片以 `data:image/jpeg;base64,...` 内联。

## 7. 实现与测试

| 文件 | 职责 |
|---|---|
| `server/src/life.mjs` | 魔术字节 + 尺寸解析、base64 校验、视觉调用、输出白名单（`parseItems`） |
| `server/src/app.mjs` | 路由、鉴权、限流、body 上限（只对这条路由放宽到 280 KB） |

测试 `server/test/life.test.mjs`：未登录、合法 JPEG/WebP、PNG 冒充、超 200 KB、长边 > 512、坏 JPEG、每分钟第 7 次 429、模型散文/坏 JSON/超时、6 项上限、热量字段被丢弃、请求形状与 `data:` 前缀。
