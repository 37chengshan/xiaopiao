import { createQmuseRow, getCurrentOwnerId, listQmuseRows, Query, updateQmuseRow, type Models } from "./appwrite";

export interface ReceiptPrefs {
  soundOn: boolean;
  hapticsOn: boolean;
}

interface ReceiptPrefsRow extends Models.Row, Partial<ReceiptPrefs> {
  ownerId: string;
}

const TABLE = "receipt_prefs";
/** The user's single prefs row, looked up once; shared so concurrent saves don't create duplicates. */
let rowLookup: Promise<ReceiptPrefsRow | null> | null = null;

function findRow(): Promise<ReceiptPrefsRow | null> {
  rowLookup ??= (async () => {
    const ownerId = await getCurrentOwnerId();
    const page = await listQmuseRows<ReceiptPrefsRow>({ tableId: TABLE, queries: [Query.equal("ownerId", ownerId)], limit: 1 });
    return page.rows[0] ?? null;
  })();
  rowLookup.catch(() => (rowLookup = null));
  return rowLookup;
}

/** Cloud copy of the receipt preferences, or null if this user never changed them. */
export async function loadReceiptPrefs(): Promise<ReceiptPrefs | null> {
  const row = await findRow();
  return row ? { soundOn: row.soundOn ?? true, hapticsOn: row.hapticsOn ?? true } : null;
}

/** Saves run one after another so the first toggle creates the row and later ones update it. */
let saving: Promise<void> = Promise.resolve();

export function saveReceiptPrefs(patch: Partial<ReceiptPrefs>): Promise<void> {
  const run = saving
    .catch(() => undefined)
    .then(async () => {
      const row = await findRow();
      const saved = row
        ? await updateQmuseRow<ReceiptPrefsRow>({ tableId: TABLE, rowId: row.$id, data: patch })
        : await createQmuseRow<ReceiptPrefsRow>({ tableId: TABLE, data: { ownerId: await getCurrentOwnerId(), ...patch } });
      rowLookup = Promise.resolve(saved);
    });
  saving = run;
  return run;
}
