# 多平台移植 · 可行性与路线（v1，2026-11-02）

tt 定的顺序：**微信小程序 → iOS → 鸿蒙**。本文回答「能不能移、移哪些、怎么移、要多久、要什么资质」。标「待核实」的条目在动手前查官方文档确认。

## 0. 结论

| 平台 | 能移植吗 | 形态 | 主要技术路线 | 最大的限制 |
|---|---|---|---|---|
| 微信小程序 | 能 | **轻量伴侣版**：记一笔 / 今日小票 / 圈子分享 / 生活记一笔 | 原生小程序（WXML + JS，逻辑与 Web 端 TS 共享），Dimina 离线编译、`miniprogram-ci` 上传 | 读不到支付通知，无法自动记账；截图不能在本机解析，所以不做截图识别 |
| iOS | 能 | 完整 App（除通知自动记账） | Compose Multiplatform（复用现有 Compose UI）+ KMP 共享核心 | 没有通知监听：用分享扩展 + 端侧 OCR 和语音代替；$99/年；中国区要 ICP 备案号 |
| 鸿蒙 NEXT | 能，风险较高 | 完整 App（除通知自动记账，待验证） | 先试腾讯 ovCompose（Compose 分支，Kotlin/Native）；不行就退回 ArkTS 原生瘦客户端 | 不能装 APK；ovCompose 落后上游、鸿蒙上有性能问题报告；虚拟商品必须走华为 IAP Kit |

## 1. 共享核心（iOS 和鸿蒙的前提）

- 新建 KMP 模块 `:shared`（commonMain），把下面这些纯 Kotlin 代码搬进去：
  - `domain/`（模型、`Cents`、分类、规则）；
  - `insights/`（小票、统计、周报）；
  - `capture/` 里的解析部分（`PaymentNotificationParser` 等，iOS 用来解析截图 OCR 出来的文字）；
  - `domain/life/`（知识库规则）。
  - 目前这几层合计约 6.4k 行，只有极少数文件 import 了 `android.*`。
- 持久化：Room 换成 Room KMP（官方支持 iOS），DataStore 用 KMP 版。
- 平台能力放到接口后面，每个平台各自实现：通知监听（只有 Android）、端侧 ASR（Android 现有模型 / iOS `SFSpeechRecognizer` 端侧模式）、OCR（iOS Vision）、安全存储（Keystore / Keychain）、液态玻璃（kyant backdrop 只支持 Android，iOS 用原生模糊材质或简化效果）。
- 工作量（粗估）：1–2 周。完成标准：Android 行为不变，`./gradlew :app:testDebugUnitTest` 全绿。

## 2. 微信小程序（第一）

### 2.1 范围
记一笔（手动 + 文本）、今日小票（WXML 渲染 + 分享卡片）、圈子（邀请卡片直接发到微信群，这是最强的获客点）、生活记一笔、登录（微信登录 → 服务端用 openid 绑定我们的账号）。

不做：通知自动记账、截图识别（小程序里没法在本机解析截图，会违背「账单原文只在本机解析」的底线）、本机语音模型（录音必须上传，和「录音留在手机」冲突；语音放 P2，上线前单独说明）。

### 2.2 技术
- 原生小程序。原型已在仓库 `miniprogram/`：自定义导航栏、今日小票、记一笔（金额 → ↵ → 备注 + 常用短语 → 保存）、原生分享。
- **右上角系统胶囊（「··· / ⊙」）不能覆盖、也不能在那里放自己的按钮**：全部页面用 `navigationStyle: custom`，导航栏尺寸由 `wx.getMenuButtonBoundingClientRect()` 推出（`miniprogram/utils/nav.js`），标题靠左。弹层和浮动按钮也不要出现在胶囊所在的区域。
- 离线编译与预览：[Dimina](https://github.com/didi/dimina)（滴滴开源，Apache-2.0），装在 `~/code/_tools/dimina`（仓库外），用法见 `miniprogram/README.md`。实测：
  - 本机 pnpm 10 不满足 Dimina 要求的 pnpm 12，要用 `npx pnpm@12.2.0` 并关掉自动切版本；
  - Dimina 的 Web 容器里 `wx.*StorageSync` 不可靠，统一改用异步 `wx.getStorage/setStorage` 加内存缓存；
  - 模拟的胶囊位置和状态栏高度有几像素偏差，最终以真机为准。
- 正式预览和上传：官方 `miniprogram-ci`（命令行，不需要开发者工具界面），但需要 AppID 和上传密钥。
- 和 Web 端共享逻辑：金额、分类、小票模型参照 `web/src/domain` 的 TS 实现；后续抽成一个两端共用的 TS 包。

### 2.3 资质（待核实）
- 小程序需要 ICP 备案。个人主体能选的类目有限，「工具」类可以。
- **虚拟支付**：2026-04-01 起，所有终端卖虚拟商品必须接入小程序虚拟支付。第三方文章称：**2026 年 9 月起个人主体也能开通**，条件是工具类目、完成认证（30 元/年）和备案、月限额 10 万元；企业和个体户没有这些限制。正式开通前以微信官方文档为准。
- iOS 端虚拟支付的费率在 2026 年是 12%（苹果佣金；微信的 5% 技术服务费 2026 年暂不收取）。**不得引导用户去 App、H5 或公众号付款。**

### 2.4 里程碑
1. 原型（✅ 2026-11-02，Dimina 离线跑通记账流程）
2. 接自建 `/v1/sync` 和微信登录（服务端需要 AppSecret，只放服务端）
3. 圈子邀请卡片 + 入圈页
4. 生活记一笔
5. 申请 AppID → 备案 → 体验版 → 提审

## 3. iOS（第二）

- 路线：Compose Multiplatform 的 iOS 版已经稳定，可以复用现有 Compose UI；iOS 外壳用 SwiftUI 承载 `ComposeUIViewController`。
- 需要替换或补充的部分：

  | 能力 | Android 现状 | iOS 方案 |
  |---|---|---|
  | 自动记账 | 通知监听 | **没有**。分享扩展：把支付截图分享给 App → Vision 端侧 OCR → 共享解析器；另外支持「快捷指令」手动触发 |
  | 语音 | 端侧模型 | `SFSpeechRecognizer`（`requiresOnDeviceRecognition = true`） |
  | 液态玻璃 | kyant backdrop | iOS 原生材质（`UIVisualEffectView`）或简化的半透明效果 |
  | 卡包 FLAG_SECURE | 系统标志 | 截屏通知 + 敏感区域打码 |
  | 小组件 / 提醒 | Glance / WorkManager | WidgetKit / 本地通知 |

- 资质：Apple Developer Program（$99/年，个人账号也能上架、也能用 IAP）。上架中国区需要 ICP 备案号，App 名称会强校验是否和备案一致。
- 工作量（粗估）：共享核心 1–2 周 + iOS 外壳和平台能力 3–5 周 + 审核。

## 4. 鸿蒙 NEXT（第三）

- HarmonyOS NEXT 不能运行 APK，必须原生开发。候选方案：
  1. **ovCompose**（腾讯视频团队，基于 Compose Multiplatform 1.6.1 分支，Kotlin/Native，自绘 UI）：能最大程度复用现有 Compose 代码。风险：分支落后上游版本，有鸿蒙上的性能问题报告。
  2. **Kuikly**（腾讯，KMP + 原生 ArkUI 渲染）：渲染更原生，但需要用它自己的 DSL 重写 UI。
  3. **ArkTS 原生瘦客户端**：完全重写 UI，逻辑通过服务端 API 复用。
- 做法：共享核心完成后，花 1 周用 ovCompose 验证 2 个页面（记账首页 + 记一笔），看性能、液态玻璃替代效果、输入法和手势。不达标就改走方案 3。
- 资质：华为开发者账号、DevEco Studio。卖虚拟商品必须走 **IAP Kit**（华为收银台里本身就包含微信和支付宝，开发者不用自己接）；实物商品才走 Payment Kit。通知监听在鸿蒙上能否实现，待验证。

## 5. 能力对照

| 能力 | Android | iOS | 鸿蒙 | 小程序 | Web |
|---|---|---|---|---|---|
| 通知自动记账 | ✅ | ❌ | 待验证 | ❌ | ❌ |
| 截图识别（本机） | ✅ | ✅ 分享扩展 | 待验证 | ❌ | ❌ |
| 手动 / 一句话 | ✅ | ✅ | ✅ | ✅ | ✅ |
| 端侧语音 | ✅ | ✅ | 待验证 | ❌ | ❌ |
| 今日小票 | ✅ | ✅ | ✅ | ✅ | ✅ |
| 圈子 | ✅ | ✅ | ✅ | ✅（分享最强） | ✅（比赛后接真实接口） |
| 生活 | ✅ | ✅ | ✅ | 记一笔 | 查看 |
| 收款渠道 | 微信 / 支付宝 | Apple IAP | 华为 IAP Kit | 小程序虚拟支付 | 微信 / 支付宝网页支付 |
