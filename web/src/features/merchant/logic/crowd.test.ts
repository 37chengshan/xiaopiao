import { describe, expect, it } from "vitest";
import { DEMO_CROWD_SIZE, generateDemoCrowd, priceBandOf } from "./crowd";

describe("DemoCrowd", () => {
  it("is deterministic for a seed", () => {
    expect(generateDemoCrowd(7, 50)).toEqual(generateDemoCrowd(7, 50));
  });
  it("differs across seeds", () => {
    expect(generateDemoCrowd(1, 50)).not.toEqual(generateDemoCrowd(2, 50));
  });
  it("makes a full demo crowd with valid fields", () => {
    const crowd = generateDemoCrowd();
    expect(crowd).toHaveLength(DEMO_CROWD_SIZE);
    for (const m of crowd) {
      expect(m.purchases.length).toBeGreaterThan(0);
      for (const p of m.purchases) {
        expect(p.weekday).toBeGreaterThanOrEqual(0);
        expect(p.weekday).toBeLessThan(7);
        expect(p.slot).toBeLessThan(6);
        expect(Number.isInteger(p.amountCents)).toBe(true);
      }
    }
  });
  it("has believable drink-buyer share and repeat rate", () => {
    const crowd = generateDemoCrowd();
    const counts = crowd.map((m) => m.purchases.filter((p) => p.category === "DRINK").length).filter((n) => n > 0);
    const share = counts.length / crowd.length;
    const repeat = counts.filter((n) => n >= 3).length / counts.length;
    expect(share).toBeGreaterThanOrEqual(0.55);
    expect(share).toBeLessThanOrEqual(0.75);
    expect(repeat).toBeGreaterThanOrEqual(0.35);
    expect(repeat).toBeLessThanOrEqual(0.55);
  });
  it("maps cents to price bands", () => {
    expect(priceBandOf(900)).toBe("lt10");
    expect(priceBandOf(1000)).toBe("10to15");
    expect(priceBandOf(1999)).toBe("15to20");
    expect(priceBandOf(2000)).toBe("gt20");
  });
});
