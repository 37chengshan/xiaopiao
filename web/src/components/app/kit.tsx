import { motion } from "framer-motion";
import { ChevronRight, type LucideIcon } from "lucide-react";
import { useId, type ButtonHTMLAttributes, type ReactNode } from "react";
import { cn } from "@/lib/utils";

/**
 * Shared building blocks for Design System v2 (docs/DESIGN.md §4).
 * Prefer these over ad-hoc cards: structure comes from grouping and hairlines, not shadows.
 */

/** Footnote-style header above a grouped section. */
export function SectionHeader({ children, action }: { children: ReactNode; action?: ReactNode }) {
  return (
    <div className="flex items-end justify-between px-4 pb-2 pt-6">
      <h2 className="text-[13px] font-normal leading-[18px] text-ink-2">{children}</h2>
      {action}
    </div>
  );
}

/** Inset grouped container: one rounded surface, rows separated by inset hairlines. */
export function Group({ children, className }: { children: ReactNode; className?: string }) {
  return (
    <div className={cn("mx-4 overflow-hidden rounded-[14px] bg-surface [&>*+*]:shadow-[inset_0_1px_0_var(--hairline)]", className)}>
      {children}
    </div>
  );
}

/** Rounded square holding a line icon (or a single emoji on receipts/badges only). */
export function IconTile({ icon: Icon, tone = "neutral", size = 30 }: { icon: LucideIcon; tone?: "neutral" | "clay"; size?: number }) {
  return (
    <span
      className={cn("grid shrink-0 place-items-center rounded-[8px]", tone === "clay" ? "bg-clay-soft text-clay" : "bg-fill text-ink")}
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

type ButtonVariant = "primary" | "clay" | "secondary" | "plain";

/** Buttons (§4): primary = ink, clay = the one accent CTA per screen, secondary = fill, plain = text. */
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
        "inline-flex items-center justify-center gap-1.5 font-medium transition-transform duration-100 ease-[var(--ease-out)] active:scale-[0.97] disabled:opacity-40 disabled:active:scale-100",
        size === "lg" ? "h-12 rounded-[12px] px-5 text-[16px]" : "h-9 rounded-[10px] px-3.5 text-[14px]",
        variant === "primary" && "bg-ink text-bg",
        variant === "clay" && "bg-clay text-on-clay",
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
                className="absolute inset-0 rounded-[8px] bg-surface shadow-[0_1px_3px_oklch(0_0_0/12%)] dark:bg-surface-2"
                transition={{ type: "spring", bounce: 0, duration: 0.3 }}
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
  const text = v.toLocaleString("zh-CN", { minimumFractionDigits: v % 1 ? 2 : 0, maximumFractionDigits: 2 });
  return (
    <span className={cn("num", className)}>
      {sign && (cents < 0 ? "−" : "+")}
      <span className="mr-[0.08em] text-[0.6em] text-ink-2">¥</span>
      {text}
    </span>
  );
}
