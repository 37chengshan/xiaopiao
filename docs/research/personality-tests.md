# 人格 / 心态小游戏的调研与取材（2026-10-30）

目的：游戏页「性格测试」不能再只有一套自编的钱包人格。这份文档记录看过的开源项目、许可证、
以及最终照着哪个量表的结构写题库（自己写中文题，不逐字搬受版权保护的题面）。

## 1. 看过的来源

| 项目 / 站点 | 内容 | 许可证 / 可用性 | 我们用了什么 |
|---|---|---|---|
| [ipip.ori.org · Mini-IPIP Scoring Key](https://ipip.ori.org/MiniIPIPKey.htm) | 20 题五因素短表（Donnellan, Oswald, Baird & Lucas, 2006），每维 4 题、正反计分各两题，附计分键 | IPIP 项目本身是公共领域（public domain），可用 | **直接照这个结构做「人格五维」**：5 维 × 4 题、`reverse` 反向题、按维度取均值 |
| [rubynor/bigfive-web](https://github.com/rubynor/bigfive-web) | IPIP-NEO 120 题的完整题库（`keyed: plus/minus`、`domain`、`facet`），前端 + 计分 | MIT | 只参考了它的**数据结构**：`{ id, text, keyed, domain }`；我们的 `ScaleQuestion(reverse = …)` 与它同构 |
| [KatherineGuoGuo/bigfive-open](https://github.com/KatherineGuoGuo/bigfive-open) | IPIP-50 的题库、计分与 TypeScript 类型 | 仓库开源 | 确认「题库 / 计分 / 结果档位」三件套的分层方式 |
| [salastro/hexaco-person](https://github.com/salastro/hexaco-person) | HEXACO 六维人格测试脚本 | 开源脚本（HEXACO 题面本身有版权） | 参考**六维**这一结构（诚正、情绪、外向、宜人、尽责、开放）；题面没有照搬 |
| [haghish/openpsychometrics](https://github.com/haghish/openpsychometrics) | openpsychometrics.org 的匿名问卷数据集（BIG5、HEXACO、Cattell 16 等） | 教育用途数据集 | 参考各量表的维度命名与档位解释写法 |
| [bigfive-web · questions.ts](https://github.com/rubynor/bigfive-web/blob/master/packages/questions/src/data/en/questions.ts) | 120 条 `{text, keyed, domain, facet}` | 同上（MIT） | 校准题面长度与「正/反」比例，避免全正向题导致天花板效应 |

金额类的小游戏（猜价格、盲盒、省钱挑战）没有现成开源题库可抄，继续用本地自编内容。

## 2. 结果页怎么算

- **五维（人格五维）**：每维 4 题取平均 → `(均值 - 1) / 4` 归一到 0..1；反向题先按 `6 - 值` 翻转。
  五维在雷达上是等角分布，`GameAxis.band(value)` 把分数切成低 / 中 / 高三档，每档一句解释。
- **六维（消费人格六维）**：12 题、每维两题，算法同上，但题目围绕花钱场景自写。
- **人格标签（钱包人格 / 存钱人格）**：命中次数最多的人格胜出（`GameScoring.personaOutcome`）；
  每个人格带三条 `insights`：行为特征 / 容易卡住 / 可以试试。
- 结果页统一结构：类型名 → 一句判断 → 关键数字 → 维度雷达（每维分数条 + 档位解释）→ 三条分析 → 分享卡。

## 3. 版权与语气边界

- **题面不复制受版权保护的量表原文**：Mini-IPIP 是公共领域，中文按原意改写；HEXACO 只借结构。
- 结果只描述**行为倾向**，不做诊断、不提「你是 X 型人格障碍」这类临床措辞。
- 不输出理财建议、收益预期或消费推荐；页面固定一行「娱乐，不构成建议」。
- 所有题目与结果都是本地常量，不联网、不读账本。

## 4. 还能继续加的（本轮没做）

1. HEXACO 六维的「诚正—谦逊」轴（需要另写题面，且这一维在消费语境里容易被误读，先不做）。
2. 16PF / 卡特尔的分面解释（openpsychometrics 有数据，但题面版权更复杂）。
3. 用真实账本做「你的花钱画像 vs 自评」对照（要过数据底线，且属于产品决策，不在本游戏范围内）。
