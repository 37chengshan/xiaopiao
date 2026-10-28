# 今日小票 (xiaopiao)

Alipay 智能体涌现奖 Season 1 参赛作品。截止 2026-12-06（按 12-05 提交）。

一句话：每天打印一张会说话的 AI 小票——无感记账、小圈子周报、头衔成就；用户自愿贡献的匿名聚合数据，变成小店老板的经营参考。

> 位于 `~/code/match/` 伞目录下；该层**不是** git 仓库。本仓库独立，worktree 放 `../_worktrees/xiaopiao/`，不要在仓库内 clone 别的仓库。

## 先读什么

- `docs/SPEC.md` — 定稿 Spec（需求、已定决策 D1–D11、架构、优先级、节奏）。**改动需求前先对照它**，标 `[假设]` 的项可被用户推翻。
- `docs/BRIEF.md` — 简要背景。
- `web/AGENTS.md` — QMuse Web 子项目规则（云服务、`qmuse import`、assets 约定）。编辑 `web/` 前必读，规则以它为准。

## 目录

| 路径 | 内容 |
|---|---|
| `app/` | Android 原生 App（Kotlin + Jetpack Compose，包名 `com.xiaopiao.app`），**当前主战场** |
| `web/` | QMuse Web 模板（React + Vite + TanStack Router + shadcn）；Spec 中 Web 端暂缓，不主动扩写 |
| `docs/` | Spec 与 Brief |
| `gradle/libs.versions.toml` | Android 依赖版本目录；加依赖先改这里 |

`app/src/main/java/com/xiaopiao/app/` 分层：`domain/`（模型、`LedgerRepository` 接口、金额工具）→ `data/`（目前只有 `InMemoryLedgerRepository`）→ `insights/`（如 `DailyReceiptBuilder`）→ `ui/`（`today` / `ledger` / `insights` / `merchant` / `settings` / `theme` / `components`）。依赖注入见 `AppContainer.kt`。

## 常用命令

Android（仓库根目录；JDK 17，minSdk 26，compileSdk 37）：

```bash
./gradlew :app:assembleDebug     # 构建 debug APK
./gradlew :app:lint              # Lint
```

Web（在 `web/` 内）：

```bash
npm install
npm run check && npm run build   # 改动后都要过
```

## 产品原则（不可违背）

1. **所有选择由人来做**：AI 只记录、总结、提醒、模拟后果；不推荐买什么、不比价、不下单、不给理财/收益结论。
2. **数据底线**：账单原文/截图只在本机解析，云端只存结构化字段；数据贡献默认关闭、单独授权、可撤回；B 端只给聚合，样本 <20 的格子不展示；游戏表态与真实交易数据分开存、分开标注。
3. **演示数据必须显式标注「演示数据」**，不冒充真实案例。
4. 订阅侦探只指引如何取消，**不代操作**。
5. 模型 API key 只放云端，绝不进 App/浏览器代码。

## 工程约定

- 先做最小改动，匹配现有风格；Compose UI 复用 `ui/components/Base.kt` 与 `ui/theme/Tokens.kt`（`XpColors` 等）里的 design tokens，不要硬编码颜色/间距。
- 金额一律用 `Cents`（`Long`，单位分，见 `domain/Models.kt`），展示用 `Cents.yuan()`（`domain/Money.kt`），不要用浮点表示钱。
- 优先级按 Spec §7 的 P0/P1/P2 排序；不删需求，只调顺序。
- 需求改动同步更新 `docs/SPEC.md`（写明日期与原因）。

## 不要提交 / 不要碰

- 不提交：`local.properties`、`.gradle/`、`.kotlin/`、`build/`、`web/node_modules/`、`web/dist/`、任何 `.env*`、token、cookie、证书/keystore。
- 不手改 `web/.qmuse/project.json`（平台绑定）。
- 推送、发布、创建远端资源需用户明确批准。

## 交接文档（记忆文档）

全局 hook（`~/.claude/hooks/context-handoff.py`）会在**上下文用量过高**或 **5h 额度剩余 ≤15%** 时中断一次，要求写交接文档（骨架已生成在 `~/.claude/handoffs/`）。收到该提示时：先 Read 骨架，整份填写，写完只告诉用户路径，不再继续原任务；新会话 `/clear` 后会自动注入该文档。文档里不得出现任何 key/token。
