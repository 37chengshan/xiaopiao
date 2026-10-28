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

import { layout } from "./receiptImage";
import { buildReceiptDoc, receiptPlainText } from "./receiptModel";
import type { DailyReceipt } from "../../domain/types";

const sample = {
  dayStart: Date.UTC(2026, 9, 27),
  totalOutCents: 8800,
  aiComment: "今天花得很从容，明天继续保持这个节奏就好，不用着急。",
  badge: { emoji: "🥐", title: "早餐守护者", reason: "早餐按时打卡" },
  items: [
    { id: "1", merchant: "美团外卖", amountCents: 3200, direction: "OUT", category: "MEAL", occurredAt: Date.UTC(2026, 9, 27, 4) },
    { id: "2", merchant: "公司食堂", amountCents: 2400, direction: "OUT", category: "MEAL", occurredAt: Date.UTC(2026, 9, 27, 5) },
    { id: "3", merchant: "地铁", amountCents: 300, direction: "OUT", category: "TRANSPORT", occurredAt: Date.UTC(2026, 9, 27, 6) },
  ],
} as unknown as DailyReceipt;

describe("share variants differ", () => {
  const ctx = { now: Date.UTC(2026, 9, 27, 13), prevTotalCents: 5000 };
  const full = buildReceiptDoc(sample, { ...ctx, variant: "full" });
  const brief = buildReceiptDoc(sample, { ...ctx, variant: "brief" });
  it("brief has no detail rows, full has", () => {
    expect(full.groups.length).toBeGreaterThan(0);
    expect(brief.groups).toHaveLength(0);
    expect(brief.remark).toBe(full.remark);
  });
  it("text and PNG layout differ, brief is shorter and keeps total, stamp, remark", () => {
    const tf = receiptPlainText(full);
    const tb = receiptPlainText(brief);
    expect(tb).not.toBe(tf);
    expect(tb.split("\n").length).toBeLessThan(tf.split("\n").length);
    expect(tf).toContain("美团外卖");
    expect(tb).not.toContain("美团外卖");
    for (const k of ["88.00", "早餐守护者", "今天花得很从容"]) expect(tb).toContain(k);
    const lf = layout(full);
    const lb = layout(brief);
    expect(lb.height).toBeLessThan(lf.height);
    expect(lb.ops.some((o) => o.k === "line" && o.left.includes("美团外卖"))).toBe(false);
    expect(lf.ops.some((o) => o.k === "line" && o.left.includes("美团外卖"))).toBe(true);
  });
});
