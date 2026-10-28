import { type Cents } from "../domain/types";
import { createQmuseRow, deleteQmuseRow, getCurrentOwnerId, listQmuseRows, Query, updateQmuseRow, type Models } from "./appwrite";

/** One business day of 小店营收 (mirrors Android `merchant/Revenue.kt` RevenueEntry, minus seeded rows). */
export interface RevenueEntry {
  id: string;
  /** epoch millis of the day start */
  dayStart: number;
  revenueCents: Cents;
  orders: number;
  note: string;
  source: "MANUAL" | "CSV";
}

interface RevenueRow extends Models.Row {
  ownerId: string;
  day: string;
  revenueCents: number;
  orders?: number | null;
  source: RevenueEntry["source"];
  note?: string | null;
}

const TABLE = "merchant_revenue";

function toEntry(row: RevenueRow): RevenueEntry {
  return {
    id: row.$id,
    dayStart: Date.parse(row.day),
    revenueCents: row.revenueCents,
    orders: row.orders ?? 0,
    note: row.note ?? "",
    source: row.source,
  };
}

function toData(e: Omit<RevenueEntry, "id">) {
  if (!Number.isInteger(e.revenueCents) || e.revenueCents < 0) throw new Error("revenueCents must be a non-negative integer");
  return {
    day: new Date(e.dayStart).toISOString(),
    revenueCents: e.revenueCents,
    orders: Math.max(0, Math.round(e.orders)),
    source: e.source,
    note: e.note.trim().slice(0, 200) || null,
  };
}

/** One page (≤20) of the current user's revenue days, newest first; pass the last row id as `cursor` for more. */
export async function listRevenue(cursor?: string): Promise<{ entries: RevenueEntry[]; total: number; nextCursor?: string }> {
  const ownerId = await getCurrentOwnerId();
  const page = await listQmuseRows<RevenueRow>({
    tableId: TABLE,
    queries: [Query.equal("ownerId", ownerId)],
    limit: 20,
    cursor,
  });
  const last = page.rows[page.rows.length - 1];
  return { entries: page.rows.map(toEntry), total: page.total, nextCursor: page.rows.length === 20 ? last?.$id : undefined };
}

export async function addRevenue(entry: Omit<RevenueEntry, "id">): Promise<RevenueEntry> {
  const ownerId = await getCurrentOwnerId();
  return toEntry(await createQmuseRow<RevenueRow>({ tableId: TABLE, data: { ownerId, ...toData(entry) } }));
}

export async function updateRevenue(entry: RevenueEntry): Promise<RevenueEntry> {
  return toEntry(await updateQmuseRow<RevenueRow>({ tableId: TABLE, rowId: entry.id, data: toData(entry) }));
}

export async function removeRevenue(id: string): Promise<void> {
  await deleteQmuseRow({ tableId: TABLE, rowId: id });
}
