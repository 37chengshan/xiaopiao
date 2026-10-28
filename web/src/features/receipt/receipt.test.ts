import { describe, expect, it } from "vitest";
import { GRADIENTS, luminance } from "./gradients";
import { nextPhase, project, rubberband, shouldTear, TEAR_DISTANCE } from "./physics";
import { dueTicks, planPrint, printDuration } from "./printTimeline";
import { deltaLabel, displayWidth, fmtMoney } from "./receiptModel";

describe("tear physics", () => {
  it("tears past the distance, or on a hard flick, never on an upward fling", () => {
    expect(shouldTear(TEAR_DISTANCE, 0)).toBe(true);
    expect(shouldTear(60, 0)).toBe(false);
    expect(shouldTear(60, 3000)).toBe(true);
    expect(shouldTear(200, -400)).toBe(false);
    expect(shouldTear(10, 5000)).toBe(false);
  });
  it("projects and rubber-bands", () => {
    expect(project(1000)).toBeGreaterThan(90);
    expect(Math.abs(rubberband(-500, 140))).toBeLessThan(140);
  });
  it("walks the phases", () => {
    expect(nextPhase("idle", "start")).toBe("printing");
    expect(nextPhase("printing", "skip")).toBe("printed");
    expect(nextPhase("printed", "tear")).toBe("tearing");
    expect(nextPhase("printing", "tear")).toBe("printing");
    expect(nextPhase("tearing", "torn")).toBe("torn");
  });
});

describe("print timeline", () => {
  it("keeps the run between 1.8s and 2.8s and ticks once per row", () => {
    expect(printDuration(1)).toBe(1.8);
    expect(printDuration(100)).toBe(2.8);
    const plan = planPrint(600, [100, 200, 300], 250);
    expect(plan.tickTimes).toHaveLength(3);
    expect(plan.totalAt).toBeLessThanOrEqual(plan.duration);
    expect(dueTicks([100, 200, 300], 0, 250)).toBe(2);
  });
});

describe("receipt model", () => {
  it("formats money and deltas", () => {
    expect(fmtMoney(9999999)).toBe("99,999.99");
    expect(fmtMoney(1)).toBe("0.01");
    expect(deltaLabel(0, 0)).toBe("较昨日 持平");
    expect(deltaLabel(50, 100)).toBe("较昨日 −50%");
    expect(deltaLabel(1, null)).toBe("较昨日 —");
  });
  it("counts CJK as two columns", () => {
    expect(displayWidth("今日abc")).toBe(7);
  });
});

describe("gradients", () => {
  it("has 12 presets whose dark flag matches luminance", () => {
    expect(GRADIENTS).toHaveLength(12);
    for (const g of GRADIENTS) expect(luminance(g.stops[1]) < 0.2).toBe(g.dark);
  });
});

describe("worst-case data", () => {
  it("keeps 7-digit amounts whole and 0.01 visible", () => {
    expect(fmtMoney(123456789)).toBe("1,234,567.89");
    expect(fmtMoney(0)).toBe("0.00");
  });
});
