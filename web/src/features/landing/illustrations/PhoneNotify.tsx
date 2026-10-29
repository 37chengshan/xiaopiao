import { ACCENT, InkFrame, PAPER } from "./InkFrame";

/** L3 card 1 — a payment notification lands on the phone and folds into the ledger. */
export function PhoneNotify({ className, label }: { className?: string; label?: string }) {
  return (
    <InkFrame className={className} label={label}>
      {/* phone */}
      <rect x="28" y="38" width="136" height="226" rx="24" fill={PAPER} />
      <path d="M78 52h36" className="opacity-40" />
      <rect
        x="46"
        y="70"
        width="100"
        height="34"
        rx="8"
        className="opacity-10"
        fill="currentColor"
      />
      <rect
        x="46"
        y="114"
        width="100"
        height="60"
        rx="10"
        className="opacity-14"
        fill="currentColor"
      />
      <path
        d="M60 172v-14M78 172v-26M96 172v-20M114 172v-32"
        strokeWidth="4"
        className="opacity-30"
      />
      {/* notification */}
      <g className="notification-bubble">
        <rect x="140" y="84" width="200" height="58" rx="15" fill={PAPER} />
        <circle cx="166" cy="113" r="11" className="opacity-20" fill="currentColor" />
        <path d="M188 103h84" className="opacity-45" />
        <path d="M188 117h54" className="opacity-25" />
        <path d="M188 131h36" className="opacity-15" />
      </g>
      {/* dashed hop into the ledger */}
      <path d="M196 150v28c0 22 18 32 40 32h44" strokeDasharray="5 6" className="opacity-40" />
      {/* ledger stack */}
      <rect x="272" y="196" width="106" height="20" rx="6" fill={PAPER} className="opacity-70" />
      <rect x="264" y="206" width="122" height="70" rx="10" fill={PAPER} />
      <path d="M280 226h58" className="opacity-40" />
      <path d="M280 242h76" className="opacity-25" />
      <path d="M280 258h44" className="opacity-25" />
      <circle cx="366" cy="226" r="4" fill={ACCENT} />
    </InkFrame>
  );
}
