/** Spring presets (docs/DESIGN.md §5). Default UI is critically damped; bounce only for ritual / momentum. */
export const spring = { type: "spring", bounce: 0, duration: 0.35 } as const;
export const springSnap = { type: "spring", bounce: 0, duration: 0.3 } as const;
/** Small ritual moments: a selection check landing. */
export const springPop = { type: "spring", bounce: 0.25, duration: 0.4 } as const;
