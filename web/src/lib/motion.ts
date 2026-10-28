/**
 * JS mirror of the motion tokens in `styles.css` (`--ease-*`, `--dur-*`). Keep both in sync.
 * Curves are framer-motion cubic-bezier tuples; durations are seconds.
 * Rules: press ≤160ms, UI ≤300ms, no ease-in, transform/opacity/clip-path only.
 */
export const EASE_OUT: [number, number, number, number] = [0.23, 1, 0.32, 1];
export const EASE_IN_OUT: [number, number, number, number] = [0.77, 0, 0.175, 1];
export const EASE_DRAWER: [number, number, number, number] = [0.32, 0.72, 0, 1];

export const DUR = {
  press: 0.12,
  fade: 0.12,
  tint: 0.16,
  row: 0.18,
  collapse: 0.16,
  sheet: 0.28,
  count: 0.52,
} as const;

/** Sliding selection (tab thumb, segmented control): critically damped with a hint of bounce. */
export const SPRING_THUMB = { type: "spring", bounce: 0.18, duration: 0.42 } as const;

/** Something landing (stamp): one controlled overshoot, then rest. */
export const SPRING_LAND = { type: "spring", bounce: 0.28, duration: 0.45 } as const;
