import { Link, useRouterState } from "@tanstack/react-router";
import { BarChart3, ReceiptText, Store, UserRound, MessageCircleHeart } from "lucide-react";
import { cn } from "@/lib/utils";

const TABS = [
  { to: "/home", label: "今日", icon: MessageCircleHeart },
  { to: "/ledger", label: "账本", icon: ReceiptText },
  { to: "/insights", label: "洞察", icon: BarChart3 },
  { to: "/merchant", label: "小店", icon: Store },
  { to: "/me", label: "我的", icon: UserRound },
] as const;

export function TabBar() {
  const path = useRouterState({ select: (s) => s.location.pathname });
  return (
    <nav aria-label="主导航" className="material shrink-0 pb-[env(safe-area-inset-bottom)] shadow-[0_-1px_0_var(--hairline)]">
      <ul className="flex">
        {TABS.map(({ to, label, icon: Icon }) => {
          const active = to === "/home" ? path === "/home" || path === "/today" : path.startsWith(to);
          const cls = cn(
            "flex flex-col items-center gap-[3px] pb-1.5 pt-2 text-[10px] font-medium tracking-[0.01em] transition-colors duration-150",
            active ? "text-clay" : "text-ink-2",
          );
          const inner = (
            <>
              <Icon className="size-6" strokeWidth={active ? 2 : 1.6} aria-hidden />
              {label}
            </>
          );
          return (
            <li key={to} className="flex-1">
              <Link to={to} className={cls} aria-current={active ? "page" : undefined}>
                {inner}
              </Link>
            </li>
          );
        })}
      </ul>
    </nav>
  );
}
