/**
 * Ledger sync: structured fields only, last-write-wins by `updatedAt`, tombstones instead of deletes,
 * and one monotonic `seq` per user so a client can pull "everything after this".
 *
 * No raw text ever reaches this module on purpose: validation rejects any unknown field and names the
 * receipt-text fields explicitly, because the product rule is that bills are parsed on the device only.
 */
export const LIMITS = {
  items: 500,
  page: 500,
  defaultPage: 200,
  id: 64,
  deviceId: 64,
  merchant: 64,
  category: 32,
  note: 200,
  label: 32,
  key: 32,
  hue: 24,
  source: 16,
  maxCents: 10_000_000_000,
  /** 2100-01-01T00:00:00Z: a sanity bound so garbage timestamps cannot pin a row forever. */
  maxTime: 4_102_444_800_000,
};

/** Receipt text never leaves the phone; these names are refused with a clear reason instead of "unknown". */
const RAW_TEXT_FIELDS = new Set([
  'rawtext', 'raw_text', 'raw', 'text', 'title', 'body', 'lines', 'items', 'receipt', 'ocr',
  'screenshot', 'screenshots', 'image', 'images', 'photo', 'photos', 'html', 'payload', 'attachment',
]);

/** snake_case aliases so a client written from the table in docs/API-SYNC.md still works. */
const ALIASES = {
  amount_cents: 'amountCents', occurred_at: 'occurredAt', card_last4: 'cardLast4', updated_at: 'updatedAt',
  icon_key: 'iconKey', sort_order: 'sortOrder', device_id: 'deviceId',
};

const TXN_FIELDS = ['id', 'amountCents', 'direction', 'merchant', 'category', 'occurredAt', 'source', 'note', 'cardLast4', 'updatedAt', 'deleted'];
const CATEGORY_FIELDS = ['id', 'label', 'iconKey', 'hue', 'direction', 'hidden', 'sortOrder', 'updatedAt', 'deleted'];

const isText = (value, max, { allowEmpty = true } = {}) =>
  typeof value === 'string' && value.length <= max && (allowEmpty || value.trim().length > 0);
const isTime = value => Number.isSafeInteger(value) && value > 0 && value <= LIMITS.maxTime;
const isDirection = value => value === 'OUT' || value === 'IN';
const ID = /^[A-Za-z0-9][A-Za-z0-9._:-]{0,63}$/;
const SOURCE = /^[A-Z][A-Z0-9_]{0,15}$/;
const CARD = /^[0-9]{4}$/;

/** Normalises aliases, refuses raw-text and unknown fields, and reports one reason per rejected item. */
function fields(raw, allowed) {
  const out = {};
  for (const [key, value] of Object.entries(raw)) {
    const lower = key.toLowerCase();
    if (RAW_TEXT_FIELDS.has(lower)) return { reason: 'RAW_TEXT_FORBIDDEN' };
    const name = ALIASES[key] ?? key;
    if (!allowed.includes(name)) return { reason: 'UNKNOWN_FIELD' };
    if (name in out && out[name] !== value) return { reason: 'ALIAS_CONFLICT' };
    out[name] = value;
  }
  return { value: out };
}

function validTxn(raw) {
  if (!raw || typeof raw !== 'object' || Array.isArray(raw)) return { reason: 'INVALID' };
  const parsed = fields(raw, TXN_FIELDS);
  if (parsed.reason) return parsed;
  const v = parsed.value;
  if (!isText(v.id, LIMITS.id, { allowEmpty: false }) || !ID.test(v.id)) return { reason: 'ID_INVALID' };
  if (!Number.isSafeInteger(v.amountCents) || v.amountCents <= 0 || v.amountCents > LIMITS.maxCents) return { reason: 'AMOUNT_INVALID' };
  if (!isDirection(v.direction)) return { reason: 'DIRECTION_INVALID' };
  if (!isText(v.merchant ?? '', LIMITS.merchant)) return { reason: 'MERCHANT_INVALID' };
  if (!isText(v.category ?? '', LIMITS.category)) return { reason: 'CATEGORY_INVALID' };
  if (!isTime(v.occurredAt)) return { reason: 'OCCURRED_AT_INVALID' };
  if (!isText(v.source, LIMITS.source, { allowEmpty: false }) || !SOURCE.test(v.source)) return { reason: 'SOURCE_INVALID' };
  if (!isText(v.note ?? '', LIMITS.note)) return { reason: 'NOTE_INVALID' };
  const card = v.cardLast4 ?? null;
  if (card !== null && (typeof card !== 'string' || !CARD.test(card))) return { reason: 'CARD_INVALID' };
  if (!isTime(v.updatedAt)) return { reason: 'UPDATED_AT_INVALID' };
  if (v.deleted !== undefined && typeof v.deleted !== 'boolean') return { reason: 'DELETED_INVALID' };
  return {
    value: {
      id: v.id,
      amountCents: v.amountCents,
      direction: v.direction,
      merchant: (v.merchant ?? '').trim(),
      category: (v.category ?? '').trim(),
      occurredAt: v.occurredAt,
      source: v.source,
      note: v.note ?? '',
      cardLast4: card,
      updatedAt: v.updatedAt,
      deleted: v.deleted === true,
    },
  };
}

function validCategory(raw) {
  if (!raw || typeof raw !== 'object' || Array.isArray(raw)) return { reason: 'INVALID' };
  const parsed = fields(raw, CATEGORY_FIELDS);
  if (parsed.reason) return parsed;
  const v = parsed.value;
  if (!isText(v.id, LIMITS.id, { allowEmpty: false }) || !ID.test(v.id)) return { reason: 'ID_INVALID' };
  if (!isText(v.label, LIMITS.label, { allowEmpty: false })) return { reason: 'LABEL_INVALID' };
  if (!isText(v.iconKey, LIMITS.key, { allowEmpty: false })) return { reason: 'ICON_INVALID' };
  if (!isText(v.hue, LIMITS.hue, { allowEmpty: false })) return { reason: 'HUE_INVALID' };
  if (v.direction !== null && v.direction !== undefined && !isDirection(v.direction)) return { reason: 'DIRECTION_INVALID' };
  if (v.hidden !== undefined && typeof v.hidden !== 'boolean') return { reason: 'HIDDEN_INVALID' };
  const sortOrder = v.sortOrder ?? 0;
  if (!Number.isSafeInteger(sortOrder) || sortOrder < 0 || sortOrder > 100_000) return { reason: 'SORT_INVALID' };
  if (!isTime(v.updatedAt)) return { reason: 'UPDATED_AT_INVALID' };
  if (v.deleted !== undefined && typeof v.deleted !== 'boolean') return { reason: 'DELETED_INVALID' };
  return {
    value: {
      id: v.id,
      label: v.label.trim(),
      iconKey: v.iconKey,
      hue: v.hue,
      direction: v.direction ?? null,
      hidden: v.hidden === true,
      sortOrder,
      updatedAt: v.updatedAt,
      deleted: v.deleted === true,
    },
  };
}

const txnJson = row => ({
  id: row.id, amountCents: row.amount_cents, direction: row.direction, merchant: row.merchant,
  category: row.category, occurredAt: row.occurred_at, source: row.source, note: row.note,
  cardLast4: row.card_last4 ?? null, updatedAt: row.updated_at, deleted: Boolean(row.deleted),
  deviceId: row.device_id, seq: row.seq,
});
const categoryJson = row => ({
  id: row.id, label: row.label, iconKey: row.icon_key, hue: row.hue, direction: row.direction ?? null,
  hidden: Boolean(row.hidden), sortOrder: row.sort_order, updatedAt: row.updated_at,
  deleted: Boolean(row.deleted), seq: row.seq,
});

export function createLedger({ db }) {
  const seqRow = db.prepare('SELECT value FROM ledger_seq WHERE user_id=?');
  const saveSeq = db.prepare('INSERT INTO ledger_seq(user_id,value) VALUES (?,?) ON CONFLICT(user_id) DO UPDATE SET value=excluded.value');
  const getTxn = db.prepare('SELECT updated_at, seq FROM ledger_txns WHERE user_id=? AND id=?');
  const putTxn = db.prepare(`INSERT INTO ledger_txns(user_id,id,amount_cents,direction,merchant,category,occurred_at,source,note,card_last4,updated_at,deleted,device_id,seq)
    VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)
    ON CONFLICT(user_id,id) DO UPDATE SET amount_cents=excluded.amount_cents,direction=excluded.direction,merchant=excluded.merchant,
      category=excluded.category,occurred_at=excluded.occurred_at,source=excluded.source,note=excluded.note,card_last4=excluded.card_last4,
      updated_at=excluded.updated_at,deleted=excluded.deleted,device_id=excluded.device_id,seq=excluded.seq`);
  const getCategory = db.prepare('SELECT updated_at, seq FROM ledger_categories WHERE user_id=? AND id=?');
  const putCategory = db.prepare(`INSERT INTO ledger_categories(user_id,id,label,icon_key,hue,direction,hidden,sort_order,updated_at,deleted,seq)
    VALUES (?,?,?,?,?,?,?,?,?,?,?)
    ON CONFLICT(user_id,id) DO UPDATE SET label=excluded.label,icon_key=excluded.icon_key,hue=excluded.hue,direction=excluded.direction,
      hidden=excluded.hidden,sort_order=excluded.sort_order,updated_at=excluded.updated_at,deleted=excluded.deleted,seq=excluded.seq`);

  /**
   * Applies one device's batch. Last write wins by `updatedAt`; an equal timestamp counts as newer
   * because "the one that arrived later wins". Older rows are reported as stale and never touch the store.
   */
  function push(userId, deviceId, txns, categories) {
    const stale = [];
    const rejected = [];
    const accepted = [];
    for (const raw of txns) {
      const parsed = validTxn(raw);
      if (parsed.reason) rejected.push({ id: typeof raw?.id === 'string' ? raw.id : null, kind: 'txn', reason: parsed.reason });
      else accepted.push({ kind: 'txn', value: parsed.value });
    }
    for (const raw of categories) {
      const parsed = validCategory(raw);
      if (parsed.reason) rejected.push({ id: typeof raw?.id === 'string' ? raw.id : null, kind: 'category', reason: parsed.reason });
      else accepted.push({ kind: 'category', value: parsed.value });
    }

    let applied = 0;
    db.exec('BEGIN IMMEDIATE');
    try {
      let seq = Number(seqRow.get(userId)?.value ?? 0);
      for (const item of accepted) {
        const v = item.value;
        const current = item.kind === 'txn' ? getTxn.get(userId, v.id) : getCategory.get(userId, v.id);
        if (current && v.updatedAt < current.updated_at) {
          stale.push({ id: v.id, kind: item.kind });
          continue;
        }
        seq += 1;
        if (item.kind === 'txn') {
          putTxn.run(userId, v.id, v.amountCents, v.direction, v.merchant, v.category, v.occurredAt, v.source,
            v.note, v.cardLast4, v.updatedAt, v.deleted ? 1 : 0, deviceId, seq);
        } else {
          putCategory.run(userId, v.id, v.label, v.iconKey, v.hue, v.direction, v.hidden ? 1 : 0, v.sortOrder, v.updatedAt,
            v.deleted ? 1 : 0, seq);
        }
        applied += 1;
      }
      saveSeq.run(userId, seq);
      db.exec('COMMIT');
      return { applied, stale, rejected, seq };
    } catch (error) {
      db.exec('ROLLBACK');
      throw error;
    }
  }

  /** Rows with `seq > since`, merged across both tables in seq order so `nextSince` is exact. */
  function changes(userId, since, limit) {
    const txns = db.prepare('SELECT * FROM ledger_txns WHERE user_id=? AND seq>? ORDER BY seq LIMIT ?').all(userId, since, limit + 1);
    const categories = db.prepare('SELECT * FROM ledger_categories WHERE user_id=? AND seq>? ORDER BY seq LIMIT ?').all(userId, since, limit + 1);
    const merged = [
      ...txns.map(row => ({ seq: row.seq, kind: 'txn', row })),
      ...categories.map(row => ({ seq: row.seq, kind: 'category', row })),
    ].sort((a, b) => a.seq - b.seq);
    const page = merged.slice(0, limit);
    return {
      txns: page.filter(item => item.kind === 'txn').map(item => txnJson(item.row)),
      categories: page.filter(item => item.kind === 'category').map(item => categoryJson(item.row)),
      nextSince: page.length ? page.at(-1).seq : since,
      hasMore: merged.length > limit,
    };
  }

  return { push, changes };
}

export { validTxn, validCategory, txnJson, categoryJson };
