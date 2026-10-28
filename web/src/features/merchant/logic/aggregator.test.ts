import { describe, expect, it } from "vitest";
import { K_ANON, aggregate, type ShareCell } from "./aggregator";
import { generateDemoCrowd } from "./crowd";

const crowd = generateDemoCrowd();
const insights = aggregate(crowd);

function noLeak(cells: ShareCell[]) {
  for (const c of cells) {
    if (c.suppressed) {
      expect(c.people).toBeNull();
      expect(c.share).toBeNull();
    } else {
      expect(c.people).toBeGreaterThanOrEqual(K_ANON);
    }
  }
}

describe("Aggregator", () => {
  it("builds a 7x6 heatmap", () => {
    expect(insights.heatmap).toHaveLength(7);
    for (const row of insights.heatmap) expect(row).toHaveLength(6);
  });
  it("suppresses every heatmap cell below k and hides its numbers", () => {
    for (const c of insights.heatmap.flat()) {
      if (c.suppressed) {
        expect(c.people).toBeNull();
        expect(c.level).toBeNull();
      } else {
        expect(c.people).toBeGreaterThanOrEqual(K_ANON);
        expect(c.level).toBeGreaterThan(0);
        expect(c.level).toBeLessThanOrEqual(1);
      }
    }
  });
  it("demo heatmap has both visible and suppressed cells", () => {
    const flat = insights.heatmap.flat();
    expect(flat.some((c) => c.suppressed)).toBe(true);
    expect(flat.some((c) => !c.suppressed)).toBe(true);
    expect(insights.suppressedCells).toBe(flat.filter((c) => c.suppressed).length);
  });
  it("never leaks small groups in category/price/topping cells", () => {
    noLeak(insights.categoryShare);
    noLeak(insights.priceBands);
    noLeak(insights.toppings);
  });
  it("suppresses everything when the crowd is smaller than k", () => {
    const tiny = aggregate(generateDemoCrowd(3, 10));
    expect(tiny.heatmap.flat().every((c) => c.suppressed)).toBe(true);
    expect(tiny.repeatRate).toBeNull();
    expect(tiny.peak).toBeNull();
    expect(tiny.toppings.every((c) => c.suppressed)).toBe(true);
  });
  it("respects a custom k", () => {
    const strict = aggregate(crowd, 10_000);
    expect(strict.heatmap.flat().every((c) => c.suppressed)).toBe(true);
  });
  it("visible shares are valid proportions", () => {
    const sum = insights.categoryShare.reduce((s, c) => s + (c.share ?? 0), 0);
    expect(sum).toBeGreaterThan(0.9);
    expect(sum).toBeLessThanOrEqual(1.0001);
    expect(insights.repeatRate).not.toBeNull();
    expect(insights.repeatRate!).toBeGreaterThan(0);
    expect(insights.repeatRate!).toBeLessThan(1);
  });
  it("exposes a peak slot that is a visible cell", () => {
    expect(insights.peak).not.toBeNull();
    const { weekday, slot } = insights.peak!;
    expect(insights.heatmap[weekday][slot].suppressed).toBe(false);
  });
  it("is deterministic", () => {
    expect(aggregate(generateDemoCrowd())).toEqual(insights);
  });
});
