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

export type Segment = "personal" | "shop";

export interface NavItem {
  /** hash path, e.g. `/app/ledger` */
  to: string;
  label: () => string;
  icon: LucideIcon;
}

/**
 * Nav targets are plain hash paths on purpose: the shop pages live on another branch, so typed
 * `Link`s would tie this shell to routes that do not exist yet. The router uses hash history,
 * so setting `location.hash` navigates without a reload.
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

/** Navigate by hash (same-document), keeping the TanStack hash history in charge. */
export function go(to: string) {
  if (window.location.hash.replace(/^#/, "") === to) return;
  window.location.hash = to;
}
