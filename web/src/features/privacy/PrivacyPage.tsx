import { Database, EyeOff, Handshake, ShieldCheck, Smartphone, Sparkles, Users } from "lucide-react";
import type { ReactNode } from "react";
import { toast } from "sonner";
import { PageHeader } from "@/components/app/PageHeader";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
  AlertDialogTrigger,
} from "@/components/ui/alert-dialog";
import { useLedger } from "@/services/LedgerProvider";

function Block({ icon, title, children }: { icon: ReactNode; title: string; children: ReactNode }) {
  return (
    <section className="rounded-2xl bg-card p-4">
      <h2 className="flex items-center gap-2 text-[15px] font-extrabold text-ink">
        <span className="text-coral" aria-hidden>
          {icon}
        </span>
        {title}
      </h2>
      <div className="mt-1.5 text-[13px] leading-relaxed text-ink-2">{children}</div>
    </section>
  );
}

export function PrivacyPage() {
  const { mode, txns, reset, forgetMode } = useLedger();
  return (
    <>
      <PageHeader title="我的" subtitle="你的数据，你说了算" />
      <div className="space-y-3 px-4 pb-8">
        <Block icon={<ShieldCheck className="size-5" />} title="数据最小化">
          只记录记账必需的字段：金额、商家、分类、时间。不读取通讯录、定位或相册，不要银行卡号。
        </Block>
        <Block icon={<Smartphone className="size-5" />} title="原文不出手机">
          Android 版读取支付宝/微信支付通知时，通知原文只在手机本地用规则解析，解析后的结构化账目才会入账，原文不上传。Web 版里导入的 CSV 只在你的浏览器内解析。
        </Block>
        <Block icon={<Handshake className="size-5" />} title="商家洞察需要你主动同意">
          只有你主动勾选「贡献匿名数据」，你的消费才会进入商家洞察；不勾选，数据一分都不会被用于商家端。随时可以关闭。
        </Block>
        <Block icon={<Users className="size-5" />} title="聚合门槛 k ≥ 20">
          商家端只能看到至少 20 位用户汇总后的统计，没有任何个人明细，也无法反查到具体的人。
        </Block>
        <Block icon={<EyeOff className="size-5" />} title="演示数据会被明确标注">
          带有「演示数据」标签的内容都是虚构的，不是真实用户的消费，也不会混入任何统计。
        </Block>
        <Block icon={<Sparkles className="size-5" />} title="AI 的边界：不提供理财建议">
          AI 只做四件事：记录、总结、提醒、模拟。每一笔账都由你确认；后悔模拟器只是把你过去的花费做算术外推，不含投资收益，也不告诉你该买什么。
        </Block>

        <section className="rounded-2xl bg-card p-4">
          <h2 className="flex items-center gap-2 text-[15px] font-extrabold text-ink">
            <Database className="size-5 text-coral" aria-hidden />
            我的数据
          </h2>
          <p className="mt-1.5 text-[13px] text-ink-2">
            当前模式：<b className="text-ink">{mode === "demo" ? "演示数据（虚构）" : mode === "empty" ? "从零开始" : "未选择"}</b>，共 {txns.length} 笔。数据暂存在本页内存中，刷新后会回到初始状态。
          </p>
          <div className="mt-3 flex flex-col gap-2">
            <AlertDialog>
              <AlertDialogTrigger asChild>
                <button disabled={!mode || txns.length === 0} className="h-11 rounded-2xl bg-stamp/10 font-bold text-stamp disabled:opacity-40">
                  清空所有记录
                </button>
              </AlertDialogTrigger>
              <AlertDialogContent className="max-w-[340px] rounded-3xl">
                <AlertDialogHeader>
                  <AlertDialogTitle>清空所有记录？</AlertDialogTitle>
                  <AlertDialogDescription>会删除当前的 {txns.length} 笔账目，无法撤销。</AlertDialogDescription>
                </AlertDialogHeader>
                <AlertDialogFooter>
                  <AlertDialogCancel>再想想</AlertDialogCancel>
                  <AlertDialogAction
                    className="bg-stamp text-white hover:bg-stamp/90"
                    onClick={() => void reset().then(() => toast.success("已清空"))}
                  >
                    清空
                  </AlertDialogAction>
                </AlertDialogFooter>
              </AlertDialogContent>
            </AlertDialog>
            <button onClick={forgetMode} disabled={!mode} className="h-11 rounded-2xl bg-secondary font-bold text-ink disabled:opacity-40">
              重新选择：演示 / 从零开始
            </button>
          </div>
        </section>

        <section className="rounded-2xl bg-card p-4 text-center">
          <p className="text-3xl" aria-hidden>
            🧾
          </p>
          <p className="mt-1 font-extrabold text-ink">今日小票</p>
          <p className="text-[12px] text-ink-2">不天天记账，只对账 · 参赛作品 · 版本 0.1</p>
          <p className="mt-2 text-[11px] text-ink-2">账单 CSV 解析规则参考 deb-sig/double-entry-generator（Apache-2.0）。</p>
        </section>
      </div>
    </>
  );
}
