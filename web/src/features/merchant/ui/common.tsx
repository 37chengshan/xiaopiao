import type { ReactNode } from "react";
import { motion, useReducedMotion } from "framer-motion";
import { Drawer, DrawerContent, DrawerDescription, DrawerHeader, DrawerTitle, DrawerTrigger } from "@/components/ui/drawer";
import { cn } from "@/lib/utils";
import { DEMO_LABEL } from "../logic/crowd";
import { spring, tokenVars } from "../tokens";

/** Self-contained phone-width column used by /merchant and /game. */
export function PhoneFrame({ children }: { children: ReactNode }) {
  return (
    <div
      style={tokenVars}
      className="min-h-dvh bg-(--mb-canvas) text-(--mb-ink) antialiased [font-family:-apple-system,BlinkMacSystemFont,'SF_Pro_Text','PingFang_SC','Helvetica_Neue',sans-serif]"
    >
      <div className="mx-auto min-h-dvh w-full max-w-[430px] border-x border-(--mb-line) bg-(--mb-canvas) px-4 pb-28 pt-[max(1rem,env(safe-area-inset-top))]">
        {children}
      </div>
    </div>
  );
}

export function DemoPill({ className }: { className?: string }) {
  return (
    <span
      className={cn(
        "inline-flex shrink-0 items-center gap-1 rounded-full bg-(--mb-coral-soft) px-2.5 py-1 text-[11px] font-semibold text-(--mb-coral-deep)",
        className,
      )}
    >
      <span aria-hidden className="size-1.5 rounded-full bg-(--mb-coral)" />
      {DEMO_LABEL}
    </span>
  );
}

export function Card({ className, children, ...rest }: React.ComponentProps<"section">) {
  return (
    <section
      className={cn("rounded-2xl border border-(--mb-line) bg-(--mb-card) p-4 shadow-[0_1px_0_oklch(0.9_0.005_90)]", className)}
      {...rest}
    >
      {children}
    </section>
  );
}

export function SectionTitle({ children, hint }: { children: ReactNode; hint?: ReactNode }) {
  return (
    <div className="mb-3 flex items-baseline justify-between gap-2">
      <h3 className="text-[15px] font-bold tracking-tight">{children}</h3>
      {hint ? <span className="text-[11px] text-(--mb-ink-soft)">{hint}</span> : null}
    </div>
  );
}

export function PrimaryButton({ className, ...rest }: React.ComponentProps<"button">) {
  return (
    <motion.button
      whileTap={{ scale: 0.97 }}
      transition={spring}
      className={cn(
        "inline-flex h-12 w-full items-center justify-center gap-2 rounded-2xl bg-(--mb-coral) px-5 text-[15px] font-bold text-white outline-offset-2 focus-visible:outline-2 focus-visible:outline-(--mb-ink) disabled:cursor-not-allowed disabled:opacity-40",
        className,
      )}
      {...(rest as object)}
    />
  );
}

export function GhostButton({ className, ...rest }: React.ComponentProps<"button">) {
  return (
    <button
      className={cn(
        "inline-flex h-11 w-full items-center justify-center gap-2 rounded-2xl border border-(--mb-line) bg-(--mb-card) px-5 text-[14px] font-semibold text-(--mb-ink) outline-offset-2 transition-colors hover:bg-(--mb-canvas) focus-visible:outline-2 focus-visible:outline-(--mb-ink) disabled:cursor-not-allowed disabled:opacity-40",
        className,
      )}
      {...rest}
    />
  );
}

/** Designed empty state with a floating emoji. */
export function EmptyState({
  emoji,
  title,
  body,
  action,
}: {
  emoji: string;
  title: string;
  body: string;
  action?: ReactNode;
}) {
  const reduce = useReducedMotion();
  return (
    <Card className="flex flex-col items-center gap-3 px-6 py-10 text-center">
      <motion.div
        aria-hidden
        className="grid size-16 place-items-center rounded-full bg-(--mb-coral-soft) text-3xl"
        animate={reduce ? undefined : { y: [0, -6, 0] }}
        transition={{ duration: 2.4, repeat: Infinity, ease: "easeInOut" }}
      >
        {emoji}
      </motion.div>
      <h3 className="text-[17px] font-bold">{title}</h3>
      <p className="max-w-[18rem] text-[13px] leading-relaxed text-(--mb-ink-soft)">{body}</p>
      {action ? <div className="mt-2 w-full">{action}</div> : null}
    </Card>
  );
}

const SOURCES: ReadonlyArray<{ emoji: string; title: string; body: string }> = [
  { emoji: "🤝", title: "自愿参与", body: "只有主动打开「愿意匿名分享」的顾客才会被统计，随时可以退出。" },
  { emoji: "🧮", title: "只看汇总", body: "你看到的都是人群汇总：占比、时段、价格带，看不到任何个人的账单。" },
  { emoji: "🔒", title: "k ≥ 20 才显示", body: "任何一格少于 20 人，就显示「样本不足」，避免从小群体里认出具体的人。" },
  { emoji: "📱", title: "原始通知不出手机", body: "支付通知等原始内容只留在顾客自己的手机里，不会上传给小店或我们。" },
  { emoji: "🎮", title: "游戏表态 ≠ 真实消费", body: "小游戏里的选择叫「游戏表态」，和真实交易分开存放、分开展示，不会混在一起。" },
  { emoji: "🧪", title: "当前全是演示数据", body: "本页所有数字都是模拟的演示数据，用来展示产品会怎么工作。" },
];

/** 「数据怎么来的」bottom drawer (vaul). Content is portaled, so token vars are re-applied. */
export function DataDrawer({ children }: { children: ReactNode }) {
  return (
    <Drawer>
      <DrawerTrigger asChild>{children}</DrawerTrigger>
      <DrawerContent
        style={tokenVars}
        className="mx-auto max-w-[430px] border-(--mb-line) bg-(--mb-card) text-(--mb-ink)"
      >
        <DrawerHeader className="text-left">
          <DrawerTitle className="text-[18px] font-bold">数据怎么来的</DrawerTitle>
          <DrawerDescription className="text-[13px] text-(--mb-ink-soft)">
            小店只能看到匿名、汇总、自愿的洞察。
          </DrawerDescription>
        </DrawerHeader>
        <ul className="max-h-[55dvh] space-y-3 overflow-y-auto px-4 pb-8">
          {SOURCES.map((s) => (
            <li key={s.title} className="flex gap-3 rounded-2xl bg-(--mb-canvas) p-3">
              <span aria-hidden className="text-2xl">
                {s.emoji}
              </span>
              <div>
                <p className="text-[14px] font-bold">{s.title}</p>
                <p className="mt-0.5 text-[13px] leading-relaxed text-(--mb-ink-soft)">{s.body}</p>
              </div>
            </li>
          ))}
        </ul>
      </DrawerContent>
    </Drawer>
  );
}
