import { Link, useNavigate, useRouterState } from "@tanstack/react-router";
import { BarChart3, MessageCircleHeart, ReceiptText, UserRound, Users, type LucideIcon } from "lucide-react";
import { useEntry } from "@/features/entry/EntryProvider";
import { t, type StringKey } from "@/lib/strings";
import { cn } from "@/lib/utils";

type Side = { to: string; label: StringKey; icon: LucideIcon };
const LEFT: Side[] = [
  { to: "/ledger", label: "nav.ledger", icon: ReceiptText },
  { to: "/insights", label: "nav.insights", icon: BarChart3 },
];
const RIGHT: Side[] = [
  { to: "/game", label: "nav.circle", icon: Users },
  { to: "/me", label: "nav.me", icon: UserRound },
];

function Tab({ to, label, icon: Icon, path }: Side & { path: string }) {
  const active = path.startsWith(to);
  return (
    <li className="flex-1">
      <Link
        to={to}
        aria-current={active ? "page" : undefined}
        className={cn(
          "flex h-16 flex-col items-center justify-center gap-[3px] text-[10px] font-medium tracking-[0.02em] transition-colors duration-150 ease-[var(--ease-out)] active:opacity-60",
          active ? "text-ink" : "text-ink-3",
        )}
      >
        <Icon className="size-6" strokeWidth={active ? 2.1 : 1.6} aria-hidden />
        {t(label)}
      </Link>
    </li>
  );
}

/**
 * Translucent tab bar: two tabs, a raised round primary button in the middle (chat + log), two tabs.
 * Selected = ink icon and label (clay is spent on the centre button). The shop view is not a tab; it lives in 我的.
 */
export function TabBar({ hidden = false }: { hidden?: boolean }) {
  const path = useRouterState({ select: (s) => s.location.pathname });
  const navigate = useNavigate();
  const entry = useEntry();
  const onHome = path === "/home";
  return (
    <nav
      aria-label={t("nav.aria")}
      style={{ bottom: "calc(10px + env(safe-area-inset-bottom, 0px))" }}
      inert={hidden || undefined}
      className={cn(
        "material tab-capsule absolute inset-x-4 z-30 h-16 rounded-[32px] transition-[transform,opacity] duration-200 ease-[var(--ease-out)]",
        hidden && "pointer-events-none translate-y-[120%] opacity-0",
      )}
    >
      <ul className="flex h-16 items-stretch px-2">
        {LEFT.map((x) => (
          <Tab key={x.to} {...x} path={path} />
        ))}
        <li className="relative flex w-[84px] shrink-0 justify-center">
          <button
            type="button"
            aria-label={t("nav.chat")}
            aria-current={onHome ? "page" : undefined}
            onClick={() => (onHome ? entry.open() : void navigate({ to: "/home" }))}
            className="btn-clay absolute -top-[18px] grid size-[58px] place-items-center rounded-full bg-clay text-on-clay ring-4 ring-bg active:scale-[0.95]"
          >
            <MessageCircleHeart className="size-[28px]" strokeWidth={1.9} aria-hidden />
          </button>
          <span className={cn("absolute bottom-[7px] text-[10px] font-medium tracking-[0.02em]", onHome ? "text-ink" : "text-ink-3")}>{t("nav.chat")}</span>
        </li>
        {RIGHT.map((x) => (
          <Tab key={x.to} {...x} path={path} />
        ))}
      </ul>
    </nav>
  );
}
