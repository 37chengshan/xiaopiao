# 今日小票 · Design System v2「Claude × Apple」

> 2026-10-27 重做。v1 被评价为「好丑」：珊瑚橙滥用、emoji 灰圆底图标、到处贴「演示数据」、卡片厚重、层级不清、无深色模式。
> 方向：**Claude 的色彩与气质**（暖象牙白、陶土橙点缀、衬线标题、安静克制）+ **Apple 的结构与动效**（大标题、inset grouped 列表、毛玻璃导航、发丝分隔、弹簧物理）。
> 必读 skill：`~/.agents/skills/apple-design/SKILL.md`、`~/.agents/skills/emil-design-eng/SKILL.md`（动效再看 `animate/`、`review-animations/`；Web 移动端看 `mobile-native/`）。

> **设计简报（app-ui-design §1）**：调性 = 安静、温暖、有一点手作感的「生活小票」；核心任务 = 不费力地看清今天花了什么，并领一张有仪式感的小票。
> 四个杠杆：字体 = 衬线标题（纸质票据/书信感）+ 系统无衬线正文；色彩 = 热敏纸的暖白 + **印泥朱砂**（来自小票上的印章，不是随手挑的「陶土色」）；圆角 = 分层（纸 2 / 控件 10–12 / 分组 14 / sheet 20）；措辞 = 友好、俏皮、不说教。
> 理由备忘：「奶油底+陶土色」是已知 AI 味（app-ui-design §AI 味清单）。我们用它是因为产品隐喻就是一张纸质小票和一枚红印章，且用户点名要 Claude 风格——所以强调色必须和印章同色系、且稀缺使用。
> 对比度已用 `app-ui-design/scripts/color_ramp.py contrast` 复核：正文/小字 ≥4.5:1，图标/大字 ≥3:1（`ink-3` 只用于图标与装饰）。

## 1. 原则（违反任何一条 = 返工）

1. **陶土色是稀缺资源**。每屏最多 1–2 处：主按钮 / 选中 tab / 印章 / 图表里「当前」那一根。其余一律中性墨色。禁止大面积橙色底、橙色渐变 hero、橙色描边卡片。
2. **结构靠排版和留白，不靠卡片**。用 inset grouped 列表（同组一个圆角容器 + 内部发丝分隔），不要每行一张阴影卡片。阴影只给浮层（sheet、toast、小票纸）。
3. **图标统一线性**：Web 用 lucide（`strokeWidth={1.75}`，20–22px），Android 用 Material Symbols Outlined / `Icons.Outlined`。分类图标放在 28–32px 的 `fill` 色圆角方块里，墨色图标。**emoji 只出现在小票纸面和头衔徽章上**。
4. **「演示数据」只标一次**：导航栏右侧一个小胶囊 `演示`（`fill` 底、`ink-2` 字、11–12px）。卡片内不再贴。商家端同理（数据说明放在「数据怎么来的」里）。
5. **深浅双模式 + 跟随系统**，所有颜色走 token，零硬编码。主题切换有 200ms 颜色过渡（不要闪）。
6. **动效有目的**：反馈 / 空间连续 / 状态变化。高频操作（tab 切换、列表滚动、键盘）不加装饰动画。UI 动画 ≤ 300ms；退出比进入快。默认临界阻尼弹簧（无回弹），只有手势带动量（甩、拖拽释放）和「仪式」（盖章、撕票、颁奖）才允许回弹。
7. 文案：中文、温柔、俏皮但不油腻；不说教、不羞辱消费。

## 2. Color tokens

语义名两端一致（Web 用 CSS 变量 `--x`，Android 用 `XpColors.X`）。

| token | 用途 | Light | Dark |
|---|---|---|---|
| `bg` (Canvas) | 页面底 | `#FAF9F5` | `#1F1E1C` |
| `bg-grouped` | grouped 列表外的底（同 bg） | `#F3F1EA` | `#1A1918` |
| `surface` (Card) | 分组容器 / 输入框 | `#FFFFFF` | `#2A2926` |
| `surface-2` | 次级容器、按下态 | `#F0EEE6` | `#34332F` |
| `fill` | 图标方块、胶囊、segmented 底 | `#E9E6DC` | `#3D3C37` |
| `ink` | 主文字 | `#141413` | `#F5F4EE` |
| `ink-2` (InkSecondary) | 次文字（最浅的**文字**色） | `#63625C` | `#B4B1A7` |
| `ink-3` (InkTertiary) | **仅图标**/占位/禁用/装饰线（≥3:1，不可做正文） | `#8A8880` | `#7C7A72` |
| `hairline` | 分隔线 | `rgba(20,20,19,0.09)` | `rgba(245,244,238,0.10)` |
| `accent` | 印泥朱砂（稀缺，与印章同色系） | `#B04A2E` | `#D97757` |
| `accent-soft` | 选中底、极轻强调 | `#F7EAE3` | `#33241E` |
| `on-accent` | 朱砂底上的字 | `#FFFFFF` | `#1F1E1C` |
| `positive` | 收入、正向 | `#3F7D4E` | `#7DB38A` |
| `negative` | 删除、错误 | `#B3412F` | `#E0705C` |
| `stamp` | 印章红（只在小票上） | `#B8322A` | `#C8463B` |
| `paper` | 小票纸（深色模式也是纸色，略暗） | `#FBFAF6` | `#E9E6DD` |
| `paper-ink` | 纸上墨 | `#2A2724` | `#24211E` |
| `scrim` | 遮罩 | `rgba(20,20,19,0.32)` | `rgba(0,0,0,0.55)` |
| `material` | 毛玻璃导航/tab bar | `rgba(250,249,245,0.78)` + blur 20 + saturate 180% | `rgba(31,30,28,0.72)` + blur 20 |

**数据可视化**：默认中性柱（`ink-3` 40% 透明度），「当前/最大」那一根用 `accent`。热力图用 `accent` 单色相透明度阶梯（8% → 85%，5 档），**样本不足的格子 = 空格子 + 细斜线纹理，不写字**，图例里解释「斜线 = 样本不足 20 人，已隐藏」。多类别只在确有必要时用：`accent`、`#8A8578`、`#B9A88E`、`#6F8F84`、`#9C7C9A`（深色模式提高 10% 明度）。

## 3. Typography

| 角色 | Web | Android | 规格 |
|---|---|---|---|
| Large Title（页标题） | `--font-serif` | `FontFamily.Serif` | 32/38，SemiBold，tracking −0.02em |
| Title（区块标题/sheet 标题） | sans | default | 20/26 SemiBold，−0.01em |
| Headline（行主文字） | sans | default | 16/22 Medium |
| Body | sans | default | 15/22 Regular |
| Footnote（次要/区块 header） | sans | default | 13/18 Regular，`ink-2` |
| Caption | sans | default | 11/14 Medium，+0.02em |
| Big Number（金额） | sans tabular-nums | `fontFeatureSettings = "tnum"` | 40–56，SemiBold，−0.03em |
| Receipt | mono | Monospace | 13/18，纸上用 `paper-ink` |

- `--font-sans`: `-apple-system, BlinkMacSystemFont, "SF Pro Text", "PingFang SC", "Noto Sans SC", system-ui, sans-serif`
- `--font-serif`: `"Songti SC", "STSong", "Noto Serif SC", "Source Han Serif SC", Georgia, serif`（**不加载外链字体**——QMuse 环境可能拦截）
- `--font-mono`: `ui-monospace, "SF Mono", Menlo, "Sarasa Mono SC", "Noto Sans Mono CJK SC", monospace`
- 金额一律 tabular 数字；`¥` 符号比数字小 40% 且 `ink-2`。

## 4. Layout & components

- 间距 4 的倍数：4 / 8 / 12 / 16 / 20 / 24 / 32。页面左右 16（Web 手机列 16，桌面列最大 430 居中）。
- 圆角：grouped 容器 14、按钮 12、胶囊 999、sheet 顶 20、小票纸 2（纸是直角的，靠锯齿边）。
- **导航栏**：大标题在内容顶部（衬线 32），向下滚动 > 40px 后，顶部出现 material 毛玻璃条 + 居中 17px SemiBold 小标题，底部用渐隐遮罩而不是 1px 线。右侧放 1–2 个 icon 按钮（32px 圆形 `fill` 底）和 `演示` 胶囊。
- **Tab bar**：material 毛玻璃，图标 24 线性，标签 10px；选中 = `accent` 图标+文字（不要选中底色块），未选中 `ink-2`。
- **Grouped list**：section header 13px `ink-2` 左对齐 + 上 24 下 8；行高 ≥ 52（双行）/ 44（单行）；左图标方块 30、发丝分隔从文字起始处开始（inset）；右侧金额 tabular、chevron `ink-3`。
- **按钮**：Primary = `ink` 底 `bg` 字（Claude 风黑按钮）或 `accent` 底（每屏最多 1 个）；Secondary = `fill` 底 `ink` 字；Plain = 无底 `accent` 字。高 48（大）/ 36（小），按下 `scale(0.97)` 100ms ease-out，按下即反馈。
- **Segmented control**：`fill` 底，选中块 `surface` + 微阴影，选中块用 layout/shared 动画滑动（临界阻尼弹簧，response 0.3）。
- **Sheet**：`surface` 底，顶圆角 20，grabber 36×5 `ink-3` 40%，背景 `scrim`；拖拽 1:1 跟手，释放按速度投影决定关闭（apple-design §5–6）；进出同一路径（从下进、往下出）。
- **Toast**：顶部或底部胶囊（`ink` 底、`bg` 字，深色模式反转），带「撤销」，可滑走；进入 ease-out 220ms，退出 160ms。
- **空状态**：一个线性大图标（48，`ink-3`）+ 衬线标题 20 + 一行 `ink-2` 说明 + 可选 Secondary 按钮。不要大 emoji。
- **加载**：骨架用 `surface-2` → `fill` 的柔和 shimmer；不要转圈。

## 5. 动效参数（两端一致）

| 场景 | 参数 |
|---|---|
| 默认 UI（位置/尺寸） | spring damping 1.0, response 0.35（Compose: `spring(dampingRatio=1f, stiffness=Spring.StiffnessMediumLow≈400)`；Framer: `{type:"spring", bounce:0, duration:0.35}`） |
| Sheet / 抽屉 | damping 0.85, response 0.3；拖拽释放带速度 |
| 动量交互（甩卡、撕票、盖章） | damping 0.7–0.8，可回弹 |
| 颜色/透明度 | 180ms ease-out `cubic-bezier(0.23,1,0.32,1)` |
| 按下反馈 | scale 0.97，100ms ease-out |
| 列表入场 stagger | 每项 30–50ms，最多 8 项，只在首次进入 |
| 主题切换 | 背景/文字颜色 200ms 过渡 |
| 减少动态效果 | 一律换成 150ms 淡入淡出；小票直接出最终态 |

禁止：`transition: all`、`scale(0)` 入场、`ease-in`、UI 动画 > 300ms（仪式除外）、tab 切换动画、无处不在的 hover 位移。

## 5b. 质量门（app-ui-design，交付前逐项过）

- **灰度检查**：截图转灰度（`ffmpeg -i in.png -vf hue=s=0 out.png`），眯眼看仍主次分明、每屏一个焦点、一个主操作。
- **三种形态**：每个列表/卡片都要有 空 / 1 条 / 很多条；外加 加载 / 错误。
- **最坏数据压测**（改 fixture，不改组件）：超长商户名（「美团外卖·老北京炸酱面（望京SOHO店）加辣不要香菜」）、一个字的商户、¥0 / ¥0.01 / ¥99,999.99 / 7 位数金额、一天 40 笔、零消费日、跨年日期；字体缩放 1.3 和 2.0（Android `adb shell settings put system font_scale 1.3`）、320dp 窄屏。金额永不截断；名称单行末尾省略，详情可看全文。
- **触控目标**：Android ≥48dp，Web ≥44px（小图标按钮扩展点击区域）。
- **平台收尾**：edge-to-edge 与安全区、预测性返回（Android 13+ `BackHandler` 正常）、无障碍标签（图标按钮都有 contentDescription / aria-label）。
- **评审输出**：交付报告附一张 `| # | 严重度 | Before | After | Why（规则） |` 表（Broken / Ugly / Fragile），列出自己发现并修掉的问题。

## 6. 主题切换

- 三档：跟随系统（默认）/ 浅色 / 深色。入口：「我的」页顶部 segmented；偏好本地保存（Web localStorage 仅存 UI 偏好；Android DataStore）。
- Web：`<html class="dark">` 切换，`color-scheme` 同步；`<meta name="theme-color">` 随主题更新。
- Android：`XpColors` 从当前 palette 读取（snapshot state），状态栏/导航栏图标颜色随主题。

## 7. 每日小票 v2（参考 nowdex 用量小票的交互模式，只学模式，不抄品牌/文案）

分镜（总时长约 3–4 秒，可「跳过」）：
1. **出纸**：顶部打印机出纸口（Android：屏幕顶部一个深色胶囊「出纸口」；Web：手机列顶部同款），背景内容 `scrim` 压暗并轻微后退（scale 0.97）。纸从出纸口**向下**逐行吐出（纸面 clip + translateY，线性匀速是唯一允许的 linear），每行出现时一次轻 tick 触感 + 打印声。
2. **底部状态胶囊**（material 毛玻璃，居中）：打印中显示「正在打印小票…   跳过 ✕」；打完变为「向下拖动撕下小票   撕下 ✕」。
3. **合计**：居中大号金额 NumberTicker 滚动；下一行小字「仅作记录 · 非正式票据」。
4. **盖章**：最后头衔章「咔」落下（scale 1.5→1、旋转 −8°、damping 0.6、墨迹扩散），强触感 + 盖章声。
5. **撕下**：向下拖动，纸跟手 1:1，越过阈值或速度足够 → 沿顶部锯齿撕开、带旋转飞出（动量弹簧），随后从底部弹出「分享小票」sheet。
6. **分享 sheet**：小票缩略图放在可选**渐变背景卡**上（12 个预设渐变：陶土、杏、沙、苔绿、雾蓝、暮紫、墨夜……）；segmented「完整 / 简版」；底部黑色主按钮「分享」+ 次按钮「保存图片」；右上「复制文字」。
7. **声音**：合成的热敏打印「滋滋」声（短促噪声脉冲，按行节奏），盖章一声闷响。设置里可关；Android 跟随系统静音/勿扰（用 `USAGE_ASSISTANCE_SONIFICATION`），Web 在首次用户手势后才可播放，且默认音量轻。

纸面排版（等宽，32–34 列）：
```
            今 日 小 票
        认真生活，留下凭证。
  ---------------------------------
  日期            2026-10-27 周二
  出票            21:30
  ---------------------------------
              今日支出
             ¥ 88.00
        仅作记录 · 非正式票据
  ---------------------------------
  分类 / 明细                 金额
  🍜 吃饭                    62.00
     美团外卖·黄焖鸡         32.00
     美团外卖·螺蛳粉         30.00
  🚇 出行                    10.00
  🧋 奶茶                    16.00
  ---------------------------------
  笔数 5    较昨日 −12%    本周第 3 天
  ---------------------------------
  「吃好喝好，生活的味道都在这张小票里了。」
          [ 地铁通勤侠 ] ← 印章
         — 今日小票 · 存根联 —
  \/\/\/\/\/\/\/\/\/\/\/\/\/\/\/\/\/
```
纸张：`paper` 色，顶部和底部锯齿，极淡纤维纹理，落地有柔和阴影；深色模式下纸仍是纸色（略暗）。

## 8. 第二轮修订（2026-10-28，真机验收反馈；覆盖上文冲突条款）

原因：tt 在真机上反馈「标题重复、文字太密、按钮没质感、设置看不懂、重要入口应在导航中间、B 端不应占 tab」。

- **导航**：底部 5 位，中间是突出的圆形主按钮（对话+记账，clay 渐变，已在首页时再点 = 打开记一笔 sheet），两侧各两个 tab（账本、洞察 | 小票、我的）。小店不在导航里，从「我的 → 切换到小店版」进入，页内有「返回个人版」。
- **标题**：tab 根页面不再用衬线大标题，改紧凑顶栏（22px 半粗，右侧放 演示数据 chip 与操作）。衬线大字只留给小票纸与首次引导。
- **文字密度**：设置每项只留标题；长说明进「了解更多 / 隐私与数据」sheet；一屏 ≤ 2 段说明。
- **层级**：卡片轻微纵向底色差 + 柔阴影（深色用发光描边）；图标块用语义浅色（blue/green/amber/violet），clay 仍每屏 1–2 处；账本顶部为月支出大数字 + 7 日迷你柱。
- **按钮质感**：`.btn-ink/.btn-clay/.btn-soft/.btn-round` = 细微纵向渐变 + 1px 内高光 + 柔投影；按下 scale 0.97 + 阴影收缩；深色用发光边。
- **引导**：首次 4 屏（价值 → 自动记 → 数据底线 → 外观/声音），可跳过；「我的 → 新手引导」可重看。
- **文案**：新增文案进 `web/src/lib/strings.ts`（zh/en），不再硬编码。
