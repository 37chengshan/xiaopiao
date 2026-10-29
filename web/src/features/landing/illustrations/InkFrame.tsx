import type { ReactNode } from "react";

/**
 * L.3 — one drawing style for all seven illustrations: 1.5px ink line work (never scaled thinner),
 * cream / accent flat fills, round caps, no realistic shadow.
 */
interface FrameProps {
  children: ReactNode;
  viewBox?: string;
  className?: string;
  label?: string;
}

export function InkFrame({ children, viewBox = "0 0 400 300", className, label }: FrameProps) {
  return (
    <svg
      viewBox={viewBox}
      className={className}
      role={label ? "img" : undefined}
      aria-label={label}
      aria-hidden={label ? undefined : true}
      fill="none"
      stroke="currentColor"
      strokeWidth={1.5}
      strokeLinecap="round"
      strokeLinejoin="round"
    >
      <g vectorEffect="non-scaling-stroke">{children}</g>
    </svg>
  );
}

/** Cream block: the only "solid" fill the drawings use besides accent. */
export const PAPER = "var(--d-paper)";
export const ACCENT = "var(--d-accent)";
export const STAMP = "var(--stamp)";
