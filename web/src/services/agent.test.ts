import { describe, expect, it } from "vitest";
import type { Txn } from "@/domain/types";
import { executeLocalTool } from "./agent";

const now = new Date(2026, 10, 1, 12).getTime();
function row(id: string, cents: number, direction: "IN" | "OUT" = "OUT"): Txn {
  return { id, amountCents: cents, direction, merchant: "咖啡", category: "DRINK", occurredAt: now, source: "MANUAL", note: "PRIVATE RECEIPT ORIGINAL" };
}
describe("local model capabilities", () => {
  it("separates income and expense and keeps integer cents", () => {
    const result = executeLocalTool("summarize_period", { period: "today" }, [row("one", 1234), row("two", 500, "IN")], now);
    expect(result).toMatchObject({ total_out: 1234, total_in: 500, count: 2, unit: "cents" });
  });
  it("caps queries, declares truncation and strips private original text", () => {
    const result = executeLocalTool("query_transactions", { limit: 1000 }, Array.from({ length: 30 }, (_, i) => row(String(i), 100)), now);
    expect(result).toMatchObject({ matched_count: 30, truncated: true });
    expect("rows" in result && result.rows?.length).toBe(20);
    expect(JSON.stringify(result)).not.toContain("PRIVATE");
    expect(JSON.stringify(result)).not.toContain("note");
  });
  it("cannot mutate or infer unshared circle data", () => {
    expect(executeLocalTool("commit_transactions", {}, [], now)).toEqual({ error: "TOOL_NOT_ALLOWED" });
    expect(executeLocalTool("read_circle", {}, [], now)).toMatchObject({ shared: false });
  });
});
