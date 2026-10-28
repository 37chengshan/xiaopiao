/** Torn-paper edges shared by the on-screen paper (CSS clip-path) and the exported PNG (canvas path). */
export const PAPER_W = 304;
export const TOOTH = 10;
export const TOOTH_DEPTH = 5;

export function toothCount(width: number, tooth = TOOTH): number {
  return Math.max(2, Math.round(width / tooth));
}

/** Zigzag along the top edge: peaks up. Returns [x, y] from left to right. */
export function topEdge(width: number, depth = TOOTH_DEPTH, tooth = TOOTH): [number, number][] {
  const n = toothCount(width, tooth);
  const w = width / n;
  const pts: [number, number][] = [[0, depth]];
  for (let i = 0; i < n; i++) pts.push([w * (i + 0.5), 0], [w * (i + 1), depth]);
  return pts;
}

/** Zigzag along the bottom edge, right to left, with y measured up from the bottom (0 = outermost tip). */
export function bottomEdge(width: number, depth = TOOTH_DEPTH, tooth = TOOTH): [number, number][] {
  const n = toothCount(width, tooth);
  const w = width / n;
  const pts: [number, number][] = [[width, depth]];
  for (let i = 0; i < n; i++) pts.push([width - w * (i + 0.5), 0], [width - w * (i + 1), depth]);
  return pts;
}

const r = (v: number) => Math.round(v * 100) / 100;

/** `clip-path: polygon(...)` for a paper of the given width; heights stay fluid via calc(). */
export function paperClipPath(width = PAPER_W, opts: { top?: boolean; bottom?: boolean } = {}): string {
  const { top = true, bottom = true } = opts;
  const pts: string[] = [];
  if (top) for (const [x, y] of topEdge(width)) pts.push(`${r(x)}px ${r(y)}px`);
  else pts.push("0 0", `${width}px 0`);
  if (bottom) for (const [x, y] of bottomEdge(width)) pts.push(`${r(x)}px calc(100% - ${r(y)}px)`);
  else pts.push(`${width}px 100%`, "0 100%");
  return `polygon(${pts.join(", ")})`;
}

/** Bottom-only teeth hanging below a line (the moving leading edge while printing). */
export function leadingEdgeClipPath(width = PAPER_W): string {
  const pts = ["0 0", `${width}px 0`];
  for (const [x, y] of bottomEdge(width)) pts.push(`${r(x)}px ${r(TOOTH_DEPTH - y)}px`);
  return `polygon(${pts.join(", ")})`;
}

/** The paper still feeding out of the slot: its bottom teeth mesh with the top teeth of the torn-off sheet. */
export const STUB_OVERLAP = 8;
export function stubClipPath(width = PAPER_W): string {
  const n = toothCount(width);
  const w = width / n;
  const o = STUB_OVERLAP;
  const pts = ["0 0", `${width}px 0`, `${width}px ${r(o + TOOTH_DEPTH)}px`];
  for (let i = n; i > 0; i--) pts.push(`${r(w * (i - 0.5))}px ${o}px`, `${r(w * (i - 1))}px ${r(o + TOOTH_DEPTH)}px`);
  return `polygon(${pts.join(", ")})`;
}
