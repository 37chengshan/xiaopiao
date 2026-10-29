import { ArrowDown, ArrowUp, ChevronDown, ChevronUp, Minus, X } from "lucide-react";
import { AnimatePresence, motion } from "framer-motion";
import { useEffect, type ButtonHTMLAttributes, type ReactNode } from "react";
import { createPortal } from "react-dom";
import { EASE_OUT } from "@/lib/motion";
import { cn } from "@/lib/utils";
import type { Change } from "@/logic/biz/book";
import { CountUp, Reveal } from "./reveal";

/**
 * Desktop console primitives (docs/WEB-SPEC.md §A.2 / §B): flat surfaces, hairlines, one accent.
 * These are local to `features/biz` for now; the shared shell lane owns `components/desktop`.
 */

/**
 * Every `/app/biz/*` page renders inside the desktop shell, which already owns the canvas, the
 * 1200px column and its gutters. This used to repeat all three, so the shop pages sat 20px lower
 * and narrower than the personal ones (caught in the 2026-10-28 polish audit) — it is now only a
 * layout hook. The grid (`grid grid-cols-12 gap-5`) stays in each page.
 */
export function BizPage({ children, className }: { children: ReactNode; className?: string }) {
  return <div className={cn("min-w-0", className)}>{children}</div>;
}

export function Card({
  title,
  action,
  children,
  className,
  bodyClassName,
  index,
}: {
  title?: ReactNode;
  action?: ReactNode;
  children: ReactNode;
  className?: string;
  bodyClassName?: string;
  /** position in the page, for the 40ms-apart first-entry lift */
  index?: number;
}) {
  const section = (
    <section className="flex min-w-0 flex-1 flex-col rounded-xl bg-d-surface">
      {(title || action) && (
        <header className="flex min-h-[44px] items-center justify-between gap-3 px-5 pt-4">
          <h2 className="text-[13px] font-semibold leading-4 text-d-ink-2">{title}</h2>
          {action}
        </header>
      )}
      <div className={cn("min-w-0 flex-1 px-5 pb-5 pt-3", bodyClassName)}>{children}</div>
    </section>
  );
  const body =
    index === undefined ? (
      section
    ) : (
      <Reveal index={index} className="min-w-0 flex-1">
        {section}
      </Reveal>
    );
  return className ? <div className={cn("flex min-w-0", className)}>{body}</div> : body;
}

/** Big number with its baseline comparison. */
export function Kpi({
  label,
  value,
  unit,
  change,
  compareLabel,
  footer,
  className,
  count,
}: {
  label: string;
  /** static text, or `count` for a value that rolls up once on entry */
  value?: string;
  count?: { to: number; format: (value: number) => string };
  unit?: string;
  change?: Change | null;
  compareLabel?: string;
  footer?: ReactNode;
  className?: string;
}) {
  return (
    <div
      className={cn(
        "flex min-w-0 flex-col justify-between rounded-xl bg-d-surface px-5 py-4 transition-transform duration-[120ms] hover:-translate-y-px",
        className,
      )}
    >
      <p className="text-[12px] font-medium leading-4 text-d-ink-2">{label}</p>
      <p className="mt-2 flex items-baseline gap-1 text-[28px] font-semibold leading-8 tracking-[-0.02em] tabular-nums">
        {count ? <CountUp cents={count.to} format={count.format} /> : value}
        {unit && <span className="text-[13px] font-normal leading-4 text-d-ink-2">{unit}</span>}
      </p>
      <div className="mt-2 flex min-h-[16px] flex-wrap items-center gap-x-1.5 text-[12px] leading-4">
        {change ? <Delta change={change} compareLabel={compareLabel} /> : footer}
      </div>
    </div>
  );
}

/** ▲ / ▼ / — with the semantic colour, and the comparison named next to it. */
export function Delta({ change, compareLabel }: { change: Change; compareLabel?: string }) {
  const tone =
    !change.hasBase || change.diffCents === 0
      ? "text-d-ink-3"
      : change.diffCents > 0
        ? "text-d-positive"
        : "text-d-negative";
  const Icon = !change.hasBase || change.diffCents === 0 ? Minus : change.diffCents > 0 ? ArrowUp : ArrowDown;
  const text = !change.hasBase ? "—" : `${Math.abs(Math.round((change.pct ?? 0) * 100))}%`;
  return (
    <>
      <span className={cn("inline-flex items-center gap-0.5 font-medium tabular-nums", tone)}>
        <Icon className="size-3" strokeWidth={2.25} aria-hidden />
        {text}
      </span>
      {compareLabel && <span className="text-d-ink-3">{compareLabel}</span>}
    </>
  );
}

export function Segmented<T extends string>({
  value,
  options,
  onChange,
  label,
  className,
}: {
  value: T;
  options: ReadonlyArray<{ value: T; label: ReactNode }>;
  onChange: (value: T) => void;
  label: string;
  className?: string;
}) {
  return (
    <div role="tablist" aria-label={label} className={cn("inline-flex items-center gap-0.5 rounded-lg bg-d-fill p-0.5", className)}>
      {options.map((option) => {
        const on = option.value === value;
        return (
          <button
            key={option.value}
            type="button"
            role="tab"
            aria-selected={on}
            onClick={() => onChange(option.value)}
            className={cn(
              "h-7 rounded-[7px] px-2.5 text-[12px] font-medium leading-4 transition-colors duration-150",
              on ? "bg-d-surface text-d-ink shadow-[0_1px_2px_oklch(0_0_0/8%)]" : "text-d-ink-2 hover:text-d-ink",
            )}
          >
            {option.label}
          </button>
        );
      })}
    </div>
  );
}

type ButtonTone = "solid" | "fill" | "quiet";

export function Button({
  tone = "fill",
  className,
  children,
  ...rest
}: ButtonHTMLAttributes<HTMLButtonElement> & { tone?: ButtonTone }) {
  return (
    <button
      type="button"
      {...rest}
      className={cn(
        "biz-press inline-flex h-8 items-center justify-center gap-1.5 rounded-lg px-3 text-[13px] font-medium leading-4 disabled:opacity-40",
        tone === "solid" && "bg-d-ink text-d-canvas hover:opacity-90",
        tone === "fill" && "bg-d-fill text-d-ink hover:bg-d-hairline",
        tone === "quiet" && "text-d-accent-ink hover:underline",
        className,
      )}
    >
      {children}
    </button>
  );
}

/** Sortable column header for the revenue table. */
export function SortHeader({
  label,
  active,
  descending,
  onClick,
  align = "left",
}: {
  label: ReactNode;
  active: boolean;
  descending: boolean;
  onClick: () => void;
  align?: "left" | "right";
}) {
  const Icon = descending ? ChevronDown : ChevronUp;
  return (
    <th scope="col" className={cn("sticky top-0 z-10 bg-d-surface px-2 py-2 text-[12px] font-medium text-d-ink-2", align === "right" ? "text-right" : "text-left")}>
      <button
        type="button"
        onClick={onClick}
        className={cn(
          "inline-flex items-center gap-1 rounded px-1 py-0.5 transition-colors hover:text-d-ink",
          active && "text-d-ink",
          align === "right" && "flex-row-reverse",
        )}
        aria-sort={active ? (descending ? "descending" : "ascending") : "none"}
      >
        {label}
        <Icon className={cn("size-3 transition-opacity", active ? "opacity-100" : "opacity-0")} strokeWidth={2.25} aria-hidden />
      </button>
    </th>
  );
}

export function Chip({ children, tone = "neutral" }: { children: ReactNode; tone?: "neutral" | "accent" | "warn" }) {
  return (
    <span
      className={cn(
        "inline-flex h-5 items-center rounded px-1.5 text-[11px] font-medium leading-3",
        tone === "neutral" && "bg-d-fill text-d-ink-2",
        tone === "accent" && "bg-d-accent/10 text-d-accent-ink",
        tone === "warn" && "bg-d-warning/10 text-d-warning",
      )}
    >
      {children}
    </span>
  );
}

export function EmptyState({ title, hint, action }: { title: string; hint?: string; action?: ReactNode }) {
  return (
    <div className="flex flex-col items-center justify-center gap-2 py-12 text-center">
      <p className="text-[14px] font-medium text-d-ink">{title}</p>
      {hint && <p className="max-w-[320px] text-[12px] leading-5 text-d-ink-2">{hint}</p>}
      {action && <div className="pt-1">{action}</div>}
    </div>
  );
}

/** A horizontal share bar. Suppressed cells keep the track but show no value (samples under 20). */
export function ShareBar({
  label,
  emoji,
  share,
  note,
  suppressed,
  suppressedLabel,
}: {
  label: string;
  emoji?: string;
  share: number | null;
  note?: string;
  suppressed: boolean;
  suppressedLabel: string;
}) {
  return (
    <div className="flex items-center gap-3 py-1.5" title={suppressed ? suppressedLabel : undefined}>
      <span className={cn("w-24 shrink-0 truncate text-[12px] leading-4", suppressed ? "text-d-ink-3" : "text-d-ink")}>
        {emoji ? `${emoji} ` : ""}
        {label}
      </span>
      <span className="relative h-1.5 flex-1 overflow-hidden rounded-full bg-d-fill">
        {!suppressed && <span className="absolute inset-y-0 left-0 rounded-full bg-d-accent" style={{ width: `${Math.max(2, Math.round((share ?? 0) * 100))}%` }} />}
      </span>
      <span className={cn("w-12 shrink-0 text-right text-[12px] tabular-nums", suppressed ? "text-d-ink-3" : "text-d-ink-2")}>
        {suppressed ? "—" : (note ?? `${Math.round((share ?? 0) * 100)}%`)}
      </span>
    </div>
  );
}

/**
 * A small modal: Escape closes, the backdrop closes, the page behind stops scrolling.
 * Kept local until the shell lane's `components/desktop` owns the shared one.
 */
export function Modal({
  open,
  onClose,
  title,
  description,
  children,
  footer,
  width = 560,
}: {
  open: boolean;
  onClose: () => void;
  title: string;
  description?: string;
  children: ReactNode;
  footer?: ReactNode;
  width?: number;
}) {
  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") onClose();
    };
    const previous = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    document.addEventListener("keydown", onKey);
    return () => {
      document.body.style.overflow = previous;
      document.removeEventListener("keydown", onKey);
    };
  }, [open, onClose]);

  return createPortal(
    <AnimatePresence>
      {open && (
    <div className="fixed inset-0 z-50 flex items-start justify-center overflow-y-auto p-4 sm:items-center sm:p-6">
      <motion.div
        className="fixed inset-0 bg-black/25"
        onClick={onClose}
        aria-hidden
        initial={{ opacity: 0 }}
        animate={{ opacity: 1 }}
        exit={{ opacity: 0 }}
        transition={{ duration: 0.14, ease: EASE_OUT }}
      />
      <motion.div
        role="dialog"
        aria-modal="true"
        aria-label={title}
        initial={{ opacity: 0, scale: 0.96 }}
        animate={{ opacity: 1, scale: 1 }}
        exit={{ opacity: 0, scale: 0.98 }}
        transition={{ duration: 0.18, ease: EASE_OUT }}
        className="relative my-auto w-full rounded-[14px] bg-d-surface shadow-[0_24px_60px_-24px_oklch(0_0_0/35%)]"
        style={{ maxWidth: width }}
      >
        <header className="flex items-start justify-between gap-4 px-5 pb-1 pt-5">
          <div>
            <h2 className="text-[15px] font-semibold leading-5 text-d-ink">{title}</h2>
            {description && <p className="mt-1 text-[12px] leading-4 text-d-ink-2">{description}</p>}
          </div>
          <button
            type="button"
            onClick={onClose}
            aria-label={title}
            className="-mr-1 -mt-1 grid size-7 place-items-center rounded-md text-d-ink-2 transition-colors hover:bg-d-fill hover:text-d-ink"
          >
            <X className="size-4" strokeWidth={1.75} aria-hidden />
          </button>
        </header>
        <div className="px-5 py-4">{children}</div>
        {footer && <footer className="flex items-center justify-end gap-2 border-t border-d-hairline px-5 py-3">{footer}</footer>}
      </motion.div>
    </div>
      )}
    </AnimatePresence>,
    document.body,
  );
}
