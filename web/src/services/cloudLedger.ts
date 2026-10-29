import { type Category, type Direction, type Source, type Txn, DAY_MS } from "../domain/types";
import { createQmuseRow, deleteQmuseRow, getCurrentOwnerId, listQmuseRows, Query, updateQmuseRow, type Models } from "./appwrite";
import { type AddResult, type LedgerService } from "./LedgerService";

const TABLE = "transactions";
/** Analytics (receipt, weekly report, subscriptions) need the whole recent ledger, so list() reads this window page by page. */
const WINDOW_DAYS = 400;

interface TxnRow extends Models.Row {
  ownerId: string;
  amountCents: number;
  direction: Direction;
  merchant: string;
  category: Category;
  occurredAt: string;
  source: Exclude<Source, "DEMO">;
  note?: string | null;
  dedupeKey?: string | null;
}

function toTxn(row: TxnRow): Txn {
  return {
    id: row.$id,
    amountCents: row.amountCents,
    direction: row.direction,
    merchant: row.merchant,
    category: row.category,
    occurredAt: Date.parse(row.occurredAt),
    source: row.source,
    note: row.note ?? undefined,
    dedupeKey: row.dedupeKey ?? undefined,
  };
}

function toData(t: Txn) {
  return {
    amountCents: t.amountCents,
    direction: t.direction,
    merchant: t.merchant.trim().slice(0, 128),
    category: t.category,
    occurredAt: new Date(t.occurredAt).toISOString(),
    source: t.source === "DEMO" ? "MANUAL" : t.source,
    note: t.note ?? null,
    dedupeKey: t.dedupeKey ?? null,
  };
}

async function listAll(): Promise<TxnRow[]> {
  const ownerId = await getCurrentOwnerId();
  const since = new Date(Date.now() - WINDOW_DAYS * DAY_MS).toISOString();
  const rows: TxnRow[] = [];
  let cursor: string | undefined;
  for (;;) {
    const page = await listQmuseRows<TxnRow>({
      tableId: TABLE,
      queries: [Query.equal("ownerId", ownerId), Query.greaterThanEqual("occurredAt", since)],
      limit: 20,
      cursor,
    });
    rows.push(...page.rows);
    if (page.rows.length < 20 || rows.length >= page.total) return rows;
    cursor = page.rows[page.rows.length - 1].$id;
  }
}

/** Runs `fn` over `items` with a small concurrency cap so bulk imports don't flood the API. */
async function eachLimited<T>(items: readonly T[], fn: (item: T) => Promise<unknown>, cap = 4) {
  let i = 0;
  await Promise.all(
    Array.from({ length: Math.min(cap, items.length) }, async () => {
      while (i < items.length) await fn(items[i++]);
    }),
  );
}

/** QMuse 云服务 adapter: the signed-in (or anonymous) user's own transactions. */
function createCloudOnlyLedger(): LedgerService {
  return {
    mode: "empty",
    async list() {
      return (await listAll()).map(toTxn).sort((a, b) => a.occurredAt - b.occurredAt);
    },
    async add(txns) {
      const ownerId = await getCurrentOwnerId();
      const seen = new Set((await listAll()).map((r) => r.dedupeKey ?? r.$id));
      const fresh: Txn[] = [];
      for (const t of txns) {
        const key = t.dedupeKey ?? t.id;
        if (seen.has(key)) continue;
        seen.add(key);
        fresh.push(t);
      }
      await eachLimited(fresh, (t) => createQmuseRow<TxnRow>({ tableId: TABLE, data: { ownerId, ...toData(t) } }));
      return { added: fresh.length, duplicates: txns.length - fresh.length };
    },
    async update(txn) {
      await updateQmuseRow<TxnRow>({ tableId: TABLE, rowId: txn.id, data: toData(txn) });
    },
    async remove(id) {
      await deleteQmuseRow({ tableId: TABLE, rowId: id });
    },
    async reset() {
      await eachLimited(await listAll(), (r) => deleteQmuseRow({ tableId: TABLE, rowId: r.$id }));
    },
  };
}

/**
 * Cloud ledger. Anonymous visitors also get `seed` (the in-memory starter ledger) merged in:
 * seed rows stay in the browser, everything the visitor records goes to QMuse 云服务.
 *
 * When the cloud is unreachable — offline, a preview without a bound app, a first run before import —
 * the starter ledger takes over completely instead of leaving every page in an error state: the toolbar
 * already tells this visitor 仅本机, so a local-only ledger is the truthful behaviour. Signed-in users
 * get no seed and keep their error states.
 */
export function createCloudLedger(seed: LedgerService | null): LedgerService {
  const cloud = createCloudOnlyLedger();
  if (!seed) return cloud;
  const isSeed = (t: Pick<Txn, "source">) => t.source === "DEMO";
  /** Flipped by the first failed cloud call; from then on this visitor's ledger is the browser's own. */
  let local = false;

  return {
    mode: "demo",
    async list() {
      if (!local) {
        try {
          const [a, b] = await Promise.all([seed.list(), cloud.list()]);
          return [...a, ...b].sort((x, y) => x.occurredAt - y.occurredAt);
        } catch {
          local = true;
        }
      }
      return [...(await seed.list())].sort((x, y) => x.occurredAt - y.occurredAt);
    },
    async add(txns) {
      if (local) return seed.add(txns);
      try {
        const [a, b]: AddResult[] = await Promise.all([
          seed.add(txns.filter(isSeed)),
          cloud.add(txns.filter((t) => !isSeed(t))),
        ]);
        return { added: a.added + b.added, duplicates: a.duplicates + b.duplicates };
      } catch {
        local = true;
        return seed.add(txns);
      }
    },
    async update(txn) {
      if (local) return seed.update(txn);
      await (isSeed(txn) ? seed : cloud).update(txn).catch(() => {
        local = true;
        return seed.update(txn);
      });
    },
    async remove(id) {
      if (local) return seed.remove(id);
      const seedIds = new Set((await seed.list()).map((t) => t.id));
      if (seedIds.has(id)) return seed.remove(id);
      // A cloud-only row that cannot be deleted here means the cloud just went away: stay local from now on.
      await cloud.remove(id).catch(() => {
        local = true;
      });
    },
    async reset() {
      if (local) return seed.reset();
      await Promise.all([seed.reset(), cloud.reset()]).catch(() => {
        local = true;
        return seed.reset();
      });
    },
  };
}
