import { Link } from "@tanstack/react-router";
import { useAccount } from "@/services/account";
import { PanelLeftClose, PanelLeftOpen, Store, Users } from "lucide-react";
import { useRouterState } from "@tanstack/react-router";
import { motion } from "framer-motion";
import { SPRING_THUMB } from "@/lib/motion";
import { SPRING_SLIDE } from "./motion";
import { cn } from "@/lib/utils";
import { BrandMark } from "@/components/brand/BrandMark";
import { PERSONAL_NAV, SETTINGS_ITEM, SHOP_NAV, go, hrefOf, isActive, isModifiedClick, segmentOf, type NavItem, type NavPath, type Segment } from "./nav";
import { s } from "./strings";
import { IconButton } from "./ui";

function NavRow({ item, active, collapsed }: { item: NavItem; active: boolean; collapsed: boolean }) {
  const Icon = item.icon;
  return (
    <a
      href={hrefOf(item.to)}
      onClick={(e) => {
        if (isModifiedClick(e)) return;
        e.preventDefault();
        go(item.to);
      }}
      aria-current={active ? "page" : undefined}
      title={collapsed ? item.label() : undefined}
      className={cn(
        "d-press relative flex h-9 items-center rounded-[10px] text-[13px] font-medium transition-colors",
        "focus-visible:outline-2 focus-visible:outline-offset-1 focus-visible:outline-d-accent-ink",
        collapsed ? "mx-auto size-10 justify-center rounded-[11px]" : "gap-2.5 pl-3 pr-2.5",
        active ? "text-d-ink" : "text-d-ink-2 hover:bg-d-fill/50 hover:text-d-ink",
        active && collapsed && "bg-d-accent/12 text-d-accent-ink",
      )}
    >
      {active && !collapsed && <motion.span aria-hidden layoutId="side-nav-active" transition={SPRING_SLIDE} className="absolute inset-0 rounded-lg bg-d-accent/12" />}
      {/* 2px warm bar marks the current page; it slides between rows with the same spring. */}
      {active && !collapsed && (
        <motion.span aria-hidden layoutId="side-nav-bar" transition={SPRING_SLIDE} className="absolute left-1.5 top-1/2 h-3.5 w-[2px] -translate-y-1/2 rounded-full bg-d-accent" />
      )}
      <Icon className={cn("relative shrink-0", collapsed ? "size-[18px]" : "size-[17px]", active && (collapsed ? "text-d-accent-ink" : "text-d-accent-ink"))} strokeWidth={active ? 2 : 1.75} aria-hidden />
      <motion.span animate={{ opacity: collapsed ? 0 : 1 }} transition={{ duration: 0.1 }} className="relative truncate whitespace-nowrap">
        {item.label()}
      </motion.span>
    </a>
  );
}

function SegmentSwitch({ segment, collapsed }: { segment: Segment; collapsed: boolean }) {
  const options: { value: Segment; label: string; icon: typeof Users; to: NavPath }[] = [
    { value: "personal", label: s("seg.personal"), icon: Users, to: "/app" },
    { value: "shop", label: s("seg.shop"), icon: Store, to: "/app/biz" },
  ];
  if (collapsed) {
    return (
      <div role="group" aria-label={s("nav.label")} className="flex flex-col items-center gap-1 pb-2 pt-1">
        {options.map((o) => {
          const Icon = o.icon;
          return (
            <button
              key={o.value}
              type="button"
              aria-pressed={segment === o.value}
              aria-label={o.label}
              title={o.label}
              onClick={() => go(o.to)}
              className={cn("d-press grid size-10 place-items-center rounded-[11px] transition-colors", segment === o.value ? "bg-d-accent/12 text-d-accent-ink" : "text-d-ink-3 hover:text-d-ink")}
            >
              <Icon className="size-[18px]" strokeWidth={segment === o.value ? 2 : 1.75} aria-hidden />
            </button>
          );
        })}
              </div>
    );
  }
  return (
    <div role="tablist" aria-label={s("nav.label")} className="mx-2 mt-3 flex h-[30px] items-center rounded-[10px] bg-d-fill p-[2px]">
      {options.map((o) => {
        const on = segment === o.value;
        return (
          <button
            key={o.value}
            role="tab"
            aria-selected={on}
            type="button"
            onClick={() => go(o.to)}
            className={cn("d-press relative h-[26px] flex-1 rounded-lg text-[12px] font-medium", on ? "text-d-ink" : "text-d-ink-2 hover:text-d-ink")}
          >
            {on && <motion.span aria-hidden layoutId="side-seg" transition={SPRING_THUMB} className="absolute inset-0 rounded-lg bg-d-accent/12" />}
            <span className={cn("relative", on && "text-d-accent-ink")}>{o.label}</span>
          </button>
        );
      })}
    </div>
  );
}

export function Sidebar({ collapsed, pathname, onToggle, toggleLabel }: { collapsed: boolean; pathname: string; onToggle: () => void; toggleLabel?: string }) {
  const user = useAccount();
  const segment = segmentOf(pathname);
  const items = segment === "shop" ? SHOP_NAV : PERSONAL_NAV;
  return (
    <motion.aside
      aria-label={s("nav.label")}
      animate={{ width: collapsed ? 64 : 232 }}
      transition={{ duration: 0.22, ease: [0.23, 1, 0.32, 1] }}
      className="relative z-20 flex h-full shrink-0 flex-col border-r border-d-hairline bg-d-canvas/80 backdrop-blur-xl"
    >
      <div className={cn("flex h-14 shrink-0 items-center", collapsed ? "justify-center" : "gap-2.5 px-4")}>
        <a
          href={hrefOf("/app")}
          onClick={(e) => {
            if (isModifiedClick(e)) return;
            e.preventDefault();
            go("/app");
          }}
          className={cn(
            "d-press flex min-w-0 items-center rounded-md text-d-ink",
            collapsed ? "size-10 justify-center rounded-[11px]" : "gap-2",
          )}
          title={s("brand")}
        >
          <BrandMark className="size-[26px] shrink-0" />
          {!collapsed && <span className="truncate text-[13px] font-semibold tracking-[-0.01em]">{s("brand")}</span>}
        </a>
      </div>

      <SegmentSwitch segment={segment} collapsed={collapsed} />

      {!collapsed && <p className="px-4 pb-1.5 pt-5 text-[11px] font-semibold uppercase tracking-[0.06em] text-d-ink-3">{segment === "shop" ? s("seg.shop") : s("seg.personal")}</p>}

      <nav className={cn("hide-scrollbar min-h-0 flex-1 overflow-y-auto pb-2", collapsed ? "space-y-1 px-2 pt-2" : "space-y-0.5 px-2 pt-1")}>
        {items.map((item) => (
          <NavRow key={item.to} item={item} active={isActive(pathname, item.to)} collapsed={collapsed} />
        ))}
      </nav>

      <div className={cn("mt-auto border-t border-d-hairline py-2", collapsed ? "px-2" : "px-2")}>
        <Link to="/login" title={user?.email ?? "登录 / Sign in"} className="mb-1 flex min-h-10 items-center gap-2 rounded-lg px-2.5 text-[13px] text-d-ink-2 hover:bg-d-fill"><span className="grid size-6 shrink-0 place-items-center rounded-full bg-d-fill text-xs">{user ? user.username[0].toUpperCase() : "我"}</span>{!collapsed && <span className="truncate">{user?.username ?? "登录 / Sign in"}</span>}</Link>
        <NavRow item={SETTINGS_ITEM} active={isActive(pathname, SETTINGS_ITEM.to)} collapsed={collapsed} />
        {!collapsed && (
          <button
            type="button"
            onClick={onToggle}
            className="d-press mt-1 flex h-9 w-full items-center gap-2 rounded-[10px] px-2.5 text-[12px] text-d-ink-3 transition-colors hover:bg-d-fill/50 hover:text-d-ink"
          >
            <PanelLeftClose className="size-4" strokeWidth={1.75} aria-hidden />
            {toggleLabel ?? s("nav.collapse")}
          </button>
        )}
      </div>

      {collapsed && (
        <div className="py-2">
          <IconButton icon={PanelLeftOpen} label={s("nav.expand")} onClick={onToggle} className="mx-auto size-10 rounded-[11px]" />
        </div>
      )}
    </motion.aside>
  );
}
