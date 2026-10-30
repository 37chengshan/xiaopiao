import {
  BarChart3,
  CreditCard,
  LayoutGrid,
  LineChart,
  ReceiptText,
  Settings,
  Sparkles,
  Store,
  TrendingUp,
  Users,
  Wallet,
  type LucideIcon,
} from "lucide-react";
import { s } from "./strings";
import { appRouter } from "@/lib/routerRef";

export type Segment = "personal" | "shop";

/** Every destination the shell links to, so `go()` stays typed against the generated route tree. */
export type NavPath =
  | "/app"
  | "/app/ledger"
  | "/app/stats"
  | "/app/ai"
  | "/app/receipt"
  | "/app/circle"
  | "/app/cards"
  | "/app/settings"
  | "/app/biz"
  | "/app/biz/revenue"
  | "/app/biz/analysis"
  | "/app/biz/report"
  | "/app/biz/insights";

export interface NavItem {
  to: NavPath;
  label: () => string;
  icon: LucideIcon;
}

/**
 * Nav rows are plain `<a href="#/app/…">` so a middle click, ⌘-click or "copy link" still works,
 * but a normal click goes through the router: `router.navigate` writes the hash *and* commits the
 * location in one step. Assigning `location.hash` instead relied on the `popstate` a fragment write
 * emits — some engines only fire `hashchange` — and left `history.state` null, so every parse minted
 * a fresh route key (broken scroll restoration, duplicated commits, a keyed page remount mid-flight).
 */
export function go(to: NavPath) {
  void appRouter().navigate({ to });
}

/** Hash an anchor should carry for a path (the router uses hash history). */
export function hrefOf(to: NavPath) {
  return `#${to}`;
}

/** True when the click should stay native (new tab / new window / context menu). */
export function isModifiedClick(e: { button?: number; metaKey: boolean; ctrlKey: boolean; shiftKey: boolean; altKey: boolean }) {
  return (e.button ?? 0) !== 0 || e.metaKey || e.ctrlKey || e.shiftKey || e.altKey;
}

/**
 * Nav targets are hash paths because the router runs on hash history, so every page is a plain
 * fragment link that also works when copied into a new tab.
 */
export const PERSONAL_NAV: readonly NavItem[] = [
  { to: "/app", label: () => s("nav.overview"), icon: LayoutGrid },
  { to: "/app/ledger", label: () => s("nav.ledger"), icon: ReceiptText },
  { to: "/app/stats", label: () => s("nav.stats"), icon: BarChart3 },
  { to: "/app/ai", label: () => s("nav.ai"), icon: Sparkles },
  { to: "/app/receipt", label: () => s("nav.receipt"), icon: Wallet },
  { to: "/app/circle", label: () => s("nav.circle"), icon: Users },
  { to: "/app/cards", label: () => s("nav.cards"), icon: CreditCard },
];

export const SHOP_NAV: readonly NavItem[] = [
  { to: "/app/biz", label: () => s("nav.bizOverview"), icon: Store },
  { to: "/app/biz/revenue", label: () => s("nav.bizRevenue"), icon: TrendingUp },
  { to: "/app/biz/analysis", label: () => s("nav.bizAnalysis"), icon: LineChart },
  { to: "/app/biz/report", label: () => s("nav.bizReport"), icon: ReceiptText },
  { to: "/app/biz/insights", label: () => s("nav.bizInsights"), icon: Users },
];

export const SETTINGS_ITEM: NavItem = { to: "/app/settings", label: () => s("nav.settings"), icon: Settings };

/** Which half of the product the current path belongs to. */
export function segmentOf(pathname: string): Segment {
  return pathname.startsWith("/app/biz") || pathname === "/merchant" ? "shop" : "personal";
}

/** Active when the path is the item itself or sits below it (`/app` matches only itself). */
export function isActive(pathname: string, to: string): boolean {
  if (to === "/app" || to === "/app/biz") return pathname === to;
  return pathname === to || pathname.startsWith(`${to}/`);
}

/** Toolbar title for the current path: the deepest matching nav item, else the shell name. */
export function titleOf(pathname: string): string {
  const all = [...PERSONAL_NAV, ...SHOP_NAV, SETTINGS_ITEM];
  const hit = all.filter((i) => isActive(pathname, i.to)).sort((a, b) => b.to.length - a.to.length)[0];
  return hit ? hit.label() : pathname.startsWith("/app/biz") ? s("seg.shop") : s("brand");
}

