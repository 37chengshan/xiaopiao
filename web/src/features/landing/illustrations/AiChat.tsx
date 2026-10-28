import { ACCENT, InkFrame, PAPER } from "./InkFrame";

/** L3 card 3 — a question, an answer, and the bars behind it. */
export function AiChat({ className, label }: { className?: string; label?: string }) {
  return (
    <InkFrame className={className} label={label}>
      <g className="chat-question">
        <rect x="28" y="42" width="188" height="58" rx="18" fill={PAPER} />
        <path d="M48 64h132" className="opacity-40" />
        <path d="M48 82h84" className="opacity-22" />
      </g>
      <g className="chat-answer">
        <rect
          x="126"
          y="118"
          width="246"
          height="66"
          rx="18"
          className="opacity-7"
          fill="currentColor"
        />
        <path d="M150 142h150" className="opacity-40" />
        <path d="M150 160h198" className="opacity-22" />
      </g>
      {/* bars */}
      <g className="chat-bars">
        <rect
          x="44"
          y="228"
          width="22"
          height="30"
          rx="5"
          className="opacity-18"
          fill="currentColor"
        />
        <rect x="78" y="210" width="22" height="48" rx="5" fill={ACCENT} opacity="0.55" />
        <rect
          x="112"
          y="220"
          width="22"
          height="38"
          rx="5"
          className="opacity-18"
          fill="currentColor"
        />
        <rect
          x="146"
          y="196"
          width="22"
          height="62"
          rx="5"
          className="opacity-30"
          fill="currentColor"
        />
        <path d="M36 258h150" className="opacity-25" />
      </g>
      <path d="M232 220h132M232 236h96M232 252h118" className="opacity-12" />
    </InkFrame>
  );
}
