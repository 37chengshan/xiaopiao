import { Compass, FlaskConical, Gamepad2, Handshake, ShieldCheck, Sigma, Smartphone, type LucideIcon } from "lucide-react";
import type { ReactNode } from "react";
import { Drawer } from "vaul";
import { Group, IconTile } from "@/components/app/kit";
import { copy } from "../copy";

const SOURCES: ReadonlyArray<{ icon: LucideIcon; title: string; body: string }> = [
  { icon: Handshake, title: "自愿参与", body: "只有主动打开「愿意匿名分享」的顾客才会被统计，随时可以退出。" },
  { icon: Sigma, title: "只看汇总", body: "你看到的都是人群汇总：占比、时段、价格带，看不到任何个人的账单。" },
  { icon: ShieldCheck, title: "20 人以上才显示", body: "任何一格少于 20 人，就留成斜线空格，避免从小群体里认出具体的人。" },
  { icon: Smartphone, title: "原始通知不出手机", body: "支付通知等原始内容只留在顾客自己的手机里，不会上传给小店或我们。" },
  { icon: Gamepad2, title: "游戏表态不是真实消费", body: "小游戏里的选择叫「游戏表态」，和真实交易分开存放、分开展示。" },
  { icon: Compass, title: copy.sheet.decide.title, body: copy.sheet.decide.body },
  { icon: FlaskConical, title: "当前全是演示数据", body: "本页所有数字都是模拟的，用来展示产品会怎么工作。" },
];

/** 「数据怎么来的」bottom sheet (vaul): drags 1:1, releases by velocity, same path in and out. */
export function DataSheet({ children }: { children: ReactNode }) {
  return (
    <Drawer.Root>
      <Drawer.Trigger asChild>{children}</Drawer.Trigger>
      <Drawer.Portal>
        <Drawer.Overlay className="fixed inset-0 z-50 bg-scrim" />
        <Drawer.Content className="fixed inset-x-0 bottom-0 z-50 mx-auto flex max-h-[88dvh] w-full max-w-[430px] flex-col rounded-t-[20px] bg-bg-grouped text-ink outline-none">
          <div className="mx-auto mt-2 h-[5px] w-9 shrink-0 rounded-full bg-ink-3/40" aria-hidden />
          <div className="px-4 pb-1 pt-4">
            <Drawer.Title className="text-[20px] font-semibold leading-[26px] tracking-[-0.01em]">{copy.sheet.title}</Drawer.Title>
            <Drawer.Description className="mt-1 text-[13px] leading-[18px] text-ink-2">{copy.sheet.lead}</Drawer.Description>
          </div>
          <div className="min-h-0 flex-1 overflow-y-auto pb-[max(1.5rem,env(safe-area-inset-bottom))] pt-3">
            <Group>
              {SOURCES.map((s) => (
                <div key={s.title} className="flex items-start gap-3 px-4 py-3">
                  <IconTile icon={s.icon} />
                  <div className="min-w-0 flex-1">
                    <p className="text-[16px] font-medium leading-[22px]">{s.title}</p>
                    <p className="mt-0.5 text-[13px] leading-[18px] text-ink-2">{s.body}</p>
                  </div>
                </div>
              ))}
            </Group>
          </div>
        </Drawer.Content>
      </Drawer.Portal>
    </Drawer.Root>
  );
}
