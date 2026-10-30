import { describe, expect, it } from "vitest";
import { dayRemark, dayTitle } from "./dayTitle";

describe("dayTitle (same rules as the app's ReceiptLayout.dayTitle)", () => {
  it("only a day with nothing spent sleeps", () => {
    expect(dayTitle([])).toBe("sleeping");
    expect(dayTitle([{ kind: "meal", cents: 99_00 }, { kind: "drink", cents: 18_00 }])).not.toBe("sleeping");
  });

  it("checks the totals before the categories", () => {
    expect(dayTitle([{ kind: "shopping", cents: 520_00 }, { kind: "meal", cents: 20_00 }])).toBe("lavish");
    expect(dayTitle([{ kind: "drink", cents: 8_00 }, { kind: "transport", cents: 4_00 }])).toBe("smallJoys");
    expect(dayTitle([{ kind: "fun", cents: 45_00 }])).toBe("oneAndDone");
    expect(dayTitle(Array.from({ length: 8 }, () => ({ kind: "snack" as const, cents: 5_00 })))).toBe("maniac");
  });

  it("names the day after the category that took the most", () => {
    expect(
      dayTitle([
        { kind: "transport", cents: 4_00 },
        { kind: "meal", cents: 28_00 },
        { kind: "drink", cents: 18_00 },
      ]),
    ).toBe("meal");
    expect(
      dayTitle([
        { kind: "transport", cents: 32_00 },
        { kind: "meal", cents: 8_00 },
      ]),
    ).toBe("transport");
  });

  it("remarks without advising", () => {
    expect(dayRemark(0)).toBe("remark.zero");
    expect(dayRemark(12_00)).toBe("remark.small");
    expect(dayRemark(136_00)).toBe("remark.normal");
    expect(dayRemark(600_00)).toBe("remark.big");
  });
});
