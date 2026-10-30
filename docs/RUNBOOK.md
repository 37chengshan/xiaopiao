# 今日小票 · 启动与常用命令（RUNBOOK）

更新：2026-10-28。所有命令默认在仓库根目录 `/Users/cc/code/match/xiaopiao` 执行，另有说明的除外。

## 0. 环境要求

| 项 | 版本 / 位置 | 检查命令 |
|---|---|---|
| JDK | 17 | `java -version` |
| Android SDK | `~/Library/Android/sdk`（compileSdk 37，targetSdk 36，minSdk 26） | `adb version` |
| Node | 24.x | `node -v` |
| QMuse CLI | `qmuse` / `qmuse-cli`（在 `~/.npm-global/bin`） | `qmuse --help` |

机器内存 16GB：**同一时间只跑一个 gradle 构建**（见 §5）。

## 1. Android App

```bash
./gradlew :app:assembleDebug            # 构建 debug APK → app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:testDebugUnitTest        # JVM 单测
./gradlew :app:lint                     # Lint
./gradlew :app:installDebug             # 构建并安装到当前连接的唯一设备
```

### 装到真机 / 模拟器

```bash
adb devices                                             # 看有哪些设备
adb -s <设备id> install -r app/build/outputs/apk/debug/app-debug.apk
adb -s <设备id> shell am start -n com.xiaopiao.app/.MainActivity     # 启动
adb -s <设备id> shell am force-stop com.xiaopiao.app                 # 关掉
adb -s <设备id> shell pm clear com.xiaopiao.app                      # 清数据（重新走引导）
```

- 真机：vivo V2405A，`10AF3N0RRS003MT`（Android 16，1260×2800）。USB 连上并开启 USB 调试后才会出现在 `adb devices`。
- 模拟器（AVD）：`xiaopiao-phone`、`xiaopiao-phone-2`。启动：`~/Library/Android/sdk/emulator/emulator -avd xiaopiao-phone &`，之后 id 为 `emulator-5554` / `emulator-5556`。

### 常用调试

```bash
adb -s <设备id> logcat --pid=$(adb -s <设备id> shell pidof com.xiaopiao.app)   # 只看本 App 日志
adb -s <设备id> shell dumpsys gfxinfo com.xiaopiao.app                         # 帧率 / jank（动效验收）
adb -s <设备id> shell cmd locale set-app-locales com.xiaopiao.app --locales en # 切英文（双语验收）
adb -s <设备id> shell dumpsys notification --noredact                          # 采集通知原文（通知记账语料）
adb -s <设备id> shell screenrecord /sdcard/x.mp4                               # 录屏，Ctrl+C 结束后 adb pull
```

## 2. Web（比赛提交物，QMuse）

在 `web/` 目录里：

```bash
cd web
npm install                  # 首次或依赖变更后
npm run dev                  # 本机开发服务器 → http://localhost:5173/
npm run check                # 路由生成 + oxlint + tsc（改动后必须过）
npm test                     # vitest
npm run build                # 产物 → web/dist/（改动后必须过）
npm run preview              # 预览 build 产物
```

### 登录与本机 API

Web 是 hash 路由：登录页是 `http://localhost:5173/#/login`（落地页导航的「登录」和 App 内设置页都能进），不是 `/login`。登录/注册/验证码要连本机 API：

```bash
. "$HOME/.config/xiaopiao/model.env"     # MODEL_API_KEY（私密，600）
. "$HOME/.config/agentmail/env"          # AGENTMAIL_API_KEY / AGENTMAIL_INBOX_ID
DB_PATH="$HOME/.config/xiaopiao/data/xiaopiao.sqlite" \
  npm start --prefix server              # 127.0.0.1:8787，vite 的 /v1 代理指向它
```

- API 没起时登录页会提示「登录服务尚未连接」，这是代理没拿到 JSON，不是页面坏了。
- 新增/改名路由后必须重启 `npm run dev`：路由表在启动时读取，热更新不会补上新页面（表现为「页面未找到」）。
- dev 下任意 loopback 端口（`127.0.0.1:*` / `localhost:*`）都接受，预览换端口不用改 `APP_ORIGIN`；生产仍是单一 HTTPS origin。

### 在手机上看 Web

- 同一 Wi-Fi：`npx vite dev --host 0.0.0.0 --port 5173`，手机打开 `http://<Mac 局域网 IP>:5173/`（IP：`ipconfig getifaddr en0`）。
- USB 连着：`adb -s <设备id> reverse tcp:5173 tcp:5173`，手机打开 `http://localhost:5173/`。
- 电脑上看：浏览器 F12 切手机模式，宽度 390。

### 发布到 QMuse（**需要 tt 明确批准**）

```bash
cd web
npm run check && npm run build
qmuse import .               # 更新 .qmuse/project.json 绑定的应用；失败时看 .qmuse/last-import-error.json
```

不要手改 `web/.qmuse/project.json`。

## 3. 云端 AI（R7，进行中）

- 模型：commandcode 免费模型自动路由（见 `docs/APP-DESIGN.md` §7）。
- key **只放云端 secret**，不写进仓库、App、浏览器代码、文档。开发期 key 放本机环境变量，dev 代理读取。
- QMuse 云服务初始化、`set-secret`、`qmuse import` 都会创建远端资源，**需要 tt 批准**后再执行。
- 直连 commandcode 时请求必须带自定义 `User-Agent`，否则 Cloudflare 返回 403（error 1010）。

## 4. 分支与 worktree

- 主仓库只放 `main`；并行工作的 worktree 都在 `/Users/cc/code/match/_worktrees/xiaopiao/<名字>`。
- 新建：`git worktree add ../_worktrees/xiaopiao/<名字> -b <分支> main`；查看：`git worktree list`。
- 提交只 `git add <具体路径>`，**不要** `git commit -a` / `git add -A`（main 上常有多个会话并行，会把别人的改动带进来）。
- 参考录屏 `docs/references/**/*.mp4` 只留本地（已 gitignore）。

## 5. 多 agent 并行时的 gradle 串行锁

```bash
until mkdir /tmp/xiaopiao-gradle.lock 2>/dev/null; do sleep 15; done
./gradlew :app:testDebugUnitTest :app:assembleDebug; rc=$?
rmdir /tmp/xiaopiao-gradle.lock; exit $rc
```

锁残留（构建被强杀）时手动 `rmdir /tmp/xiaopiao-gradle.lock`。

## 6. 派活（agent）

- Claude 模型子代理：在 Claude Code 里直接用 Agent 派发。
- omp + deepseek：走 Orca（`orca orchestration …`），命令见全局 `~/.agents/AGENTS.md`「OMP + DeepSeek 子代理」一节；worker 交付后先保留会话复用。

## 7. 文档索引

| 文件 | 内容 |
|---|---|
| `docs/SPEC.md` | 定稿 Spec（D1–D11 + §2.1 变更记录） |
| `docs/APP-DESIGN.md` | 整体设计方案，先读 §00 定稿速览 |
| `docs/tt-words.md` | tt 原话（tt#N），设计以原话为准 |
| `docs/GAPS-2026-10-28.md` | 原话 → 实现 缺口审计与工作包 |
| `docs/AGENT.md` | AI 对话 Agent 框架（prompt / 工具 / 循环） |
| `docs/references/wallet-app-2026-10-28/` | 参考截图 + omp 拆解 `analysis-*.md` |
