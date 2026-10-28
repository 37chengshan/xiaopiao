import { ACCENT, InkFrame, PAPER, STAMP } from "./InkFrame";

const TEETH = 14;
/** Torn bottom edge of the receipt: 14 teeth, 12 units each. */
const TORN = `M116 36H284V222${"l-6 12l-6 -12".repeat(TEETH)}Z`;

/** L3 card 2 — a receipt with the daily stamp landing on it. */
export function ReceiptStamp({ className, label }: { className?: string; label?: string }) {
  return (
    <InkFrame className={className} label={label}>
      <path d={TORN} fill={PAPER} />
      <path d="M136 62h60" className="opacity-45" />
      <path d="M136 78h128" className="opacity-22" />
      <path d="M136 94h108" className="opacity-22" />
      <path d="M136 110h124" className="opacity-22" />
      <path d="M136 126h88" className="opacity-22" />
      <path d="M136 150h128" className="opacity-25" strokeDasharray="4 5" />
      <path d="M136 170h56" className="opacity-45" />
      <path d="M228 170h36" className="opacity-45" />
      <rect x="136" y="140" width="24" height="3" rx="1.5" fill={ACCENT} />
      {/* stamp */}
      <g transform="rotate(-8 258 196)" className="stamp">
        <circle cx="258" cy="196" r="42" stroke={STAMP} strokeWidth="2.5" />
        <circle cx="258" cy="196" r="33" stroke={STAMP} strokeDasharray="2 6" />
        <path
          d="M258 178l5 10.1 11.2 1.6-8.1 7.9 1.9 11.1-10-5.3-10 5.3 1.9-11.1-8.1-7.9 11.2-1.6z"
          fill={STAMP}
          opacity="0.9"
        />
        <path d="M234 216h10M272 216h10" stroke={STAMP} opacity="0.8" />
      </g>
      <path d="M64 236h18M64 252h34M64 268h26" className="opacity-12" />
      <path d="M302 226h34M302 242h20M302 258h28" className="opacity-12" />
    </InkFrame>
  );
}
