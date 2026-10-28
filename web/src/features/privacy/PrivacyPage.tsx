import { useState, type ReactNode } from "react";
import { toast } from "sonner";
import { Footnote, Group, Row, SectionHeader, Segmented, Switch, XButton } from "@/components/app/kit";
import { PageHeader } from "@/components/app/PageHeader";
import { type ThemePref, useTheme } from "@/lib/theme";
import { useLedger } from "@/services/LedgerProvider";
import { useReceiptSound } from "../receipt/soundPref";

const THEME_OPTIONS: readonly { value: ThemePref; label: string }[] = [
  { value: "system", label: "跟随系统" },
  { value: "light", label: "浅色" },
  { value: "dark", label: "深色" },
];

const PRIVACY: readonly { title: string; body: ReactNode }[] = [
  { title: "数据最小化", body: "只记录记账必需的字段：金额、商家、分类、时间。不读取通讯录、定位或相册，不要银行卡号。" },
  {
    title: "原文不出手机",
    body: "Android 版读取支付宝、微信支付通知时，通知原文只在手机本地用规则解析，解析后的结构化账目才会入账，原文不上传。Web 版里导入的 CSV 只在你的浏览器内解析。",
  },
  { title: "商家洞察需要你主动同意", body: "只有你主动勾选「贡献匿名数据」，你的消费才会进入商家洞察；不勾选，数据一分都不会被用于商家端。随时可以关闭。" },
  { title: "聚合门槛 k ≥ 20", body: "商家端只能看到至少 20 位用户汇总后的统计，没有任何个人明细，也无法反查到具体的人。" },
  { title: "演示数据会被明确标注", body: "带有「演示数据」标签的内容都是虚构的，不是真实用户的消费，也不会混入任何统计。" },
  {
    title: "AI 的边界：不提供理财建议",
    body: "AI 只做四件事：记录、总结、提醒、模拟。每一笔账都由你确认；后悔模拟器只是把你过去的花费做算术外推，不含投资收益，也不告诉你该买什么。",
  },
];

export function PrivacyPage() {
  const { mode, txns, reset, forgetMode } = useLedger();
  const { pref, setPref } = useTheme();
  const [soundOn, setSoundOn] = useReceiptSound();
  const [confirming, setConfirming] = useState(false);

  return (
    <>
      <PageHeader title="我的" subtitle="你的数据，你说了算" />

      <SectionHeader>外观</SectionHeader>
      <Group>
        <div className="px-4 py-3">
          <Segmented value={pref} onChange={setPref} options={THEME_OPTIONS} />
        </div>
      </Group>

      <SectionHeader>小票</SectionHeader>
      <Group>
        <Row title="打印音效" subtitle="出纸的滋滋声和盖章声" trailing={<Switch checked={soundOn} onCheckedChange={setSoundOn} label="打印音效" />} />
      </Group>

      <SectionHeader>数据</SectionHeader>
      <Group>
        <Row title="当前模式" subtitle={`共 ${txns.length} 笔`} trailing={<span className="text-[15px] text-ink-2">{mode === "demo" ? "演示数据（虚构）" : mode === "empty" ? "从零开始" : "未选择"}</span>} />
        <Row title="重新选择模式" chevron onClick={mode ? forgetMode : undefined} className={mode ? "" : "opacity-40"} />
        {confirming ? (
          <div className="px-4 py-3" role="alert">
            <p className="text-[15px] leading-[22px] text-ink">清空当前的 {txns.length} 笔账目？无法撤销。</p>
            <div className="mt-3 flex gap-2">
              <XButton size="sm" className="flex-1" onClick={() => setConfirming(false)}>
                再想想
              </XButton>
              <XButton
                size="sm"
                className="flex-1 bg-negative text-on-clay"
                onClick={() =>
                  void reset().then(() => {
                    setConfirming(false);
                    toast.success("已清空");
                  })
                }
              >
                清空
              </XButton>
            </div>
          </div>
        ) : (
          <Row
            title={<span className="text-negative">清空所有记录</span>}
            onClick={mode && txns.length > 0 ? () => setConfirming(true) : undefined}
            className={mode && txns.length > 0 ? "" : "opacity-40"}
          />
        )}
      </Group>
      <Footnote>数据暂存在本页内存中，刷新后会回到初始状态。</Footnote>

      <SectionHeader>隐私说明</SectionHeader>
      <div className="mx-4 divide-y divide-hairline px-4">
        {PRIVACY.map((p) => (
          <section key={p.title} className="py-4 first:pt-1">
            <h3 className="font-serif text-[18px] font-semibold leading-[26px] tracking-[-0.01em] text-ink">{p.title}</h3>
            <p className="mt-1 text-[15px] leading-[24px] text-ink-2">{p.body}</p>
          </section>
        ))}
      </div>

      <SectionHeader>关于</SectionHeader>
      <Group>
        <Row title="今日小票" subtitle="不天天记账，只对账" trailing={<span className="text-[15px] text-ink-2">参赛作品</span>} />
        <Row title="版本" trailing={<span className="text-[15px] text-ink-2">0.1</span>} />
      </Group>
      <Footnote className="pb-8">账单 CSV 解析规则参考 deb-sig/double-entry-generator（Apache-2.0）。</Footnote>
    </>
  );
}
