import { motion } from "framer-motion";
import { ChevronRight, type LucideIcon } from "lucide-react";
import * as SwitchPrimitive from "@radix-ui/react-switch";
import { useId, type ButtonHTMLAttributes, type ReactNode } from "react";
import { SPRING_THUMB } from "@/lib/motion";
import { cn } from "@/lib/utils";

/**
 * Shared building blocks for Design System v2 (docs/DESIGN.md §4).
 * Prefer these over ad-hoc cards: structure comes from grouping and hairlines, not shadows.
 */

/** Footnote-style header above a grouped section. */
export function SectionHeader({ children, action }: { children: ReactNode; action?: ReactNode }) {
  return (
    <div className="flex items-end justify-between px-8 pb-2 pt-6">
      <h2 className="text-[13px] font-normal leading-[18px] text-ink-2">{children}</h2>
      {action}
    </div>
  );
}

/**
 * Inset grouped container: one rounded surface, rows separated by inset hairlines.
 * `inset="icon"` starts the hairline at the text of rows that have a 30px IconTile; default starts at the row edge padding.
 */
export function Group({ children, className, inset = "plain" }: { children: ReactNode; className?: string; inset?: "plain" | "icon" }) {
  return (
    <div
      className={cn(
        "mx-4 overflow-hidden rounded-[18px] bg-surface",
        "[&>*+*]:relative [&>*+*]:after:pointer-events-none [&>*+*]:after:absolute [&>*+*]:after:right-0 [&>*+*]:after:top-0 [&>*+*]:after:h-px [&>*+*]:after:bg-hairline [&>*+*]:after:content-['']",
        inset === "icon" ? "[&>*+*]:after:left-[58px]" : "[&>*+*]:after:left-4",
        className,
      )}
    >
      {children}
    </div>
  );
}

/** Rounded square holding a line icon (or a single emoji on receipts/badges only). */
export type Tone = "neutral" | "clay" | "blue" | "green" | "amber" | "violet";
export const TONE_CLASS: Record<Tone, string> = {
  neutral: "bg-fill text-ink",
  clay: "bg-clay-soft text-clay",
  blue: "bg-blue-soft text-blue",
  green: "bg-green-soft text-green",
  amber: "bg-amber-soft text-amber",
  violet: "bg-violet-soft text-violet",
};

export function IconTile({ icon: Icon, tone = "neutral", size = 30 }: { icon: LucideIcon; tone?: Tone; size?: number }) {
  return (
    <span
      className={cn("grid shrink-0 place-items-center rounded-[9px]", TONE_CLASS[tone])}
      style={{ width: size, height: size }}
      aria-hidden
    >
      <Icon className="size-[17px]" strokeWidth={1.75} />
    </span>
  );
}

/** A list row: leading tile, title/subtitle, trailing value, optional chevron. Press feedback on pointer-down. */
export function Row({
  leading,
  title,
  subtitle,
  trailing,
  chevron,
  onClick,
  className,
}: {
  leading?: ReactNode;
  title: ReactNode;
  subtitle?: ReactNode;
  trailing?: ReactNode;
  chevron?: boolean;
  onClick?: () => void;
  className?: string;
}) {
  const body = (
    <>
      {leading}
      <span className="min-w-0 flex-1">
        <span className="block truncate text-[16px] leading-[22px] text-ink">{title}</span>
        {subtitle && <span className="block truncate text-[13px] leading-[18px] text-ink-2">{subtitle}</span>}
      </span>
      {trailing && <span className="num shrink-0 text-[16px] text-ink">{trailing}</span>}
      {chevron && <ChevronRight className="size-4 shrink-0 text-ink-3" strokeWidth={2} aria-hidden />}
    </>
  );
  const cls = cn("flex min-h-[52px] w-full items-center gap-3 px-4 py-2.5 text-left", className);
  return onClick ? (
    <button type="button" onClick={onClick} className={cn(cls, "transition-colors duration-100 active:bg-surface-2")}>
      {body}
    </button>
  ) : (
    <div className={cls}>{body}</div>
  );
}

/** 32px round icon button for nav rows: `fill` (default), `ink` (the primary round action) or `clay`. */
export function IconButton({
  icon: Icon,
  label,
  tone = "fill",
  className,
  ...props
}: { icon: LucideIcon; label: string; tone?: "fill" | "ink" | "clay" } & Omit<ButtonHTMLAttributes<HTMLButtonElement>, "children">) {
  return (
    <button
      type="button"
      aria-label={label}
      {...props}
      className={cn(
        "press relative grid size-8 shrink-0 place-items-center rounded-full before:absolute before:-inset-1.5 before:content-['']",
        tone === "fill" && "bg-fill text-ink",
        tone === "ink" && "bg-ink text-bg",
        tone === "clay" && "bg-clay text-on-clay",
        className,
      )}
    >
      <Icon className="size-[17px]" strokeWidth={1.9} aria-hidden />
    </button>
  );
}

/** Selectable capsule: quiet `fill` when idle, ink when selected. */
export function Chip({
  selected,
  className,
  ...props
}: ButtonHTMLAttributes<HTMLButtonElement> & { selected?: boolean }) {
  return (
    <button
      type="button"
      {...props}
      className={cn(
        "press inline-flex h-9 shrink-0 items-center rounded-full px-3.5 text-[14px] font-medium",
        selected ? "bg-ink text-bg" : "bg-fill text-ink-2",
        className,
      )}
    />
  );
}

/** iOS-style switch built on Radix; 51x31 with a soft thumb. */
export function Switch({ checked, onCheckedChange, label }: { checked: boolean; onCheckedChange: (v: boolean) => void; label: string }) {
  return (
    <SwitchPrimitive.Root
      checked={checked}
      onCheckedChange={onCheckedChange}
      aria-label={label}
      className="relative h-[31px] w-[51px] shrink-0 rounded-full bg-fill transition-colors duration-200 ease-[var(--ease-out)] data-[state=checked]:bg-positive"
    >
      <SwitchPrimitive.Thumb className="block size-[27px] translate-x-[2px] rounded-full bg-white shadow-[0_2px_4px_oklch(0_0_0/22%)] transition-transform duration-200 ease-[var(--ease-out)] data-[state=checked]:translate-x-[22px]" />
    </SwitchPrimitive.Root>
  );
}

/** Small ink-2 explanatory text that sits under a group. */
export function Footnote({ children, className }: { children: ReactNode; className?: string }) {
  return <p className={cn("px-8 pt-2 text-[13px] leading-[18px] text-ink-2", className)}>{children}</p>;
}

type ButtonVariant = "primary" | "clay" | "tinted" | "secondary" | "plain";

/** Buttons: primary/clay = the one solid pill per screen, tinted = accent 14% fill, secondary = gray fill, plain = text. */
export function XButton({
  variant = "secondary",
  size = "lg",
  className,
  ...props
}: ButtonHTMLAttributes<HTMLButtonElement> & { variant?: ButtonVariant; size?: "lg" | "sm" }) {
  return (
    <button
      type="button"
      {...props}
      className={cn(
        "press inline-flex items-center justify-center gap-1.5 rounded-full font-medium",
        size === "lg" ? "h-[50px] px-6 text-[16px] font-semibold" : "h-9 px-4 text-[14px]",
        variant === "primary" && "bg-ink text-bg",
        variant === "clay" && "bg-clay text-on-clay",
        variant === "tinted" && "bg-clay/14 text-clay",
        variant === "secondary" && "bg-fill text-ink",
        variant === "plain" && "px-1 text-clay",
        className,
      )}
    />
  );
}

/** Apple segmented control; the selected thumb slides with a critically damped spring. */
export function Segmented<T extends string>({
  value,
  options,
  onChange,
  className,
}: {
  value: T;
  options: readonly { value: T; label: string }[];
  onChange: (v: T) => void;
  className?: string;
}) {
  const id = useId();
  return (
    <div role="tablist" className={cn("flex rounded-[10px] bg-fill p-[2px]", className)}>
      {options.map((o) => {
        const active = o.value === value;
        return (
          <button
            key={o.value}
            role="tab"
            aria-selected={active}
            onClick={() => onChange(o.value)}
            className={cn("relative h-8 flex-1 rounded-[8px] text-[13px] font-medium", active ? "text-ink" : "text-ink-2")}
          >
            {active && (
              <motion.span
                layoutId={`seg-${id}`}
                className="absolute inset-0 rounded-[8px] bg-surface dark:bg-surface-2"
                transition={SPRING_THUMB}
              />
            )}
            <span className="relative">{o.label}</span>
          </button>
        );
      })}
    </div>
  );
}

/** Money with a smaller, quieter ¥ (§3). */
export function Money({ cents, className, sign }: { cents: number; className?: string; sign?: boolean }) {
  const v = Math.abs(cents) / 100;
  const full = v.toLocaleString("zh-CN", { minimumFractionDigits: v % 1 ? 2 : 0, maximumFractionDigits: 2 });
  const dot = full.indexOf(".");
  return (
    <span className={cn("num", className)}>
      {sign && (cents < 0 ? "−" : "+")}
      <span className="mr-[0.08em] text-[0.6em] text-ink-2">¥</span>
      {dot < 0 ? full : full.slice(0, dot)}
      {dot >= 0 && <span className="text-[0.72em]">{full.slice(dot)}</span>}
    </span>
  );
}
