# 今日小票 · 微信小程序伴侣版（原型）

轻量伴侣版：记一笔、今日小票、分享到聊天。完整 App 能力（通知自动记账等）只在 Android；规划见 `docs/PLATFORMS.md`。

## 离线编译与预览（不需要微信开发者工具）

用滴滴开源的 [Dimina（星河小程序）](https://github.com/didi/dimina)，装在仓库外：

```bash
git clone --depth 1 https://github.com/didi/dimina ~/code/_tools/dimina
cd ~/code/_tools/dimina/fe
npx -y pnpm@12.2.0 --config.manage-package-manager-versions=false install   # 本机 pnpm 版本不对时这样跑
ln -sfn "$PWD/../../../match/xiaopiao/miniprogram" ../examples/miniprogram/xiaopiao  # 或改成你的绝对路径
npx -y pnpm@12.2.0 --config.manage-package-manager-versions=false compile
npx -y pnpm@12.2.0 --config.manage-package-manager-versions=false dev        # http://localhost:5173 → 选 xiaopiao-miniprogram
```

Dimina 与微信 API 不是 100% 兼容（例如 `wx.*StorageSync` 在它的 Web 容器里不可靠，所以这里统一用异步 `wx.getStorage` / `wx.setStorage` + 内存缓存）。**最终以真微信为准**：拿到正式 AppID 后用官方 [`miniprogram-ci`](https://www.npmjs.com/package/miniprogram-ci) 命令行预览 / 上传（需要 AppID + 上传密钥，同样不用开发者工具界面）。`project.config.json` 里的 `wxxiaopiaodev001` 只是本地占位。

## 约定

- **右上角系统胶囊（「··· / ⊙」）不能盖、不能放自己的按钮**：页面统一 `navigationStyle: custom`，导航栏高度和标题右边界由 `utils/nav.js` 从 `wx.getMenuButtonBoundingClientRect()` 推出，标题靠左。
- 金额一律整数「分」（`utils/money.js`），与 App 的 `Cents` 一致。
- 记一笔流程与 App 一致：金额 → ↵ 跳到备注（上方常用短语）→ 保存。
- 原型数据只存本机；正式版走自建 `/v1/sync`，与 App 同一个账本。
