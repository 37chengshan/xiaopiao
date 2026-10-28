import { ACCENT, InkFrame, PAPER } from "./InkFrame";

/**
 * Hero art: a thermal printer. The print head / slot band sits along the bottom lip (y≈272) of the
 * 600×520 canvas, so `Hero` anchors the receipt at 26.7% / 58.1% / 46.7% of this box and it prints
 * downward out of the slot. The dark band is what makes the cream paper readable as paper coming out of a machine.
 */
export function Printer({ className, label }: { className?: string; label?: string }) {
  return (
    <InkFrame viewBox="0 0 600 520" className={className} label={label}>
      {/* body */}
      <rect x="110" y="64" width="380" height="250" rx="22" fill={PAPER} />
      {/* lid seam + paper roll behind it */}
      <path d="M130 136h340" className="opacity-20" />
      <rect
        x="142"
        y="82"
        width="180"
        height="34"
        rx="10"
        className="opacity-12"
        fill="currentColor"
      />
      <circle cx="452" cy="99" r="16" fill={PAPER} />
      <circle cx="452" cy="99" r="5" className="opacity-30" fill="currentColor" />
      {/* front panel: label plate, screen, status light */}
      <rect
        x="142"
        y="158"
        width="96"
        height="4"
        rx="2"
        className="opacity-25"
        fill="currentColor"
      />
      <rect
        x="142"
        y="174"
        width="62"
        height="4"
        rx="2"
        className="opacity-15"
        fill="currentColor"
      />
      <rect
        x="424"
        y="152"
        width="52"
        height="30"
        rx="7"
        className="opacity-85"
        fill="currentColor"
      />
      <circle cx="452" cy="167" r="4" fill={PAPER} />
      <circle cx="466" cy="199" r="5" fill={ACCENT} className="landing-led" />
      <path d="M142 198v20M154 198v20M166 198v20" className="opacity-20" />
      {/* print head band along the bottom lip, its slot opening, and the paper under it */}
      <rect
        x="146"
        y="272"
        width="308"
        height="30"
        rx="8"
        className="opacity-90"
        fill="currentColor"
      />
      <rect x="162" y="286" width="276" height="3.5" rx="1.75" fill={PAPER} />
    </InkFrame>
  );
}
