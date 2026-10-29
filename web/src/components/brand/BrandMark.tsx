/**
 * 今日小票 brand mark: a solid receipt slip with a torn zigzag foot, two printed lines knocked out
 * of the paper, and a warm-orange dot on the top-right corner — the stamp that is also the
 * "it has something to say" badge. Body uses currentColor; the knock-out lines take `--mark-paper`
 * (defaults to the page canvas) so the mark works on light and dark grounds.
 */
export const BRAND_ORANGE = "#FF8A00";

export const BRAND_BODY_PATH =
  "M8 3h16a2 2 0 0 1 2 2v20l-2.5-2.5-2.5 2.5-2.5-2.5-2.5 2.5-2.5-2.5-2.5 2.5-2.5-2.5-2.5 2.5V5a2 2 0 0 1 2-2Z";

export function BrandMark({ className, title }: { className?: string; title?: string }) {
  return (
    <svg
      viewBox="0 0 32 32"
      className={className}
      role={title ? "img" : undefined}
      aria-label={title}
      aria-hidden={title ? undefined : true}
    >
      <path d={BRAND_BODY_PATH} fill="currentColor" />
      <rect x="10" y="10.5" width="10" height="2.4" rx="1.2" fill="var(--mark-paper, var(--d-canvas, #fff))" />
      <rect x="10" y="15.5" width="6" height="2.4" rx="1.2" fill="var(--mark-paper, var(--d-canvas, #fff))" />
      <circle cx="24.3" cy="6.7" r="4.4" fill={BRAND_ORANGE} stroke="var(--mark-paper, var(--d-canvas, #fff))" strokeWidth="2" />
    </svg>
  );
}
