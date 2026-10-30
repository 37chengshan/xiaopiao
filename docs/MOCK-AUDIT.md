# Mock 数据审计清单（提交前）

日期：2026-10-29 · 只读审计 · 分支 `feat/circle-api` · 范围：Android `app/`、Web `web/src`（含落地页）、
落地页 worktree `_worktrees/xiaopiao/landing-v3/web/src`。server 测试夹具不在范围内。

产品原则 3 的口径：**测试版界面不加「演示数据」标注，但上线 / 比赛提交前必须知道每一处 mock 在哪、
用户会在哪看到它、怎么处理**（`AGENTS.md` 产品原则 3、`docs/APP-DESIGN.md` §9）。这份文档就是那张底账。

结论先说：**有 5 处 mock 会以「用户自己的数据」的形态出现**（小店营收、小店人群、Web 游客账本、
圈子动态流、圈子里的家人周报），这 5 处是 P0；其余是开发工具、脚本演示或文案里的示例数字。

复现方法（只读）：

```bash
grep -rn --include=*.kt -iE "demo|seed|sample|fake|mock" app/src/main
grep -rn --include=*.ts --include=*.tsx -iE "demo|seed|sample|fake|mock" web/src
grep -rn -iE "¥|[0-9]+ 人" web/src/features/landing landing-v3/web/src 2>/dev/null
```

---

## 1. 全部 mock 来源

「混入真实数据」一列的含义：用户会不会把它当成自己/自己家人的真实记录，或它是否会被写进真实存储。

### 1.1 Android `app/`

| # | 位置 | 内容 | 用户在哪看到 | 混入真实数据？ | 建议 |
|---|---|---|---|---|---|
| A1 | `app/src/main/java/com/xiaopiao/app/data/DemoSeed.kt:14`（`NOTE = "演示数据"`）、`:29`/`:30` 商户池、`:34` 21 天生成 | 3 周日常消费 + 3 个订阅（爱奇艺/网易云/iCloud），每行 `note = "演示数据"` | 设置 → 数据 → 「载入测试账本」（`ui/settings/DataSection.kt:80` 仅 `BuildConfig.DEBUG` 显示） | **会**：写进真实账本并持久化 | 保留为 DEBUG 工具；界面上要能看见 `演示数据`（见 §4 P0-4） |
| A2 | `app/src/main/java/com/xiaopiao/app/insights/SampleData.kt:14`（`:18` 生成 130 天） | 130 天假账本，「演示数据」注释写在文件头 | 无 —— 全仓库无引用（死代码） | 不会（从未渲染） | 删除 |
| A3 | `app/src/main/java/com/xiaopiao/app/ui/today/SampleData.kt:10` | 今日预览用的 9 笔 + 前几天假账 | 无 —— 全仓库无引用（死代码） | 不会（从未渲染） | 删除 |
| A4 | `app/src/main/java/com/xiaopiao/app/ui/circle/CircleModel.kt:207`（`CircleDemo`）、`:214` 成员（妈妈/爸爸/妹妹）、`:222` 三个圈子（我的一家 / 合租三人组 / 两个人）、`:271` 三天小票与文字 | 假家庭 + 假室友 + 三个圈子的帖子（主圈 13 条：3 天小票、文字、2 条系统消息，含「妹妹 本周头衔「奶茶续命王」」`:324`） | 圈子 tab 初始状态（`ui/circle/CircleHome.kt:89`，文件注释 `:79` 写明「Everything is local demo state」） | **会**：界面上完全像真实家人的小票；不落盘，但也没有任何 demo 标记 | 首次启动后替换成真实圈子；在真人数据到达前用空状态示例（P0-3） |
| A5 | `app/src/main/java/com/xiaopiao/app/ui/circle/CircleModel.kt:132` `baseReactions` | 每条帖子预置 1–3 个 👍、0–2 个 ❤️ 等起手计数（种子由 postId 哈希决定） | 圈子每条帖子的表情计数 | **会**：用户以为自己不在时已经有人表态 | 删除起手计数，或只在 demo 圈子里生效（P0-3） |
| A6 | `app/src/main/java/com/xiaopiao/app/ui/circle/CircleHome.kt:91`、`CircleManageSheets.kt:114`、`CircleDrawer.kt:80`/`:161`、`CircleLeaderboards.kt:86` | `viewerId` 默认值 = `CircleDemo.MY_ID`；抽屉里 `CircleDemo.members().first()` 兜底 | 圈子成员表、排行榜、管理弹层 | **会**：真实数据接上后，缺参路径仍会回落到 demo 身份 | 去掉默认值，改为必传（P1-1） |
| A7 | `app/src/main/java/com/xiaopiao/app/ui/chat/ChatModels.kt:386`（`ChatFamily.week`） | 「家里人这周花了多少」从 `CircleDemo.feed` 现算 | AI 对话的家庭周报回答（`:204` 开场白也提到圈子共享） | **会**：AI 把假家人金额当真实回答 | 接真实圈子 API 前，先答「圈子里还没有数据」（P0-3） |
| A8 | `app/src/main/java/com/xiaopiao/app/ui/chat/ChatModels.kt:116`（`ChatSuggestions`）、`:204` 开场白 | 3 条示例问题 + 一句能力介绍 | AI 对话首屏 | 不会（是提示不是数据） | 保留 |
| A9 | `app/src/main/java/com/xiaopiao/app/merchant/DemoRevenue.kt:9`/`:14` | 42 天合成营收（含 2 个闭店日），行标 `RevenueSource.SEED` | 小店版今日/营收/分析/经营小票 | **会且最严重**：`merchant/MerchantStore.kt:53` 首次启动自动种入并持久化；`ui/merchant/MerchantUi.kt:103`/`:107` 「seeded rows carry no label」→ 界面上与真实营收无法区分；`MerchantReceiptPage.kt:350` 注释「seeded days print like any other」 | 首次启动后替换为「记录今天营收」空状态；或保留种子但恢复「演示」标（P0-1） |
| A10 | `app/src/main/java/com/xiaopiao/app/merchant/DemoCrowd.kt:66`（`:68` `DEFAULT_SIZE = 300`、`:75` 生成）、`:10` `DEMO_SHOP_NAME`、`:11` `DEMO_LABEL` | 300 个合成顾客、年龄/口味/时段权重、`楼下的芋泥奶茶铺` | 小店 → 人群洞察（`ui/merchant/InsightsStep.kt:192`「约 N 人到店」）、顾客实验（`merchant/Experiment.kt:148`） | **会**：数字精确到个位数，看起来是真实到店人数；`DEMO_LABEL` 定义了但全仓库没有任何地方渲染 | 渲染 `演示数据` 徽标；或人群洞察在无授权数据时给空状态（P0-2） |
| A11 | `app/src/main/java/com/xiaopiao/app/ui/merchant/MerchantMine.kt:89` | 页头写死店名 `DEMO_SHOP_NAME` | 小店版「我的」页头 | **会**：用户以为这是自己店的名字 | 改成用户输入或「我的小店」（P0-2） |
| A12 | `app/src/main/java/com/xiaopiao/app/ui/merchant/SetupStep.kt:121` | 「楼下的芋泥奶茶铺 想听听你的想法」实验文案 | 小店 → 发起实验 | **会**：同上，店名被当成真实店 | 同上（P0-2） |
| A13 | `app/src/main/java/com/xiaopiao/app/ui/merchant/MerchantMine.kt:119`、`ResultsStep.kt:82` | 「样本 N 人」「人群样本 X 人 + 游戏表态 Y 人」 | 实验卡片、结果页 | **会**：X 来自 DemoCrowd 模拟，Y 是用户自己玩的 | 结果页标明哪部分是本机模拟（P0-2） |
| A14 | `app/src/main/java/com/xiaopiao/app/domain/games/GameContent.kt:505`–`:514`（`:817` `actualCents`） | 10 道价格题的「实际价格」（关东煮 ¥12.50、生椰拿铁 ¥16 …），hint 写「常见门店价格，仅作猜谜」 | 小游戏 → 猜价格 | 不会（是题目答案，不读用户数据） | 保留；比赛材料里别提成「真实价格数据」 |
| A15 | `app/src/main/java/com/xiaopiao/app/insights/BadgeCatalog.kt:26`–`:39` | 15 个头衔（首单达成、奶茶战神、钱包休眠者…） | 洞察 → 颁奖、印刷小票 | 不会（是规则产物，真实账本才算） | 保留 |
| A16 | `app/src/main/java/com/xiaopiao/app/ui/settings/SettingsScreen.kt:133` | 「已载入 N 笔，去记账页看看吧～」 | 设置 → 载入测试账本后的提示 | 不会 | 保留（配合 A1） |

### 1.2 Web 应用 `web/src`

| # | 位置 | 内容 | 用户在哪看到 | 混入真实数据？ | 建议 |
|---|---|---|---|---|---|
| W1 | `web/src/services/demoData.ts:6`（`buildDemoTxns`）、`services/demoLedger.ts:7`（`createMemoryLedger`） | 3 周假账：喜茶/瑞幸/美团外卖·黄焖鸡/iCloud ¥6…，`source: "DEMO"` | 未登录访问 `/app`：`services/LedgerProvider.tsx:32` 默认 `createMemoryLedger("demo")` | **会**：游客/评委打开 Web 版看到的就是一份像真的账本 | 未登录先进空账本 + 「载入示例」按钮（P0-4） |
| W2 | `web/src/components/desktop/circleData.ts:67`（三个圈子：我们家 / 合租三人组 / 大学室友）、`:74`–`:75` 成员（阿哲、小雨、老王、Mia、胖虎、程子、老六）、`:166` `seedMessages`、`:183` 起 5 张假小票 | 「阿哲 26 小时前的小票：食堂 ¥12 / 奶茶 ¥16 / 电影票 ¥45」等 | Web 桌面版圈子页 | **会**：和 A4 同一类问题（桌面版独立实现） | 与 A4 同批处理（P0-3） |
| W3 | `web/src/components/desktop/cardsData.ts:18`–`:21` | 3 张写死银行卡（招商 4821 / 中行 7130 / 交行 2296），注释写「Local placeholder until the card service exists」 | Web 桌面版卡包 | **会**：用户以为卡已被读取 | 空卡包 + 录入引导（P1-2） |
| W4 | `web/src/logic/biz/seed.ts:16`（`seededBook`）、`web/src/features/biz/store.ts:36` | 42 天合成营收，`source: "SEED"` | Web 小店后台（营收页、日报） | **会**：`web/src/logic/biz/book.ts:13` 注释自认「recognisable here in code, not labelled in the UI」 | 同 A9（P0-1） |
| W5 | `web/src/features/biz/strings.ts:264`、`:280` | 「这一轮的表态由本机预置」「表态来源：游戏表态，本机预置」 | 小店实验 → 发起 / 结果页 | 部分：文案已诚实说明，但人数仍显示（`web/src/features/biz/ui/ResultsBoard.tsx:92`） | 保留文案，人数旁加「本机模拟」（P1-3） |

### 1.3 落地页 `web/src/features/landing`（仓库内版本，`/` 路由）

| # | 位置 | 内容 | 用户在哪看到 | 混入真实数据？ | 建议 |
|---|---|---|---|---|---|
| L1 | `web/src/features/landing/strings.ts:44`–`:60`（`:49`–`:51` 金额） | 「现场演示」脚本：支付宝 面馆 −¥32.00、微信支付 便利店 −¥11.50、回话「这周吃饭 186，比上周少 24」、小票日期 `2026年10月28日 周三` | 落地页「现场演示」整段 | 不会（纯动画），但**看起来像真实使用录像** | 在段落里写清「示例动画」（P1-4） |
| L2 | `web/src/features/landing/sections/Demo.tsx:8` | 同一句「午饭 32 / 这周 186 / 少 24」硬编码在组件里（与 L1 重复维护） | 同上 | 不会 | 收敛到 `strings.ts`（P2-1） |
| L3 | `web/src/features/landing/strings.ts:78`–`:94` | bento 卡里的假数字：「找到 3 个规律扣款」「已用 68%」「还剩 ¥1,280」「三个人」「只给 ≥20 人的聚合」 | 落地页「它每天在做的六件小事」 | 不会 | 保留；比赛材料与页面均不宣称是真实统计（P2-2） |
| L4 | `web/src/features/landing/sections/Bento.tsx:84`–`:86`、`:106`、`:187` | 订阅金额 ¥6/¥25/¥15；预算环累计到 ¥552；「一年省」金额 | bento 卡动效 | 不会 | 同 L3 |

### 1.4 落地页 v4 worktree（`../landing-v3/web/src/features/landing`，相对本仓库根目录）

| # | 位置 | 内容 | 用户在哪看到 | 混入真实数据？ | 建议 |
|---|---|---|---|---|---|
| V1 | `../landing-v3/web/src/features/landing/lib/days.ts:33`（`SHELF_DAYS`） | 14 天固定小票：10-29 地铁 ¥4 / 午饭 ¥28 / 奶茶 ¥18 / 晚饭 ¥86；10-27 空账本；10-26 盒马 ¥128 … | 落地页「小票架」整排 | 不会 | 保留；标注「示例日期」（P2-2） |
| V2 | `../landing-v3/web/src/features/landing/strings.ts:62`、`:70`、`:86`、`:87`、`:469` | 「比昨天少 ¥12.00」「¥186.00，5 笔。上周 ¥214.00」「匿名聚合 · 32 人」「圈子 4 人 · 只看总额」「32 人 · 匿名汇总」 | 落地页 bento / 五件事 / 手机概念图 | 不会，但**具体人数会被当成真实样本** | 改「示例：32 人」或去掉具体数字（P1-5） |
| V3 | `../landing-v3/web/src/features/landing/strings.ts:36`–`:57`（`:36`/`:44`/`:48`/`:52`/`:56` 为数字） | 「0 次手动输入」「21:30」「7 天，一屏」「3 秒一笔」「1 周一次颁奖」「5 分钟一局」 | 落地页五件事的数字条 | 不会（产品主张） | 保留，但要能兑现（P1-6） |
| V4 | `../landing-v3/web/src/features/landing/sections/Bento.tsx:68`–`:78`、`../landing-v3/web/src/features/landing/sections/PhoneConcept.tsx:119` | ¥4.00 / ¥16.00 / ¥28.00 的当天金额；手机概念图 ¥16.00 | bento 卡、概念图 | 不会 | 同 V2 |

---

## 2. 覆盖情况（对照任务清单）

| 要求覆盖的点 | 对应行 |
|---|---|
| `DemoSeed` | A1、A16 |
| `CircleDemo` | A4、A7 |
| `SampleData`（insights / today 两份） | A2、A3 |
| `baseReactions` 起始计数 | A5 |
| 商户/小店 Revenue 种子 | A9、W4 |
| 人群/实验种子 | A10、A13、W5 |
| AI 对话里的示例 | A7、A8、W5 |
| 颁奖头衔 | A15、A4（`奶茶续命王` 那条系统消息） |
| Web `services/demoData.ts` | W1 |
| 落地页示例金额/人数（`bento` 的「匿名聚合 · 32 人」等） | L1、L3、V1–V4 |
| 内联 `remember { ...Demo... }` 种子 | A6（`remember` 里的 `CircleDemo` 状态）、A4 |

主条目共 **29 条**（A1–A16、W1–W5、L1–L4、V1–V4），每条都带 `path:line`；表格内的 `:行号` 子引用另有 40 余处，覆盖一个文件里的多处 mock。

---

## 3. 界面上写死、会被误认为真实案例的数字或说法

| 说法 / 数字 | 位置 | 为什么危险 |
|---|---|---|
| **「楼下的芋泥奶茶铺」** | `ui/merchant/MerchantMine.kt:89`、`ui/merchant/SetupStep.kt:121` | 一个具体店名出现在小店版页头与实验文案里，老板会以为系统识别了自己的店 |
| **人群洞察「约 N 人到店」** | `ui/merchant/InsightsStep.kt:192` + `DemoCrowd.kt:68`（300 人） | 个位数人数看起来像真实到店统计，但全部是固定种子的合成人群 |
| **「样本 N 人」「人群样本 X 人 + 游戏表态 Y 人」** | `MerchantMine.kt:119`、`ui/merchant/ResultsStep.kt:82`、`web/src/features/biz/ui/ResultsBoard.tsx:92` | X 是模拟，Y 是用户自己玩的，文案把它们并列成同一类样本 |
| **小店营收 42 天曲线 + 经营小票** | `DemoRevenue.kt:14`、`MerchantStore.kt:53`、`MerchantReceiptPage.kt:350`、`web/src/logic/biz/seed.ts:16` | 没有「演示」标；小票会真的打印/导出 CSV，看起来就是这家店的经营史 |
| **圈子里的「妈妈 ¥…」「妹妹 头衔「奶茶续命王」」** | `ui/circle/CircleModel.kt:214`/`:324`、`ChatModels.kt:386` | 家人姓名 + 金额 + 头衔组合，最容易被当成真实家庭数据 |
| **表情计数 1–3 起步** | `ui/circle/CircleModel.kt:132` | 会让用户以为已经有别人点过赞 |
| **Web 游客账本（喜茶 ¥… / 外卖 ¥…）** | `web/src/services/demoData.ts:6` + `LedgerProvider.tsx:32` | 评委打开 Web 版直接看到一份「自己的」账本，没有任何提示 |
| **落地页脚本「这周吃饭 186，比上周少 24」/「¥11.50」** | `web/src/features/landing/strings.ts:49`–`:51`、`sections/Demo.tsx:8` | 「现场演示」四个字下有精确金额与时间，容易被当成真实录屏 |
| **「匿名聚合 · 32 人」/「32 人 · 匿名汇总」/「圈子 4 人」** | `../landing-v3/web/src/features/landing/strings.ts:86`、`:87`、`:469` | 32、4 这些具体人数会被当成真实样本量（产品对 B 端承诺的是 <20 不展示） |
| **落地页「比昨天少 ¥12.00」「¥186.00，5 笔」** | `../landing-v3/web/src/features/landing/strings.ts:62`、`:70` | 同上，精确到分 |
| **14 天固定小票（10 月 26–29 …）** | `../landing-v3/web/src/features/landing/lib/days.ts:33` | 日期是今年真实日期，看起来就是某人的相册 |
| **游戏价格题「实际价格」** | `domain/games/GameContent.kt:505`–`:514` | hint 已写「仅作猜谜」，材料里不要再称其为真实价格数据 |
| **「0 次手动输入」「3 秒一笔」「5 分钟一局」** | `../landing-v3/web/src/features/landing/strings.ts:36`–`:57` | 产品主张，不是数据；但评委可能按字面验收，需与实机一致 |

---

## 4. 按优先级处理清单

### P0 —— 比赛提交前必须处理

1. **小店营收种子（A9 / W4）**：`MerchantStore.kt:53` 首次启动自动种 42 天营收并持久化，
   `MerchantUi.kt:107` 明确不给 SEED 行任何标签，`MerchantReceiptPage.kt:350` 按真实日打印。
   二选一：改成首次启动空账本 + 「记录今天营收」引导；或保留种子但恢复「演示」标（`SourcePill` 加 SEED 分支）。
2. **小店人群/店名（A10–A13、W5）**：`DEMO_LABEL` 定义后从未渲染，页头与实验文案写死「楼下的芋泥奶茶铺」，
   人群洞察显示个位到店人数。必须渲染「演示数据」或换空状态，并把店名交给用户输入。
3. **圈子假家人（A4–A7、W2）**：`CircleDemo` 三圈十三帖 + `baseReactions` 起手计数 + `ChatFamily` 从 demo 算家庭周报。
   接上 circle API 后改为真实数据；在此之前圈子 tab 给空状态示例，AI 回答改成「圈子里还没有数据」。
4. **启动路径上的假账本**：
   - 设置页「载入测试账本」已是 DEBUG-only（`DataSection.kt:80`），但写入的是真实账本，且 `演示数据` 徽标
     实际没被渲染（`InsightsScreen.kt:50` 只置标记，`AwardsPage.kt:148` 收下 `demo` 但不用）。
     → 补上可见徽标，或把它改成只写进一个独立预览账本。
   - Web 游客默认进 demo 账本（`LedgerProvider.tsx:32`）→ 改空账本 + 显式「载入示例」。
5. **落地页人数（V2）**：「匿名聚合 · 32 人」「圈子 4 人」「32 人 · 匿名汇总」与 B 端「样本 <20 不展示」
   的产品底线直接冲突，改成没有具体人数的表述，或明确写「示例」。

### P1 —— 最好处理

1. 去掉圈子组件里 `viewerId = CircleDemo.MY_ID` 的默认值与 `CircleDemo.members().first()` 兜底（A6）。
2. Web 卡包写死的三张银行卡（W3）→ 空卡包 + 录入引导。
3. 实验结果页把「模拟人群」和「游戏表态」两个来源分列标注（W5 / A13）。
4. 落地页「现场演示」段落加一句「示例动画，非真实录屏」，并把 `Demo.tsx:8` 的重复文案收进 `strings.ts`（L1/L2）。
5. 落地页精确到分的金额（V2 的 `¥12.00`、`¥186.00`）改成整数或加「示例」（V2）。
6. 核对五件事的数字（0 次手动输入 / 3 秒一笔 / 5 分钟一局）与实机一致（V3）。

### P2 —— 可以保留

1. 删除两份死代码 `SampleData`（A2、A3）。
2. 落地页 bento 的动效金额（L3、L4、V1、V4）保留，只在说明材料里点明是示例。
3. 游戏价格题的「实际价格」（A14）与头衔目录（A15）保留，材料里不称其为真实数据。
4. `ChatSuggestions` 示例问题（A8）保留。
5. `notifake/`（假支付通知生成器）是开发工具，不入比赛包，保留在仓库即可。

---

## 5. 审计时确认「不是 mock」的几处

- `server/` 的测试夹具与 `qa/` 下的历史报告：不在范围内，也不进提交包。
- `web/src/ai/assistant.ts:37` 的回答由真实 txns 现算（`今天花了 ¥…（N 笔）`），不是硬编码。
- `app/.../capture/PaymentNotificationParser.kt` 的正则示例注释引用真实通知格式，但不是数据。
- `BadgeCatalog` / `WeeklyReport` 的头衔由真实账本触发，只有 A4 那条 demo 系统消息是写死的。
