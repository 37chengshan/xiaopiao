import { Link, useRouterState } from "@tanstack/react-router";
import { BarChart3, MessageCircleHeart, ReceiptText, Store, UserRound } from "lucide-react";
import { cn } from "@/lib/utils";

const TABS = [
  { to: "/home", label: "今日", icon: MessageCircleHeart },
  { to: "/ledger", label: "账本", icon: ReceiptText },
  { to: "/insights", label: "洞察", icon: BarChart3 },
  { to: "/merchant", label: "小店", icon: Store },
  { to: "/me", label: "我的", icon: UserRound },
] as const;

/** Translucent tab bar floating over the content; selected = clay icon + label, no filled block. */
export function TabBar() {
  const path = useRouterState({ select: (s) => s.location.pathname });
  return (
    <nav
      aria-label="主导航"
      className="material absolute inset-x-0 bottom-0 z-30 h-[var(--tabbar-h)] pb-[env(safe-area-inset-bottom,0px)] shadow-[0_-0.5px_0_var(--hairline)]"
    >
      <ul className="flex h-[52px]">
        {TABS.map(({ to, label, icon: Icon }) => {
          const active = to === "/home" ? path === "/home" || path === "/today" : path.startsWith(to);
          return (
            <li key={to} className="flex-1">
              <Link
                to={to}
                aria-current={active ? "page" : undefined}
                className={cn(
                  "flex h-full flex-col items-center justify-center gap-[3px] text-[10px] font-medium tracking-[0.02em] transition-colors duration-150 ease-[var(--ease-out)] active:opacity-60",
                  active ? "text-clay" : "text-ink-2",
                )}
              >
                <Icon className="size-6" strokeWidth={active ? 2 : 1.6} aria-hidden />
                {label}
              </Link>
            </li>
          );
        })}
      </ul>
    </nav>
  );
}
