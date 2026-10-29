import * as SwitchPrimitive from "@radix-ui/react-switch";
import { motion } from "framer-motion";
import { ArrowDownRight, ArrowUpRight, Search, type LucideIcon } from "lucide-react";
import { useId, type ButtonHTMLAttributes, type InputHTMLAttributes, type ReactNode } from "react";
import { SPRING_THUMB } from "@/lib/motion";
import { cn } from "@/lib/utils";
import { money, type Delta } from "./format";
import { useCountUp } from "./motion";
import { s } from "./strings";

/* ---------------------------------------------------------------- surfaces */

/** Apple-style content card: surface fill, 12 radius, 20 padding, no shadow — depth comes from the canvas. */
export function Card({
  title,
  action,
  children,
  className,
  padded = true,
  fill = false,
}: {
  title?: ReactNode;
  action?: ReactNode;
  children: ReactNode;
  className?: string;
  padded?: boolean;
  /** body becomes a flex column that fills the card (chat panels, split views) */
  fill?: boolean;
}) {
  return (
    <section className={cn("d-lift flex min-w-0 flex-col rounded-xl bg-d-surface", className)}>
      {(title || action) && (
        <header className="flex items-center justify-between gap-3 px-5 pt-4">
          <h2 className="min-w-0 truncate text-[13px] font-semibold text-d-ink-2">{title}</h2>
          {action && <div className="shrink-0">{action}</div>}
        </header>
      )}
      <div className={cn("min-w-0 flex-1", fill && "flex min-h-0 flex-col", padded && "p-5", (title || action) && padded && "pt-3")}>{children}</div>
    </section>
  );
}

/* --------------------------------------------------------------------- kpi */

const SPEND_TONE = { up: "text-d-warning", down: "text-d-positive", flat: "text-d-ink-2" } as const;
const INCOME_TONE = { up: "text-d-positive", down: "text-d-warning", flat: "text-d-ink-2" } as const;

/**
 * Big number + change. `tone` says how to read the arrow: rising spending is not "good",
 * so it never borrows the success colour (the product does not judge spending).
 */
export function DeltaText({ d, vs, tone = "spend" }: { d: Delta; vs?: string; tone?: "spend" | "income" }) {
  const palette = tone === "income" ? INCOME_TONE : SPEND_TONE;
  const Arrow = d.dir === "down" ? ArrowDownRight : ArrowUpRight;
  return (
    <p className={cn("flex items-center gap-1 text-[12px] font-medium", palette[d.dir])}>
      {d.dir === "flat" ? (
        <span>{s("ov.flat").replace("{}", (vs ?? "").replace(/^vs\s*/i, "").replace(/^较/, ""))}</span>
      ) : (
        <>
          <Arrow className="size-3.5" strokeWidth={2} aria-hidden />
          <span className="num">{d.pct === null ? "—" : `${Math.abs(d.pct)}%`}</span>
        </>
      )}
      {vs && d.dir !== "flat" && <span className="text-d-ink-3">{vs}</span>}
    </p>
  );
}

export function Kpi({
  label,
  value,
  cents,
  delta,
  vs,
  tone = "spend",
  hint,
  suffix,
}: {
  label: string;
  /** already formatted value (counts, days) */
  value?: string;
  /** money in cents: the number rolls from 0 on first paint (§A.4) */
  cents?: number;
  delta?: Delta;
  vs?: string;
  tone?: "spend" | "income";
  hint?: string;
  suffix?: string;
}) {
  const rolled = useCountUp(cents ?? 0, 600, label);
  const shown = cents === undefined ? value : money(rolled);
  return (
    <div className="flex min-w-0 flex-col gap-1.5 rounded-xl bg-d-surface p-5">
      <p className="text-[12px] font-medium text-d-ink-2">{label}</p>
      <p className="num text-[28px] font-semibold leading-[34px] tracking-[-0.01em] text-d-ink">
        {shown}
        {suffix && <span className="ml-0.5 text-[15px] font-medium text-d-ink-2">{suffix}</span>}
      </p>
      {delta ? <DeltaText d={delta} vs={vs} tone={tone} /> : hint ? <p className="text-[12px] text-d-ink-3">{hint}</p> : <span className="h-[18px]" />}
    </div>
  );
}

/* ----------------------------------------------------------------- buttons */

type ButtonProps = ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: "primary" | "quiet" | "ghost" | "danger";
  size?: "sm" | "md";
  icon?: LucideIcon;
};

const BUTTON_FILL = {
  primary: "bg-d-ink text-d-canvas hover:bg-d-ink/90",
  quiet: "bg-d-fill text-d-ink hover:bg-d-hairline",
  ghost: "text-d-accent-ink hover:bg-d-fill/70",
  danger: "bg-d-negative text-white hover:opacity-90",
} as const;

export function Button({ variant = "quiet", size = "md", icon: Icon, className, children, ...rest }: ButtonProps) {
  return (
    <button
      type="button"
      {...rest}
      className={cn(
        "d-press inline-flex shrink-0 items-center justify-center gap-1.5 rounded-lg font-medium transition-colors",
        size === "sm" ? "h-8 px-3 text-[12.5px]" : "h-9 px-3.5 text-[13px]",
        BUTTON_FILL[variant],
        "focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-d-accent-ink disabled:opacity-40",
        className,
      )}
    >
      {Icon && <Icon className={size === "sm" ? "size-3.5" : "size-4"} strokeWidth={1.75} aria-hidden />}
      {children}
    </button>
  );
}

export function IconButton({
  icon: Icon,
  label,
  active,
  className,
  ...rest
}: ButtonHTMLAttributes<HTMLButtonElement> & { icon: LucideIcon; label: string; active?: boolean }) {
  return (
    <button
      type="button"
      aria-label={label}
      title={label}
      {...rest}
      className={cn(
        "d-press grid size-8 shrink-0 place-items-center rounded-lg text-d-ink-2 transition-colors hover:bg-d-fill hover:text-d-ink",
        active && "bg-d-fill text-d-ink",
        "focus-visible:outline-2 focus-visible:outline-offset-1 focus-visible:outline-d-accent-ink",
        className,
      )}
    >
      <Icon className="size-4" strokeWidth={1.75} aria-hidden />
    </button>
  );
}

/* --------------------------------------------------------------- segmented */

export function Segmented<T extends string>({
  value,
  options,
  onChange,
  label,
  size = "md",
  className,
}: {
  value: T;
  options: readonly { value: T; label: string }[];
  onChange: (v: T) => void;
  label: string;
  size?: "sm" | "md";
  className?: string;
}) {
  const id = useId();
  return (
    <div role="tablist" aria-label={label} className={cn("inline-flex shrink-0 items-center rounded-lg bg-d-fill p-[2px]", className)}>
      {options.map((o) => {
        const on = o.value === value;
        return (
          <button
            key={o.value}
            role="tab"
            aria-selected={on}
            type="button"
            onClick={() => onChange(o.value)}
            className={cn(
              "d-press relative rounded-[7px] font-medium transition-colors",
              size === "sm" ? "h-6 px-2.5 text-[12px]" : "h-7 px-3 text-[12.5px]",
              on ? "text-d-ink" : "text-d-ink-2 hover:text-d-ink",
            )}
          >
            {on && <motion.span aria-hidden layoutId={`seg-${id}`} transition={SPRING_THUMB} className="absolute inset-0 rounded-[7px] bg-d-surface shadow-[0_1px_2px_oklch(0_0_0/8%)]" />}
            <span className="relative">{o.label}</span>
          </button>
        );
      })}
    </div>
  );
}

/* ------------------------------------------------------------------ inputs */

export function SearchInput({ className, ...rest }: InputHTMLAttributes<HTMLInputElement>) {
  return (
    <div className={cn("flex h-8 min-w-0 items-center gap-1.5 rounded-lg bg-d-fill px-2.5 text-d-ink-3", className)}>
      <Search className="size-3.5 shrink-0" strokeWidth={1.9} aria-hidden />
      <input {...rest} className="min-w-0 flex-1 bg-transparent text-[13px] text-d-ink outline-none placeholder:text-d-ink-3" />
    </div>
  );
}

export function Field({ label, children, hint }: { label: string; children: ReactNode; hint?: string }) {
  return (
    <label className="flex min-w-0 flex-col gap-1.5">
      <span className="text-[12px] font-medium text-d-ink-2">{label}</span>
      {children}
      {hint && <span className="text-[11.5px] text-d-ink-3">{hint}</span>}
    </label>
  );
}

export const inputClass =
  "h-9 w-full min-w-0 rounded-lg bg-d-fill px-3 text-[13px] text-d-ink outline-none placeholder:text-d-ink-3 focus-visible:outline-2 focus-visible:outline-offset-1 focus-visible:outline-d-accent-ink";

export function TextInput({ className, ...rest }: InputHTMLAttributes<HTMLInputElement>) {
  return <input {...rest} className={cn(inputClass, className)} />;
}

export function Toggle({ checked, onChange, label }: { checked: boolean; onChange: (v: boolean) => void; label: string }) {
  return (
    <SwitchPrimitive.Root
      checked={checked}
      onCheckedChange={onChange}
      aria-label={label}
      className={cn(
        "relative h-[22px] w-[38px] shrink-0 rounded-full transition-colors duration-200 ease-[var(--ease-out)]",
        checked ? "bg-d-accent" : "bg-d-hairline",
        "focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-d-accent-ink",
      )}
    >
      <SwitchPrimitive.Thumb className="block size-[18px] translate-x-[2px] rounded-full bg-white shadow-sm transition-transform duration-200 ease-[var(--ease-out)] data-[state=checked]:translate-x-[18px]" />
    </SwitchPrimitive.Root>
  );
}

/* ------------------------------------------------------------------- chips */

export function Chip({ children, onRemove, onClick, active }: { children: ReactNode; onRemove?: () => void; onClick?: () => void; active?: boolean }) {
  const base = cn(
    "inline-flex h-6 items-center gap-1 rounded-md px-2 text-[12px]",
    active ? "bg-d-accent/10 text-d-accent-ink" : "bg-d-fill text-d-ink-2",
  );
  if (onClick) {
    return (
      <button type="button" onClick={onClick} className={cn(base, "d-press hover:text-d-ink")}>
        {children}
      </button>
    );
  }
  return (
    <span className={base}>
      {children}
      {onRemove && (
        <button type="button" onClick={onRemove} aria-label={s("ledger.clearFilters")} className="d-press -mr-0.5 grid size-4 place-items-center rounded text-d-ink-3 hover:text-d-ink">
          ×
        </button>
      )}
    </span>
  );
}

/* ------------------------------------------------------------------ states */

export function EmptyState({
  icon: Icon,
  title,
  hint,
  action,
  art,
  className,
}: {
  icon?: LucideIcon;
  title: string;
  hint?: string;
  action?: ReactNode;
  /** photo/illustration under `public/img`; the frame keeps its crop consistent across pages */
  art?: string;
  className?: string;
}) {
  return (
    <div className={cn("flex flex-col items-center justify-center px-8 py-12 text-center", className)}>
      {art ? (
        <img
          src={art}
          alt=""
          aria-hidden
          className="mb-4 h-[104px] w-[184px] rounded-xl bg-d-fill object-cover ring-1 ring-d-hairline"
        />
      ) : Icon ? (
        <div className="relative mb-4 grid size-14 place-items-center rounded-full bg-d-fill">
          <Icon className="size-6 text-d-ink-3" strokeWidth={1.5} aria-hidden />
        </div>
      ) : null}
      <p className="text-[15px] font-semibold text-d-ink">{title}</p>
      {hint && <p className="mt-1.5 max-w-[22rem] text-[13px] leading-[20px] text-d-ink-2">{hint}</p>}
      {action && <div className="mt-5">{action}</div>}
    </div>
  );
}

/** First-paint skeleton; the caller keeps it up for at least 200ms so it never flashes. */
export function Skeleton({ rows = 5, className }: { rows?: number; className?: string }) {
  return (
    <div className={cn("space-y-px", className)} role="status" aria-label={s("common.loading")}>
      {Array.from({ length: rows }, (_, i) => (
        <div key={i} className="flex items-center gap-3 rounded-lg bg-d-surface px-4 py-2.5">
          <div className="d-shimmer size-7 shrink-0 rounded-md" />
          <div className="min-w-0 flex-1 space-y-1.5">
            <div className="d-shimmer h-2.5 w-1/3 rounded" />
            <div className="d-shimmer h-2 w-1/5 rounded" />
          </div>
          <div className="d-shimmer h-2.5 w-14 rounded" />
        </div>
      ))}
    </div>
  );
}

/* ------------------------------------------------------------------ layout */

/** A quiet heading row inside the content column (page-level toolbars sit here). */
export function PageBar({ children, className }: { children: ReactNode; className?: string }) {
  return <div className={cn("mb-4 flex min-h-8 flex-wrap items-center gap-2", className)}>{children}</div>;
}

/** Grey caption under a card or a table. */
export function Hint({ children, className }: { children: ReactNode; className?: string }) {
  return <p className={cn("text-[11.5px] leading-[16px] text-d-ink-3", className)}>{children}</p>;
}

/** Small keyboard cap, used next to shortcuts and in the command palette. */
export function Kbd({ children }: { children: ReactNode }) {
  return <kbd className="rounded border border-d-hairline px-1 py-px font-sans text-[10.5px] leading-[14px] text-d-ink-3">{children}</kbd>;
}
