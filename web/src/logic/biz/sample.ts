/**
 * Small-sample gate for anything customer-facing on the shop side.
 * Product rule (docs/WEB-SPEC.md §B, docs/APP-DESIGN.md §9): a group under 20 people is
 * never shown as a number. The cell is drawn, greyed, and says why on hover — it is not
 * merely rounded, and it is never inferred from the rest of the table.
 */

export const K_ANON = 20;

export interface Gated<T> {
  /** false → the UI must render the placeholder, not the value */
  visible: boolean;
  /** null when hidden, so a caller cannot accidentally print it */
  value: T | null;
  /** people behind the cell, always safe to show as "样本不足" context */
  sample: number;
  threshold: number;
}

/** Passes `value` through only when at least `threshold` people back it. */
export function gate<T>(sample: number, value: T, threshold: number = K_ANON): Gated<T> {
  const visible = Number.isFinite(sample) && sample >= threshold;
  return { visible, value: visible ? value : null, sample, threshold };
}

