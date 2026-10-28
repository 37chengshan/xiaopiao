/** Plans the print run from measured row positions. Pure so the pacing is testable. */
export interface PrintPlan {
  /** seconds the leading edge needs to travel the whole paper (the only linear motion) */
  duration: number;
  /** seconds, ascending: when each row's bottom edge leaves the slot (one printer tick each) */
  tickTimes: number[];
  /** seconds when the total row is uncovered and the ticker may start */
  totalAt: number;
  /** seconds when the stamp should land */
  stampAt: number;
}

export function printDuration(rows: number): number {
  return Math.min(2.8, Math.max(1.8, 1 + rows * 0.08));
}

export function planPrint(height: number, rowBottoms: readonly number[], totalBottom: number): PrintPlan {
  const duration = printDuration(rowBottoms.length);
  const speed = height / duration;
  const sorted = [...rowBottoms].sort((a, b) => a - b);
  const at = (px: number) => Math.min(duration, Math.max(0, px / speed));
  return {
    duration,
    tickTimes: sorted.map(at),
    totalAt: at(totalBottom),
    stampAt: duration + 0.18,
  };
}

/** Index of the next tick that is due once the edge has travelled `revealed` px. */
export function dueTicks(rowBottoms: readonly number[], from: number, revealed: number): number {
  let i = from;
  while (i < rowBottoms.length && revealed >= rowBottoms[i]) i++;
  return i;
}
