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
    <nav aria-label="主导航" className="shrink-0 border-t border-hairline bg-card/95 pb-[env(safe-area-inset-bottom)] backdrop-blur">
      <ul className="flex">
        {TABS.map(({ to, label, icon: Icon }) => {
          const active = to === "/home" ? path === "/home" || path === "/today" : path.startsWith(to);
          const cls = cn(
            "flex flex-col items-center gap-0.5 py-2 text-[11px] font-medium transition-colors",
            active ? "text-coral" : "text-ink-2 hover:text-ink",
          );
          const inner = (
            <>
              <Icon className="size-[22px]" strokeWidth={active ? 2.4 : 1.8} aria-hidden />
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
