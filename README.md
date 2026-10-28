# 今日小票 (xiaopiao)

每天打印一张会说话的 AI 小票：无感记账、小圈子周报、头衔成就；用户自愿贡献的匿名聚合数据，变成楼下小店老板的经营参考。

- **赛事**：Alipay 智能体涌现奖（Emergent Agent Award）Season 1，主报「生活好帮手」
- **截止**：2026-12-06（按 12-05 提交）
- **形态**：Android 原生 App（Kotlin + Jetpack Compose）；`web/` 为 QMuse Web 智能体，是比赛提交物
- **Spec**：[`docs/SPEC.md`](docs/SPEC.md) · [`docs/BRIEF.md`](docs/BRIEF.md)
- **协作约定**：[`AGENTS.md`](AGENTS.md)（`CLAUDE.md` 引用同一份）

## 构建

```bash
./gradlew :app:assembleDebug   # 需要 JDK 17 与 Android SDK（local.properties 指向 sdk.dir，不入库）
```

## 提交物

封面 16:9、90 秒视频、说明材料——待补。
