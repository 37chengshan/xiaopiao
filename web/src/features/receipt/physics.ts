/** Small pure helpers for the tear gesture (apple-design §5, §6, §9). */

/** Where a flick would come to rest: exponential-decay projection, px/s in, px out. */
export function project(velocity: number, decelerationRate = 0.99): number {
  return ((velocity / 1000) * decelerationRate) / (1 - decelerationRate);
}

/** Progressive resistance past a boundary. */
export function rubberband(overshoot: number, dimension: number, constant = 0.55): number {
  const sign = overshoot < 0 ? -1 : 1;
  const o = Math.abs(overshoot);
  return (sign * (o * dimension * constant)) / (dimension + constant * o);
}

export const TEAR_DISTANCE = 110;
export const TEAR_PROJECTED = 240;
const MIN_PULL = 24;

/** Tear when pulled far enough, or flicked hard enough; never against an upward fling. */
export function shouldTear(y: number, velocityY: number): boolean {
  if (velocityY < -250) return false;
  if (y < MIN_PULL) return false;
  return y >= TEAR_DISTANCE || y + project(velocityY) >= TEAR_PROJECTED;
}

/** Velocity (px/s) from the last ~80ms of pointer samples. */
export function releaseVelocity(samples: readonly { t: number; y: number }[], windowMs = 80): number {
  if (samples.length < 2) return 0;
  const last = samples[samples.length - 1];
  let first = last;
  for (let i = samples.length - 2; i >= 0; i--) {
    if (last.t - samples[i].t > windowMs) break;
    first = samples[i];
  }
  const dt = last.t - first.t;
  return dt > 0 ? ((last.y - first.y) / dt) * 1000 : 0;
}

export type Phase = "idle" | "printing" | "printed" | "tearing" | "torn";
export type PhaseEvent = "start" | "done" | "skip" | "tear" | "torn" | "close";

/** printing → printed (skip jumps there at once) → tearing → torn (share sheet). */
export function nextPhase(phase: Phase, event: PhaseEvent): Phase {
  switch (event) {
    case "start":
      return phase === "idle" ? "printing" : phase;
    case "done":
      return phase === "printing" ? "printed" : phase;
    case "skip":
      return phase === "idle" || phase === "printing" ? "printed" : phase;
    case "tear":
      return phase === "printed" ? "tearing" : phase;
    case "torn":
      return phase === "tearing" ? "torn" : phase;
    case "close":
      return "idle";
  }
}
