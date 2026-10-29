import { PanelLeftClose, PanelLeftOpen, ReceiptText, Store, Users } from "lucide-react";
import { useRouterState } from "@tanstack/react-router";
import { motion } from "framer-motion";
import { SPRING_THUMB } from "@/lib/motion";
import { SPRING_SLIDE } from "./motion";
import { cn } from "@/lib/utils";
import { PERSONAL_NAV, SETTINGS_ITEM, SHOP_NAV, go, isActive, segmentOf, type NavItem, type Segment } from "./nav";
import { s } from "./strings";
import { IconButton } from "./ui";

function NavRow({ item, active, collapsed }: { item: NavItem; active: boolean; collapsed: boolean }) {
  const Icon = item.icon;
  return (
    <a
      href={`#${item.to}`}
      onClick={(e) => {
        e.preventDefault();
        go(item.to);
      }}
      aria-current={active ? "page" : undefined}
      title={collapsed ? item.label() : undefined}
      className={cn(
        "d-press relative flex h-[30px] items-center overflow-hidden rounded-lg text-[13px] font-medium transition-colors",
        collapsed ? "mx-auto w-8 justify-center" : "gap-2 px-2.5",
        active ? "text-d-ink" : "text-d-ink-2 hover:bg-d-fill/50 hover:text-d-ink",
        active && collapsed && "bg-d-fill",
      )}
    >
      {active && !collapsed && <motion.span aria-hidden layoutId="side-nav-active" transition={SPRING_SLIDE} className="absolute inset-0 rounded-lg bg-d-fill" />}
      <Icon className="relative size-4 shrink-0" strokeWidth={1.75} aria-hidden />
      <motion.span animate={{ opacity: collapsed ? 0 : 1 }} transition={{ duration: 0.1 }} className="relative truncate whitespace-nowrap">
        {item.label()}
      </motion.span>
    </a>
  );
}

function SegmentSwitch({ segment, collapsed }: { segment: Segment; collapsed: boolean }) {
  const options: { value: Segment; label: string; icon: typeof Users; to: string }[] = [
    { value: "personal", label: s("seg.personal"), icon: Users, to: "/app" },
    { value: "shop", label: s("seg.shop"), icon: Store, to: "/app/biz" },
  ];
  if (collapsed) {
    return (
      <div className="flex flex-col items-center gap-1 py-2">
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
              className={cn("d-press grid size-8 place-items-center rounded-lg transition-colors", segment === o.value ? "bg-d-surface text-d-ink shadow-[0_1px_2px_oklch(0_0_0/8%)]" : "text-d-ink-3 hover:text-d-ink")}
            >
              <Icon className="size-4" strokeWidth={1.75} aria-hidden />
            </button>
          );
        })}
      </div>
    );
  }
  return (
    <div role="tablist" aria-label={s("nav.label")} className="mx-2 mt-1 flex h-7 items-center rounded-lg bg-d-fill p-[2px]">
      {options.map((o) => {
        const on = segment === o.value;
        return (
          <button
            key={o.value}
            role="tab"
            aria-selected={on}
            type="button"
            onClick={() => go(o.to)}
            className={cn("d-press relative h-6 flex-1 rounded-[7px] text-[12px] font-medium", on ? "text-d-ink" : "text-d-ink-2 hover:text-d-ink")}
          >
            {on && <motion.span aria-hidden layoutId="side-seg" transition={SPRING_THUMB} className="absolute inset-0 rounded-[7px] bg-d-surface shadow-[0_1px_2px_oklch(0_0_0/8%)]" />}
            <span className="relative">{o.label}</span>
          </button>
        );
      })}
    </div>
  );
}

export function Sidebar({ collapsed, pathname, onToggle, toggleLabel }: { collapsed: boolean; pathname: string; onToggle: () => void; toggleLabel?: string }) {
  const segment = segmentOf(pathname);
  const items = segment === "shop" ? SHOP_NAV : PERSONAL_NAV;
  return (
    <motion.aside
      aria-label={s("nav.label")}
      animate={{ width: collapsed ? 64 : 232 }}
      transition={{ duration: 0.22, ease: [0.23, 1, 0.32, 1] }}
      className="relative z-20 flex h-full shrink-0 flex-col border-r border-d-hairline bg-d-canvas/80 backdrop-blur-xl"
    >
      <div className={cn("flex h-[52px] shrink-0 items-center", collapsed ? "justify-center" : "gap-2 px-4")}>
        <a
          href="#/app"
          onClick={(e) => {
            e.preventDefault();
            go("/app");
          }}
          className="d-press flex min-w-0 items-center gap-2 rounded-md text-d-ink"
          title={s("brand")}
        >
          <span className="grid size-6 shrink-0 place-items-center rounded-md bg-d-ink text-d-canvas">
            <ReceiptText className="size-3.5" strokeWidth={2} aria-hidden />
          </span>
          {!collapsed && <span className="truncate text-[13px] font-semibold tracking-[-0.01em]">{s("brand")}</span>}
        </a>
      </div>

      <SegmentSwitch segment={segment} collapsed={collapsed} />

      {!collapsed && <p className="px-4 pb-1 pt-4 text-[11px] font-semibold uppercase tracking-[0.06em] text-d-ink-3">{segment === "shop" ? s("seg.shop") : s("seg.personal")}</p>}

      <nav className={cn("hide-scrollbar min-h-0 flex-1 space-y-0.5 overflow-y-auto pb-2", collapsed ? "px-2 pt-2" : "px-2 pt-1")}>
        {items.map((item) => (
          <NavRow key={item.to} item={item} active={isActive(pathname, item.to)} collapsed={collapsed} />
        ))}
      </nav>

      <div className={cn("mt-auto border-t border-d-hairline py-2", collapsed ? "px-2" : "px-2")}>
        <NavRow item={SETTINGS_ITEM} active={isActive(pathname, SETTINGS_ITEM.to)} collapsed={collapsed} />
        {!collapsed && (
          <button
            type="button"
            onClick={onToggle}
            className="d-press mt-1 flex h-[30px] w-full items-center gap-2 rounded-lg px-2.5 text-[12px] text-d-ink-3 transition-colors hover:bg-d-fill/50 hover:text-d-ink"
          >
            <PanelLeftClose className="size-4" strokeWidth={1.75} aria-hidden />
            {toggleLabel ?? s("nav.collapse")}
          </button>
        )}
      </div>

      {collapsed && (
        <div className="border-t border-d-hairline py-2">
          <IconButton icon={PanelLeftOpen} label={s("nav.expand")} onClick={onToggle} className="mx-auto" />
        </div>
      )}
    </motion.aside>
  );
}
