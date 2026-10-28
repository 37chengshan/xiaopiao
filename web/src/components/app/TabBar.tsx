import { Link, useRouterState } from "@tanstack/react-router";
import { motion, useReducedMotion } from "framer-motion";
import { BarChart3, Gamepad2, MessageCircleHeart, Plus, ReceiptText, Users, type LucideIcon } from "lucide-react";
import { type ReactNode } from "react";
import { useEntry } from "@/features/entry/EntryProvider";
import { SPRING_THUMB } from "@/lib/motion";
import { t, type StringKey } from "@/lib/strings";
import { cn } from "@/lib/utils";

/** Slot / padding in px: one 64px slot per destination, 4px capsule padding (docs/APP-DESIGN.md §2). */
const SLOT = 64;
const PAD = 4;
/** Opens and closes the circle: one spring, retargeted (not restarted) when interrupted. */
const OPEN_SPRING = { type: "spring", stiffness: 320, damping: 29 } as const;

type Dest = { to: string; label: StringKey; icon: LucideIcon };
const LEDGER: Dest = { to: "/ledger", label: "nav.ledger", icon: ReceiptText };
const INSIGHTS: Dest = { to: "/insights", label: "nav.insights", icon: BarChart3 };
const AI: Dest = { to: "/home", label: "nav.ai", icon: MessageCircleHeart };
const CIRCLE: Dest = { to: "/game", label: "nav.circle", icon: Users };
const GAMES: Dest = { to: "/game", label: "nav.games", icon: Gamepad2 };

function Tab({ dest, active, thumb, reduce }: { dest: Dest; active: boolean; thumb: string; reduce: boolean }) {
  const Icon = dest.icon;
  return (
    <Link
      to={dest.to}
      aria-current={active ? "page" : undefined}
      style={{ width: SLOT }}
      className={cn(
        "relative flex h-16 shrink-0 flex-col items-center justify-center gap-[3px] text-[10px] font-medium tracking-[0.02em] transition-colors duration-[var(--dur-tint)] ease-[var(--ease-out)] active:opacity-60",
        active ? "text-ink" : "text-ink-3",
      )}
    >
      {active && (
        <motion.span
          aria-hidden
          layoutId={thumb}
          className="absolute inset-x-2 inset-y-[7px] rounded-[19px] bg-fill/80"
          transition={reduce ? { duration: 0 } : SPRING_THUMB}
        />
      )}
      <Icon className="relative size-6" strokeWidth={active ? 2.1 : 1.6} aria-hidden />
      <span className="relative">{t(dest.label)}</span>
    </Link>
  );
}

/** Slides shut (width and opacity) while the child keeps its size, so nothing squishes. */
function Fold({ open, tr, children }: { open: boolean; tr: object; children: ReactNode }) {
  return (
    <motion.div
      initial={false}
      animate={{ width: open ? SLOT : 0, opacity: open ? 1 : 0 }}
      transition={tr}
      inert={!open || undefined}
      className="shrink-0 overflow-hidden"
    >
      <div style={{ width: SLOT }}>{children}</div>
    </motion.div>
  );
}

/**
 * v5 navigation: a left capsule 记账 · 统计 · ＋记一笔 · AI and a detached 圈子 circle on the right.
 * On the circle page the left capsule folds to 记账 ＋ and the circle opens into AI · 圈子 · 游戏 while drifting
 * toward the middle. 圈子 and 游戏 share /game until the circle and games pages exist on Web.
 */
export function TabBar({ hidden = false }: { hidden?: boolean }) {
  const path = useRouterState({ select: (s) => s.location.pathname });
  const entry = useEntry();
  const reduce = useReducedMotion() ?? false;
  const tr = reduce ? { duration: 0 } : OPEN_SPRING;
  const circle = path.startsWith("/game");
  const openWidth = SLOT * 3 + PAD * 2;
  return (
    <nav
      aria-label={t("nav.aria")}
      style={{ bottom: "calc(10px + env(safe-area-inset-bottom, 0px))" }}
      inert={hidden || undefined}
      className={cn("absolute inset-x-4 z-30 flex items-center", hidden && "pointer-events-none translate-y-[120%] opacity-0")}
    >
      <div className="glass-float flex h-16 shrink-0 items-center rounded-[32px]" style={{ paddingInline: PAD }}>
        <Tab dest={LEDGER} active={path.startsWith(LEDGER.to)} thumb="tab-thumb" reduce={reduce} />
        <Fold open={!circle} tr={tr}>
          <Tab dest={INSIGHTS} active={path.startsWith(INSIGHTS.to)} thumb="tab-thumb" reduce={reduce} />
        </Fold>
        <button
          type="button"
          aria-label={t("nav.record")}
          onClick={() => entry.open()}
          style={{ width: SLOT }}
          className={cn(
            "press flex h-16 shrink-0 flex-col items-center justify-center gap-[3px] text-[10px] font-medium tracking-[0.02em] transition-colors duration-[var(--dur-tint)] ease-[var(--ease-out)]",
            circle ? "text-ink-3" : "text-ink",
          )}
        >
          <Plus className="size-6" strokeWidth={1.6} aria-hidden />
          <span>{t("nav.record")}</span>
        </button>
        <Fold open={!circle} tr={tr}>
          <Tab dest={AI} active={path.startsWith(AI.to)} thumb="tab-thumb" reduce={reduce} />
        </Fold>
      </div>

      <div aria-hidden className="min-w-0 flex-1" />

      <motion.div
        initial={false}
        animate={{ width: circle ? openWidth : SLOT }}
        transition={tr}
        className="glass-float relative h-16 shrink-0 overflow-hidden rounded-[32px]"
      >
        {/* closed: an ink circle that fades out as the capsule opens */}
        <motion.div
          initial={false}
          animate={{ opacity: circle ? 0 : 1 }}
          transition={tr}
          className={cn("absolute inset-0 z-10 bg-ink", circle && "pointer-events-none")}
        >
          <Link to={CIRCLE.to} aria-label={t("nav.circle")} className="press grid size-full place-items-center text-bg">
            <Users className="size-6" strokeWidth={1.8} aria-hidden />
          </Link>
        </motion.div>
        {/* open: AI · 圈子 · 游戏 */}
        <motion.div
          initial={false}
          animate={{ opacity: circle ? 1 : 0 }}
          transition={tr}
          inert={!circle || undefined}
          className="absolute inset-y-0 left-1/2 flex -translate-x-1/2 items-center"
          style={{ width: openWidth, paddingInline: PAD }}
        >
          <Tab dest={AI} active={path.startsWith(AI.to)} thumb="tab-thumb-circle" reduce={reduce} />
          <Tab dest={CIRCLE} active={circle} thumb="tab-thumb-circle" reduce={reduce} />
          <Tab dest={GAMES} active={false} thumb="tab-thumb-circle" reduce={reduce} />
        </motion.div>
      </motion.div>

      <motion.div aria-hidden initial={false} animate={{ flexGrow: circle ? 0.55 : 0 }} transition={tr} className="min-w-0 shrink" style={{ flexBasis: 0 }} />
    </nav>
  );
}
