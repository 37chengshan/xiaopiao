import type { ReactNode } from "react";
import { cn } from "@/lib/utils";

type Variant = "solid" | "grey";
type Size = "lg" | "sm";

interface Props {
  children: ReactNode;
  variant?: Variant;
  size?: Size;
  href?: string;
  onClick?: () => void;
  className?: string;
  cursorLabel?: string;
  ariaLabel?: string;
}

/** §0.2 — two button levels only: ink-filled and grey-filled, both pills. */
export function Pill({
  children,
  variant = "solid",
  size = "lg",
  href,
  onClick,
  className,
  cursorLabel,
  ariaLabel,
}: Props) {
  const classes = cn(
    "inline-flex items-center justify-center rounded-full font-medium whitespace-nowrap",
    "transition-[transform,opacity] duration-150 ease-[cubic-bezier(0.23,1,0.32,1)]",
    "active:scale-[0.98] select-none",
    size === "lg" ? "h-12 px-7 text-[15px]" : "h-9 px-4 text-[13px]",
    variant === "solid"
      ? "bg-d-ink text-d-canvas hover:opacity-90"
      : "bg-d-fill text-d-ink hover:bg-d-hairline",
    className,
  );
  const cursor = cursorLabel
    ? { "data-cursor": "cta", "data-cursor-label": cursorLabel }
    : undefined;

  if (href) {
    return (
      <a href={href} className={classes} aria-label={ariaLabel} {...cursor}>
        {children}
      </a>
    );
  }
  return (
    <button type="button" onClick={onClick} className={classes} aria-label={ariaLabel} {...cursor}>
      {children}
    </button>
  );
}
