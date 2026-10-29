import { ACCENT, InkFrame, PAPER } from "./InkFrame";

/** L5 art — shield, lock, and the phone that never leaves the owner. */
export function Shield({ className, label }: { className?: string; label?: string }) {
  return (
    <InkFrame className={className} label={label}>
      <path d="M200 34l108 34v90c0 56-52 90-108 108-56-18-108-52-108-108V68Z" fill={PAPER} />
      <path
        d="M200 52l88 28v76c0 46-42 74-88 89-46-15-88-43-88-89V80Z"
        className="opacity-25"
        strokeDasharray="4 6"
      />
      {/* lock */}
      <g className="shield-lock">
        <rect x="166" y="126" width="68" height="54" rx="12" fill={PAPER} />
        <path d="M180 126v-12a20 20 0 0 1 40 0v12" />
        <circle cx="200" cy="146" r="4" fill={ACCENT} />
        <path d="M200 150v12" />
      </g>
      {/* phone, kept in the lower half of the shield */}
      <g className="shield-phone">
        <rect x="176" y="196" width="48" height="66" rx="10" fill={PAPER} />
        <path d="M191 204h18" className="opacity-40" />
        <rect
          x="184"
          y="214"
          width="32"
          height="18"
          rx="4"
          className="opacity-12"
          fill="currentColor"
        />
        <path d="M191 246h18" className="opacity-40" />
      </g>
      <path d="M84 268h26M84 280h16" className="opacity-12" />
      <path d="M296 268h22" className="opacity-12" />
    </InkFrame>
  );
}
