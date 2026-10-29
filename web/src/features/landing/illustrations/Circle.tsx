import { ACCENT, InkFrame, PAPER } from "./InkFrame";

const RING = 96;
const MEMBERS = [-90, 30, 150].map((deg) => ({
  x: 200 + RING * Math.cos((deg * Math.PI) / 180),
  y: 150 + RING * Math.sin((deg * Math.PI) / 180),
}));

/** L3 card 4 — three avatars on a ring around one shared receipt. */
export function Circle({ className, label }: { className?: string; label?: string }) {
  return (
    <InkFrame className={className} label={label}>
      <circle cx="200" cy="150" r={RING} strokeDasharray="4 7" className="opacity-35" />
      {MEMBERS.map((m, index) => (
        <g key={index} className={`circle-member circle-member-${index + 1}`}>
          <circle cx={m.x} cy={m.y} r="28" fill={PAPER} />
          <circle cx={m.x} cy={m.y - 6} r="6" className="opacity-40" />
          <path d={`M${m.x - 12} ${m.y + 16}a12 12 0 0 1 24 0`} className="opacity-40" />
        </g>
      ))}
      {/* the receipt in the middle */}
      <g className="circle-receipt">
        <path d={`M172 108h56v66${"l-3.5 8l-3.5 -8".repeat(8)}Z`} fill={PAPER} />
        <path d="M184 126h32" className="opacity-40" />
        <path d="M184 140h22" className="opacity-22" />
        <path d="M184 154h28" className="opacity-22" />
        <circle cx="200" cy="166" r="3" fill={ACCENT} />
      </g>
    </InkFrame>
  );
}
