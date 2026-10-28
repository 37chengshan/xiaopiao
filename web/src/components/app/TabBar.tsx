import { Link, useNavigate, useRouterState } from "@tanstack/react-router";
import { motion, useReducedMotion } from "framer-motion";
import { BarChart3, MessageCircleHeart, ReceiptText, UserRound, Users, type LucideIcon } from "lucide-react";
import { useEntry } from "@/features/entry/EntryProvider";
import { SPRING_THUMB } from "@/lib/motion";
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

function Tab({ to, label, icon: Icon, path, reduce }: Side & { path: string; reduce: boolean }) {
  const active = path.startsWith(to);
  return (
    <li className="flex-1">
      <Link
        to={to}
        aria-current={active ? "page" : undefined}
        className={cn(
          "relative flex h-16 flex-col items-center justify-center gap-[3px] text-[10px] font-medium tracking-[0.02em] transition-colors duration-[var(--dur-tint)] ease-[var(--ease-out)] active:opacity-60",
          active ? "text-ink" : "text-ink-3",
        )}
      >
        {active && (
          <motion.span
            aria-hidden
            layoutId="tab-thumb"
            className="absolute inset-x-2 inset-y-[7px] rounded-[19px] bg-fill/80"
            transition={reduce ? { duration: 0 } : SPRING_THUMB}
          />
        )}
        <Icon className="relative size-6" strokeWidth={active ? 2.1 : 1.6} aria-hidden />
        <span className="relative">{t(label)}</span>
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
  const reduce = useReducedMotion() ?? false;
  const onHome = path === "/home";
  return (
    <nav
      aria-label={t("nav.aria")}
      style={{ bottom: "calc(10px + env(safe-area-inset-bottom, 0px))" }}
      inert={hidden || undefined}
      className={cn(
        // keyboard-driven visibility snaps: no animation on typing actions
        "glass-float absolute inset-x-4 z-30 h-16 rounded-[32px]",
        hidden && "pointer-events-none translate-y-[120%] opacity-0",
      )}
    >
      <ul className="flex h-16 items-stretch px-2">
        {LEFT.map((x) => (
          <Tab key={x.to} {...x} path={path} reduce={reduce} />
        ))}
        <li className="relative flex w-[84px] shrink-0 justify-center">
          <button
            type="button"
            aria-label={t("nav.chat")}
            aria-current={onHome ? "page" : undefined}
            onClick={() => (onHome ? entry.open() : void navigate({ to: "/home" }))}
            className="press absolute -top-[18px] grid size-[58px] place-items-center rounded-full bg-clay text-on-clay ring-4 ring-bg"
          >
            <MessageCircleHeart className="size-[28px]" strokeWidth={1.9} aria-hidden />
          </button>
          <span className={cn("absolute bottom-[7px] text-[10px] font-medium tracking-[0.02em] transition-colors duration-[var(--dur-tint)]", onHome ? "text-ink" : "text-ink-3")}>{t("nav.chat")}</span>
        </li>
        {RIGHT.map((x) => (
          <Tab key={x.to} {...x} path={path} reduce={reduce} />
        ))}
      </ul>
    </nav>
  );
}
