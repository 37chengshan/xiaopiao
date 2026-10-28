import { ACCENT, InkFrame, PAPER } from "./InkFrame";

/** L3 card 5 — three stacked cards, tail numbers only. */
export function Wallet({ className, label }: { className?: string; label?: string }) {
  return (
    <InkFrame className={className} label={label}>
      <g transform="rotate(-10 200 150)" className="wallet-card wallet-card-1">
        <rect
          x="76"
          y="106"
          width="248"
          height="132"
          rx="16"
          className="opacity-20"
          fill="currentColor"
        />
      </g>
      <g transform="rotate(-4 200 150)" className="wallet-card wallet-card-2">
        <rect x="88" y="96" width="232" height="132" rx="16" className="opacity-30" fill={PAPER} />
        <path d="M106 122h52" className="opacity-30" />
      </g>
      <g className="wallet-card wallet-card-3">
        <rect x="98" y="88" width="212" height="136" rx="16" fill={PAPER} />
        <rect x="98" y="88" width="212" height="30" rx="16" fill={ACCENT} opacity="0.12" />
        <rect
          x="118"
          y="112"
          width="30"
          height="22"
          rx="5"
          className="opacity-25"
          fill="currentColor"
        />
        <path d="M118 158h96" className="opacity-45" />
        <circle cx="122" cy="186" r="3" className="opacity-40" fill="currentColor" />
        <circle cx="136" cy="186" r="3" className="opacity-40" fill="currentColor" />
        <circle cx="150" cy="186" r="3" className="opacity-40" fill="currentColor" />
        <circle cx="164" cy="186" r="3" className="opacity-40" fill="currentColor" />
        <path d="M180 186h34" className="opacity-45" />
        <path d="M244 186h40" className="opacity-25" />
      </g>
      <path d="M56 262h20M56 274h32" className="opacity-12" />
      <path d="M318 258h26" className="opacity-12" />
    </InkFrame>
  );
}
