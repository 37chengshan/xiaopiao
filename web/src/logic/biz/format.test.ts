import { describe, expect, it } from "vitest";
import {
  addDays,
  centsToInput,
  changeText,
  money,
  moneyCompact,
  moneyPlain,
  monthKey,
  parseDayText,
  parseMoneyText,
  shortDate,
  weekdayIndex,
  weekStart,
} from "./format";

const WED = new Date(2026, 9, 28).getTime(); // 2026-10-28, a Wednesday

describe("money", () => {
  it("groups thousands and drops empty decimals", () => {
    expect(money(0)).toBe("¥0");
    expect(money(128_050)).toBe("¥1,280.5");
    expect(money(128_000)).toBe("¥1,280");
    expect(money(128_005)).toBe("¥1,280.05");
    expect(money(2_650_000)).toBe("¥26,500");
    expect(money(-128_050)).toBe("-¥1,280.5");
  });

  it("reads money the way a spreadsheet writes it", () => {
    expect(parseMoneyText("1280.5")).toBe(128_050);
    expect(parseMoneyText("¥1,280.50")).toBe(128_050);
    expect(parseMoneyText('"2,650"')).toBe(265_000);
    expect(parseMoneyText("0")).toBe(0);
    expect(parseMoneyText("-12.34")).toBe(-1234);
    expect(parseMoneyText("12.345")).toBe(1235);
    expect(parseMoneyText("abc")).toBeNull();
    expect(parseMoneyText("")).toBeNull();
  });

  it("round-trips an editable value", () => {
    expect(centsToInput(128_050)).toBe("1280.50");
    expect(centsToInput(128_000)).toBe("1280");
    expect(parseMoneyText("1.2k")).toBeNull();
  });

  it("labels axes without losing the tooltip", () => {
    expect(moneyCompact(128_050)).toBe("1.3k");
    expect(moneyCompact(26_500_000)).toBe("26.5万");
    expect(moneyCompact(4_200)).toBe("42");
    expect(moneyPlain(1_234_567)).toBe("12,345.67");
  });

  it("writes a signed percentage, or nothing without a base", () => {
    expect(changeText(0.123)).toBe("+12%");
    expect(changeText(-0.04)).toBe("-4%");
    expect(changeText(0)).toBe("0%");
    expect(changeText(null)).toBe("");
  });
});

describe("days", () => {
  it("reads full, short and dotted dates", () => {
    expect(parseDayText("2026-10-27", WED)).toBe(new Date(2026, 9, 27).getTime());
    expect(parseDayText("2026/1/5", WED)).toBe(new Date(2026, 0, 5).getTime());
    expect(parseDayText("2026.10.1", WED)).toBe(new Date(2026, 9, 1).getTime());
    expect(parseDayText("10/26", WED)).toBe(new Date(2026, 9, 26).getTime());
    expect(parseDayText(" 2026-10-27 ", WED)).toBe(new Date(2026, 9, 27).getTime());
  });

  it("refuses dates it cannot trust", () => {
    expect(parseDayText("2026-02-31", WED)).toBeNull();
    expect(parseDayText("2026-13-01", WED)).toBeNull();
    expect(parseDayText("去年", WED)).toBeNull();
    expect(parseDayText("", WED)).toBeNull();
  });

  it("counts Monday as 0 and finds the week start", () => {
    expect(weekdayIndex(WED)).toBe(2);
    expect(shortDate(weekStart(WED))).toBe("10/26");
    expect(monthKey(WED)).toBe("2026-10");
    expect(new Date(addDays(WED, 5)).getDate()).toBe(2);
  });
});
