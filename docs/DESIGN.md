# 今日小票 · Design System v3「Liquid Glass × Apple」

> 2026-10-29 重写（v2「Claude × Apple」作废：Claude 皮肤已删除，见 git 历史）。
> 本文件管**视觉、组件、页面框架与状态**；产品与信息架构看 `docs/APP-DESIGN.md`，需求看 `docs/SPEC.md`。三者冲突时：视觉/组件以本文件为准，功能以 SPEC 为准。
> 起因（tt 2026-10-29）：「编辑账单和记一笔的 ui 不一样，我觉得你先写一个 design md 文档和页面复用状态统一什么的」「账单的三个分页左上角和右上角应该图标一样」。
> 规则：**新页面 / 新弹层先在本文件找对应的框架和组件；找不到就先补本文件，再写代码。** 每条「必须」违反 = 返工。

## 1. 原则

1. **一个系统**：只有一套视觉语言（Apple 结构 + 液态玻璃材质），颜色由用户选的「主题配色」决定。不再有皮肤切换。
2. **框架先于内容**：同类页面用同一个框架组件（§4），顶栏、底栏、弹层的位置和样式不允许每页各写一套。
3. **同一件事只有一个组件**：记一笔与编辑账单是同一个面板（§7）；所有「设置某个值」的弹层走同一种弹层（§6）。
4. **玻璃给浮在内容上的东西**：顶栏、底栏、主按钮 ⊕、设置类分组卡片。列表行、正文卡片不做真玻璃（性能 + 噪声）。
5. **内容要从玻璃后面经过**：用玻璃的页面，列表必须能滚到顶栏 / 底栏后面（`contentPadding`，不是 `Modifier.padding`），否则玻璃下面只有纯色。
6. **尺寸自适应是底线**：tt 手机 360dp（1260×2800 @560dpi）。每个界面都过 411dp、360dp、系统字体 1.3 倍三组；不许文字竖排、截断、重叠、横向溢出（§10）。
7. **动效有状态**：图标、按钮的动画表达状态变化（线框 → 实心 + 各自动作），不是统一放大一下的「假动」（tt）。尊重「减少动态效果」。
8. **mock 不标注**（tt#54）：界面不出现「演示 / 示例」；mock 在代码里可识别，上线前统一清。
9. **AI 不推荐买什么、不比价、不给理财结论**；预算、标签只陈述用户自己的数字，不说教。

## 2. 颜色

### 2.1 底色（`ui/theme/Tokens.kt` → `LightPalette` / `DarkPalette`）

系统灰阶：`Canvas` 白 / 黑，`CanvasGrouped` `#F5F5F7` / **`#000000`**（2026-10-29 修正：深色下它原与 `Card` 同为 `#1C1C1E`，账单 / 统计 / 设置卡片失去边界，只剩一根发丝线；深色分组页应当是黑底 + `#1C1C1E` 卡片），`Card`，`Fill`，`Ink` / `InkSecondary` / `InkTertiary`，`Hairline`，语义色 `Positive` / `Negative` / `Warning`，小票专用 `Paper*` / `Printer*`。**页面只用 token，零硬编码颜色**（玻璃膜、阴影的 alpha 也从 token 派生）。

### 2.2 主题配色（`ui/theme/Accent.kt` → `ColorTheme`）

5 套 × 3 色，设置 → 外观 → 主题配色，DataStore 持久化，实时全 App 生效：

| 主题 | accent（唯一交互色） | highlight（暖色强调面） | tint（图标块 / 次要图表） |
|---|---|---|---|
| 晴空（默认，tt#72「蓝色系 + 一点黄」） | `#0071E3` / `#2997FF` | `#FFC93C` / `#FFD60A` | `#5AC8FA` / `#64D2FF` |
| 暖阳 | `#F27A1A` / `#FF9F0A` | `#FFD166` / `#FFD60A` | `#FF6B5B` / `#FF7A6B` |
| 薄荷 | `#12A07A` / `#30D19A` | `#F2D45C` / `#FFE066` | `#3FB8C4` / `#5AD1DB` |
| 莓果 | `#E0457B` / `#FF5C8D` | `#FFB86B` / `#FFC27A` | `#A78BFA` / `#B9A3FF` |
| 夜墨 | `#1D1D1F` / `#F5F5F7` | `#D9A441` / `#E8B85A` | `#6B7A90` / `#8E9BB0` |

- `withTheme()` 派生 `AccentSoft` / `HighlightSoft` / `InfoSoft` 与 `OnAccent`（白字对比 <4.5 时自动换深色字，单测覆盖 5 套 × 深浅）。
- 用法：`Accent` = 选中态、主按钮、⊕、链接、收入金额；`Highlight` / `HighlightSoft` = 账单总览卡这类「今天」的强调面；`Info` / `InfoSoft` = 设置图标块、图表第二系列。**不要用 accent 铺大面积底色。**
- 小店版：`merchantPalette()` 整体覆盖为小店绿，不跟随主题配色。

## 3. 材质：液态玻璃

依赖 `io.github.kyant0:backdrop:2.0.0`（模糊 API 31+，折射 API 33+；更低版本自动退化为半透明膜）。

**结构规则（必须）**：被采样的内容用 `Modifier.layerBackdrop(source)` 录制；玻璃元素必须是 source 的**兄弟**而不是子孙（否则递归采样）。

| 级别 | 用在 | 参数 | 组件 |
|---|---|---|---|
| Bar glass | 底栏胶囊 | vibrancy + blur 4dp + lens 18/24dp + 42% 膜 + 柔阴影 + 跟手光斑 | `XpNavBar` |
| Hero glass | ⊕ 主按钮 | accent 80% 膜 + blur 2dp + lens 12/18dp + 色散 + 内亮边 | `XpNavBar.NavBarPlus` |
| Top glass | 所有 `NavScaffold` 顶栏 | vibrancy + blur 14dp + lens 10/14dp + 55% 膜 + 底部发丝线 | `NavScaffold` |
| Card glass | 设置类分组卡片（在 `GlassGround` 上） | vibrancy + blur 10dp + lens 8/12dp + 55% 膜（深色 42%）+ 16dp 淡阴影 | `GroupedSection` 读 `LocalGlass` |

- `GlassGround`：主题三色的大色斑铺底（固定不随滚动），只录底不录卡片。用于设置及其子页（`NavScaffold(glassGround = true)`）；其他页面要用先写进本节。
- 列表行、账单卡片、聊天气泡等大量重复元素**不做真玻璃**。

## 4. 页面框架（Page chrome）

### 4.1 一级页：必须用 `NavScaffold`，顶栏槽位固定

| 区域 | 左侧（leading） | 中间 | 右侧（trailing） |
|---|---|---|---|
| 记账侧：记账 / 小票 / 统计 | **卡包 + 搜索**（三页完全一样；在小票、统计点搜索 = 跳到记账并进入搜索） | 页名 | **小票 + 设置** |
| 社交侧：AI / 圈子 / 游戏 | 页面自己的一个入口（AI = ☰ 会话抽屉；圈子 = 圈子切换；游戏 = 无） | 页名 | **小票 + 设置** |
| 小店版：今日 / 营收 / 分析 / 我的… | 无 | 页名 | **经营小票 + 设置** |

- 右上角在所有一级页**完全相同**：同一个组件 `AppTopActions()`（`ReceiptStrip` + 齿轮，顺序、尺寸、间距一致），不许单独写 `NavIconButton(Settings)`。
- 左上角记账侧用同一个 `LedgerSideLeading()`（卡包 + 搜索）。
- 页面级动作（日期跳转、筛选、新建）**不进顶栏**：放进页面内容（如账单总览卡右上角的「按天查看」）。

### 4.2 二级页

push 进入：左 = 返回（`onBack`），右 = 最多一个与本页相关的动作；**不放**小票和设置。需要沉浸的页（游戏局内、经营小票）隐藏底栏。

### 4.3 内容与安全区

- 列表顶部用 `contentPadding(top = top + …)`，让内容滚到玻璃顶栏下。
- 底部：一级页内容延伸到底栏后面（`AppShell.underBar`），列表 `contentPadding(bottom ≥ 120dp)`；有底部输入框的页（AI、圈子）输入框浮在底栏上方，背景用玻璃或半透明，**不许不透明色带**。
- 底栏后面露出的底色必须和页面一致（不许灰带）。

## 5. 底栏（`ui/components/XpNavBar.kt`）

- 始终 5 个图标；记账态 `[记账 小票 ⊕ 统计][圈子]`，圈子态 `[账单 ＋][AI 圈子 游戏]`，只有点「账单」切回；一个弹簧进度驱动整套切换（APP-DESIGN §2）。
- 尺寸：胶囊高 56dp、⊕ 50dp、凸出 10dp、胶囊间距 12dp、图标 24dp、标签 10sp 单行。
- 图标一律 `NavGlyph`（`ui/components/NavGlyphs.kt`，Canvas 手绘）：静止线框 → 选中实心 + 各自一次性动作（账单写线、小票出纸、统计长柱、圈子小人弹出、AI 打点、游戏晃动；小店：日历落点、硬币翻面、遮阳棚落下、点头、烧瓶冒泡）；再点重播；减少动态效果只变色。**新 tab 必须新增 glyph，不许用静态 Icon。**
- 小店版用同一个 `XpNavBar` + `MerchantNavSpec`，规则相同。

## 6. 弹层层级（Overlay ladder）

| 场景 | 形态 | 组件 |
|---|---|---|
| 少量选项的选择 / 确认（打印哪些分类、删除确认） | 居中窗口，背后页面虚化 18dp | `Dialog` + 页面 `Modifier.blur`（今后收为 `XpBlurDialog`） |
| 任务型编辑（记一笔、编辑账单、预算、筛选、选日期、选时间） | 底部抽屉，顶角 32dp，有 grabber，可下滑关 | `XpSheet` |
| 独立功能（设置、AI 会话抽屉、卡包） | 设置 = push 页；会话 = 左侧抽屉（底栏让位）；卡包 = **浮层卡片**（12dp 留边、底部留 96dp 露出页面、从卡包按钮长出、点外面或下滑关闭，tt 2026-10-29） | `WalletPanel` |
| 结果反馈 | toast / 可撤销 toast，不挡按键 | `rememberXpToast` / `UndoToastHost` |

规则：同一类操作只用一种形态（tt：预算是抽屉，不是页面）。

材质（tt 2026-10-29：抽屉弹窗都要液态玻璃）：弹层是独立窗口，Kyant 玻璃采样不到背后页面，所以用系统跨窗口模糊——`rememberWindowBlurBehind()`（`components/GlassWindow.kt`）给弹层窗口开 blur-behind 28dp，表面 `glassSurface` / `glassRim`（Card 74% + 顶部高光描边），抽屉内填充读 `LocalSheetGlass` 保持半透明；设备不支持或省电模式关闭模糊时自动回到不透明。新弹窗一律照此做。

## 7. 记一笔 = 编辑账单（同一个面板）

- 唯一实现：`ui/record/ManualRecordPane.kt`（1:1 参考 041431）。新建和编辑都用它：`mode = New | Edit(txn)`。
- Edit 模式：预填金额 / 分类 / 时间 / 备注；底部「再记一笔」换成「删除」，「保存」= 保存改动；标题区不变。
- `ledger/AddSheet.kt` 已删除；账单行点击打开同一个面板（`RecordSheetState.openEdit(txn, onDelete)`，删除交回调用方出可撤销 toast）。AI 确认卡「改一下」针对未入账草稿，暂仍退回输入框。

## 8. 共用组件清单

| 组件 | 文件 | 用于 | 不要 |
|---|---|---|---|
| `NavScaffold` | `components/NavHeader.kt` | 所有带顶栏的页（含分类管理、引导页、游戏、小店） | 自己画顶栏 |
| `AppTopActions` / `LedgerSideLeading` | `components/TopActions.kt`（动作来自 shell 提供的 `LocalTopBarHost`） | §4.1 固定槽位 | 单独放齿轮 |
| `XpNavBar` + `NavGlyph` | `components/XpNavBar.kt`、`NavGlyphs.kt` | 底栏 | 静态图标 tab |
| `GroupedSection` / `GroupedRow` | `components/Grouped.kt` | 设置类列表 | 每行一张阴影卡 |
| `GlassGround` / `LocalGlass` | `components/GlassGround.kt` | 玻璃卡片的底 | 把卡片自己录进 source |
| `XpSheet` | `components/Sheet.kt` | 抽屉 | 全屏页做编辑 |
| `XpButton`（Primary / Secondary / Plain） | `components/XpButton.kt` | 按钮两级 | 框 + 立体的按钮 |
| `Segmented`、`XpSwitch` | `components/` | 二到四选一、开关 | 自绘 tab |
| `EmptyState` | `components/` | 空态：插图 / 图标 + 一句话 + 一个主操作 | 只放一行灰字 |
| `CategoryTile` | `ui/category/` | 分类图标（该色 + 12% 浅底） | emoji 当图标 |
| `ReceiptStrip` / `ReceiptPrinter` / `ReceiptPaper` | `ui/today/` | 小票相关 | 另画打印机 |

## 9. 状态统一

1. **数据单一来源**：账目 `LedgerRepository`、分类 `CategoryRepository`、预算 `BudgetRepository`、小票设置 `ReceiptPrefs` 等都以 `Flow` 暴露，页面 `collectAsStateWithLifecycle`。页面不复制一份数据到本地 state（草稿除外）。
2. **偏好**：每个领域一个 DataStore（`ThemePrefs` 外观与主题、`NavPrefs` 启动侧、`LedgerPrefs` 隐藏金额与最近搜索、小票设置…）。设置页只读写这些，不自己存。
3. **UI 状态**：弹层开关、选中 tab、草稿用 `rememberSaveable`；**不许**用会被 `restoreState` 意外恢复的「打开某页」布尔值去决定一个页面显示什么（「圈子打不开」的根因）——能成路由的就做成路由。
4. **跨页一致**：同一个数字在不同页必须同源计算（如今日支出：总览卡、小票、统计都用 `LedgerTotals`），不同页显示不一致 = bug。
5. **三态统一**：加载用骨架（至少 200ms，防闪）、空态用 `EmptyState`、错误用 toast + 可重试；同一页三态位置一致。
6. **金额**一律 `Cents`（Long 分），展示 `yuan()` / `yuanGrouped()`；不用浮点。
7. **标签 / 头衔**只读用户自己的数据：零消费才是「钱包休眠者」；有消费按 `ReceiptLayout.dayTitle()` 规则给（`insights/BadgeCatalog` 管周头衔）。

## 10. 文字与排版

- 字体 Inter（数字 `tnum`）+ 系统 CJK；字号用 `MaterialTheme.typography`，不写散落的 sp（小票纸面除外）。
- 防重叠（必须）：单行文字 `maxLines = 1` + `overflow = Ellipsis`；两段并排时给次要的那段 `weight(1f, fill = false)` 让它先省略；格子里的标签用 `TextAutoSize`（12→8sp）；不写死宽度，用 `weight` / `BoxWithConstraints`。
- 每次改界面截 411dp / 360dp / 1.3 倍字体，逐张看。

## 11. 动效 tokens

`XpMotion.snappy`（1.0 / 400）默认、`gentle`（0.85 / 300）抽屉、`bouncy`（0.72 / 380）仅仪式；`XpEase.out` 颜色与入场；按压缩放 0.94–0.96；底栏切换 `spring(0.82, 300)`；按 dt 推进，减少动态效果时只变色 / 直接到终态。

## 12. 双语

中英双语是必备项（tt）。App：文案进 `strings.xml` + `values-en`，App 内语言切换（Android 13+ per-app language）——**未完成**；Web：`defineStrings` 中英两套（web/i18n 已完成，待合并）。新增文案必须同时写中英。

## 13. 交付前验收

1. `./gradlew :app:testDebugUnitTest :app:assembleDebug` 全绿（gradle 串行锁）。
2. 模拟器逐屏截图 411 / 360 / 1.3 倍，深浅各一；走一遍主路由（记账 → 记一笔 → 编辑 → 小票打印 → 统计 → 圈子 → AI → 设置 → 小店来回切换）。
3. 对照 §4 顶栏槽位、§6 弹层形态、§10 防重叠逐条打勾。
4. `install -r` 到 tt 真机（vivo 需 tt 在手机上点「继续安装」）。

## 14. 当前不一致清单（2026-10-29，改完划掉）

| # | 问题 | 归属 |
|---|---|---|
| 1 | ~~小票、统计页顶栏左侧缺卡包 + 搜索，右侧缺小票；三页不一致~~ 记账 / 小票 / 统计 / AI 已换 `components/TopActions.kt`（2026-10-29）；圈子 / 游戏 / 小店待 worker 交付后替换 | Claude |
| 2 | ~~编辑账单用旧 `AddSheet`，和记一笔不同~~ 已改为 `RecordSheetState.openEdit` + `ManualRecordPane(editing)`，`AddSheet.kt` 已删（2026-10-29）；AI 确认卡「改一下」仍是退回输入框（未入账草稿，另议） | Claude（§7） |
| 3 | ~~统计页文字重叠~~ 柱图/环形图标签按格宽缩字（`TrendCharts.kt` 的 `fit`）、环形图居中两行标签、KPI 单行 autoSize、AI 小结链接独占一行、小票条字号不随系统放大（2026-10-29） | Claude（§10） |
| 4 | 小票页：打印机在上、出纸口朝下，**吐出来的纸就是设置面板**（tt） | Claude |
| 5 | ~~圈子 / 游戏页没有玻璃感、功能缺失、游戏简陋、分享改成选择圈子~~ circle-v8 已合；AI / 圈子 / 游戏进 `underBar`，底部用 `LocalTabBarInset`（2026-10-29） | omp |
| 6 | ~~小店版按本规范对齐~~ merchant-v8 已合（2026-10-29）；遗留：`MerchantConfirmDialog` 待收进 `XpBlurDialog`、`FitText`/`MerchantHeroCard` 等可上收、`bi()` 待 App i18n 后换 strings | Sonnet 子代理 |
| 7 | 卡包展开动效未照参考录屏 16.2–16.8s 还原 | Claude |
| 8 | App 中英文切换 | 待定（Claude 或 Sonnet 子代理） |
