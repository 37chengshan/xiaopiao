import { useState, type ReactNode } from "react";
import { toast } from "sonner";
import { useNavigate } from "@tanstack/react-router";
import { Eraser, Info, Palette, PlayCircle, Shield, Store, PenLine, Vibrate, Volume2 } from "lucide-react";
import { Group, IconTile, Row, Segmented, Switch, XButton } from "@/components/app/kit";
import { onboarding } from "@/components/app/onboardingStore";
import { Sheet } from "@/components/app/Sheet";
import { t } from "@/lib/strings";
import { PageHeader } from "@/components/app/PageHeader";
import { type ThemePref, useTheme } from "@/lib/theme";
import { useLedger } from "@/services/LedgerProvider";
import { useReceiptHaptics } from "../receipt/hapticPref";
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
  {
    title: "AI 的边界：不提供理财建议",
    body: "AI 只做四件事：记录、总结、提醒、模拟。每一笔账都由你确认；后悔模拟器只是把你过去的花费做算术外推，不含投资收益，也不告诉你该买什么。",
  },
];

export function PrivacyPage() {
  const { txns, reset } = useLedger();
  const { pref, setPref } = useTheme();
  const [soundOn, setSoundOn] = useReceiptSound();
  const [hapticsOn, setHapticsOn] = useReceiptHaptics();
  const [confirming, setConfirming] = useState(false);
  const [sheet, setSheet] = useState<"privacy" | null>(null);
  const navigate = useNavigate();
  const canClear = txns.length > 0;

  return (
    <>
      <PageHeader title={t("me.title")} />

      <div className="space-y-3 pb-6">
        <Group>
          <Row leading={<IconTile icon={Palette} tone="violet" />} title={t("me.appearance")} />
          <div className="px-4 pb-3.5">
            <Segmented value={pref} onChange={setPref} options={THEME_OPTIONS} />
          </div>
          <Row
            leading={<IconTile icon={Volume2} tone="amber" />}
            title={t("me.sound")}
            trailing={<Switch checked={soundOn} onCheckedChange={setSoundOn} label={t("me.sound")} />}
          />
          <Row
            leading={<IconTile icon={Vibrate} tone="green" />}
            title={t("me.haptics")}
            trailing={<Switch checked={hapticsOn} onCheckedChange={setHapticsOn} label={t("me.haptics")} />}
          />
        </Group>

        <Group inset="icon">
          <Row leading={<IconTile icon={Store} tone="blue" />} title={t("me.merchant")} chevron onClick={() => void navigate({ to: "/merchant" })} />
          <Row leading={<IconTile icon={PenLine} tone="amber" />} title={t("me.entryDefault")} trailing={<span className="text-[15px] text-ink-3">{t("me.soon")}</span>} />
          <Row leading={<IconTile icon={PlayCircle} tone="green" />} title={t("me.guide")} chevron onClick={onboarding.replay} />
        </Group>

        <Group inset="icon">
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
              leading={<IconTile icon={Eraser} tone="clay" />}
              title={<span className="text-negative">{t("me.clear")}</span>}
              onClick={canClear ? () => setConfirming(true) : undefined}
              className={canClear ? "" : "opacity-40"}
            />
          )}
        </Group>

        <Group inset="icon">
          <Row leading={<IconTile icon={Shield} tone="green" />} title={t("me.privacy")} chevron onClick={() => setSheet("privacy")} />
          <Row leading={<IconTile icon={Info} />} title={t("me.version")} trailing={<span className="text-[15px] text-ink-2">0.1</span>} />
        </Group>
      </div>

      <Sheet open={sheet === "privacy"} onOpenChange={(o) => !o && setSheet(null)} title={t("me.privacy")} hideTitle={false}>
        <div className="overflow-y-auto px-5 pb-[max(1.5rem,env(safe-area-inset-bottom))]">
          <div className="divide-y divide-hairline">
            {PRIVACY.map((p) => (
              <section key={p.title} className="py-4">
                <h3 className="text-[16px] font-semibold leading-[24px] text-ink">{p.title}</h3>
                <p className="mt-1 text-[14px] leading-[22px] text-ink-2">{p.body}</p>
              </section>
            ))}
          </div>
          <p className="pt-2 text-[12px] leading-[18px] text-ink-2">数据暂存在本页内存中，刷新后会回到初始状态。账单 CSV 解析规则参考 deb-sig/double-entry-generator（Apache-2.0）。</p>
        </div>
      </Sheet>
    </>
  );
}
