# Web 端现状与计划（2026-11-02）

结论：**比赛演示层面是完整的，产品层面还不完整**。最大的缺口是 Web 和 App 用的是两套后端，同一个人在两边的账本互不相通。

## 1. 现状

| 模块 | 路由 / 位置 | 数据来源 | 状态 |
|---|---|---|---|
| 落地页 | `/`（`features/landing`，约 3.7k 行） | 静态 | **按 tt 要求整体废弃**，改做产品官网（见 §3） |
| 桌面工作台外壳 | `/app/*`（`components/desktop/*`） | — | 侧栏、状态栏、命令面板、消息中心齐全 |
| 总览 / 账本 / 统计 / 小票 | `/app`、`/app/ledger`、`/app/stats`、`/app/receipt` | **QMuse 云服务**（`services/cloudLedger.ts`） | 可用；和 Android 的自建 `/v1/sync` **不是同一个账本** |
| AI | `/app/ai`（约 1.1k 行） | 自建服务端 Agent（`services/agent.ts`） | 可用 |
| 登录 | `/login` | 自建 `/v1/auth/*`（`services/account.ts`） | 可用 |
| 圈子 | `/app/circle` | **本地 mock**（`components/desktop/circleData.ts`） | 未接真实 `/v1/circles` |
| 卡包 | `/app/cards` | **占位数据**（`components/desktop/cardsData.ts`） | 未接 |
| 小店 | `/app/biz/*`（`features/biz` + `features/merchant`，约 6.7k 行） | 内存数据 + 文件导入 + 种子数据 | 演示可用；样本不足 20 的格子不展示的规则需要在提交前复查 |
| 旧手机版路由 | `/_legacy/*` | — | 只做跳转，可以在比赛后删除 |
| 测试 | 20 个 `*.test.ts(x)` | — | `npm run check && npm run build` 必须保持通过 |

## 2. 约束

- `web/AGENTS.md`：QMuse 托管；生产代码里**不能用 localStorage、内存假接口或其他后端替代 QMuse 云服务**；改动后跑 `npm run check` 和 `npm run build`；发布用 `qmuse import .`；不要手改 `.qmuse/project.json`。
- 比赛（12-05 提交）期间：账本继续用 QMuse 云服务；提交前按 `docs/MOCK-AUDIT.md` 清理或说明圈子和卡包的 mock。
- 比赛之后（作为产品）：Web 账本改走自建 `/v1/sync`，和 App 用同一个账本（QMuse 只做托管，或者迁走），圈子接 `/v1/circles`，卡包只显示 App 同步来的银行名和尾号，加入权益闸门和定价页。

## 3. 官网重做（替换 `/`）

- 定位：产品官网，面向普通用户，比赛入口作为其中一个入口。
- 信息架构：Hero（一句话 + 真机动图）→ 三个核心体验（记录 / 小票 / 圈子）→ 生活 → 隐私承诺 → 各平台下载（Android / 小程序 / iOS 即将推出）→ 定价（免费 / Pro / 买断）→ FAQ → 页脚（比赛入口）。
- 素材：`docs/media/` 里的真机动图和截图（样例账本）。
- 流程：用 `/unslop` skill（heydamir/unslop，CC BY 4.0）先审计现有 `/app`，再给出 3 个视觉方向（私有 Artifact）让 tt 挑选，然后在 `web/src/features/site/` 实现，替换 `/` 之后删除 `features/landing`。
- 不装 unslop 的可选依赖 Playwright（全局规则规定网页截图一律用 ego-browser）；它的生图功能涉及付费，用之前先问 tt。

## 4. unslop 审计

（待第 5 步补充：针对 `/app` 工作台的审计结论和优先修改项。）
