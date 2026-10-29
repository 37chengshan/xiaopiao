/** Torn bottom edge as `clip-path: polygon(...)`. Percentages keep the paper responsive. */
export function tornBottomClip(teeth = 28, depthPx = 6): string {
  const points = ["0 0", "100% 0", `100% calc(100% - ${depthPx}px)`];
  for (let i = teeth; i >= 1; i -= 1) {
    const center = ((i - 0.5) / teeth) * 100;
    const left = ((i - 1) / teeth) * 100;
    points.push(`${center.toFixed(2)}% 100%`, `${left.toFixed(2)}% calc(100% - ${depthPx}px)`);
  }
  return `polygon(${points.join(", ")})`;
}
