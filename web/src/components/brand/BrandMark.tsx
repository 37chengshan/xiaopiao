/**
 * 今日小票 / Daily Slip brand mark — the same drawing as the Android launcher icon (docs/BRAND.md): a coral tile
 * with a white receipt whose foot is torn in a zigzag, three printed lines in the tile's coral. One mark for the
 * App icon, the favicon, the nav and the in-page phones, so the brand reads the same everywhere.
 */
export const BRAND_CORAL = "#FF6B4A";

const RECEIPT_PATH = "M36 30h36v44l-4.5-3-4.5 3-4.5-3-4.5 3-4.5-3-4.5 3-4.5-3-4.5 3z";
const LINES_PATH = "M42 40h24v3H42zm0 8h18v3H42zm0 8h24v3H42z";

export function BrandMark({ className, title }: { className?: string; title?: string }) {
  return (
    <svg
      viewBox="18 18 72 72"
      className={className}
      role={title ? "img" : undefined}
      aria-label={title}
      aria-hidden={title ? undefined : true}
    >
      <rect x="18" y="18" width="72" height="72" rx="17" fill={BRAND_CORAL} />
      <path d={RECEIPT_PATH} fill="#fff" />
      <path d={LINES_PATH} fill={BRAND_CORAL} />
    </svg>
  );
}
