/**
 * Profile avatars: either a preset id (`p1`…`p12`) or one small photo (JPEG / PNG / WebP, ≤ 256 KiB).
 *
 * Two rules, the same ones circles follow:
 * - The image bytes reach only the user themself or someone who shares a circle with them. Everyone else gets
 *   404, never 403: "does this person have an avatar" is itself private.
 * - The mime is detected from the bytes, never taken from the client, so a text file renamed .png cannot be
 *   stored or later served as an image.
 *
 * Nothing here is bill data: it is the one field of a user that other circle members are meant to see.
 */
export const LIMITS = Object.freeze({
  presets: 12,
  bytes: 256 * 1024,
  /** Body limit for PUT /v1/auth/avatar: base64 of 256 KiB plus the JSON envelope. */
  body: 384_000,
  perHour: 30,
});

const PRESET = /^p(1[0-2]|[1-9])$/;
/** Standard base64 only: a `data:` URL wrapper or url-safe alphabet is a client bug, not a photo. */
const BASE64 = /^[A-Za-z0-9+/]+={0,2}$/;

const fail = code => { throw Object.assign(new Error(code), { code }); };

export function presetValid(value) { return typeof value === 'string' && PRESET.test(value); }

/** Magic bytes decide the type. Anything else — GIF, BMP, SVG, random bytes — is refused. */
export function sniff(bytes) {
  if (!bytes || bytes.length < 12) return null;
  if (bytes[0] === 0xff && bytes[1] === 0xd8 && bytes[2] === 0xff) return 'image/jpeg';
  if (bytes[0] === 0x89 && bytes[1] === 0x50 && bytes[2] === 0x4e && bytes[3] === 0x47 &&
      bytes[4] === 0x0d && bytes[5] === 0x0a && bytes[6] === 0x1a && bytes[7] === 0x0a) return 'image/png';
  if (bytes[0] === 0x52 && bytes[1] === 0x49 && bytes[2] === 0x46 && bytes[3] === 0x46 &&
      bytes[8] === 0x57 && bytes[9] === 0x45 && bytes[10] === 0x42 && bytes[11] === 0x50) return 'image/webp';
  return null;
}

/** One prepared read per database, shared by every module that needs to show a face. */
const readers = new WeakMap();
function avatarRow(db, userId) {
  let read = readers.get(db);
  if (!read) { read = db.prepare('SELECT preset, mime, updated_at FROM avatars WHERE user_id=?'); readers.set(db, read); }
  return read.get(userId);
}

/**
 * The public shape of a user's avatar. `url` carries `v=<updated_at>` so a replaced photo is a new URL and a
 * cached one can never outlive it.
 */
export function avatarJson(db, userId) {
  const row = avatarRow(db, userId);
  if (!row) return null;
  if (row.preset) return { preset: row.preset };
  return row.mime ? { url: `/v1/avatars/${userId}?v=${row.updated_at}` } : null;
}

export function createAvatars({ db, now = Date.now }) {
  const write = db.prepare(`INSERT INTO avatars(user_id,preset,mime,bytes,updated_at) VALUES (?,?,?,?,?)
    ON CONFLICT(user_id) DO UPDATE SET preset=excluded.preset, mime=excluded.mime, bytes=excluded.bytes,
      updated_at=excluded.updated_at`);
  const drop = db.prepare('DELETE FROM avatars WHERE user_id=?');
  const photoRow = db.prepare('SELECT mime, bytes FROM avatars WHERE user_id=?');

  /** Exactly one of `preset` / `image`; anything else is a malformed request, not a new avatar. */
  function set(userId, body) {
    const hasPreset = body.preset !== undefined;
    const hasImage = body.image !== undefined;
    if (hasPreset === hasImage) fail('AVATAR_INVALID');
    if (hasPreset) {
      if (!presetValid(body.preset)) fail('AVATAR_INVALID');
      // A preset replaces any stored photo: one avatar per account, never a stale image behind a new preset.
      write.run(userId, body.preset, null, null, now());
      return;
    }
    if (typeof body.image !== 'string' || !body.image || !BASE64.test(body.image)) fail('AVATAR_INVALID');
    const bytes = Buffer.from(body.image, 'base64');
    if (!bytes.length) fail('AVATAR_INVALID');
    if (bytes.length > LIMITS.bytes) fail('AVATAR_TOO_LARGE');
    const mime = sniff(bytes);
    if (!mime) fail('AVATAR_INVALID');
    write.run(userId, null, mime, bytes, now());
  }

  function clear(userId) { drop.run(userId); }

  function photo(userId) {
    const row = photoRow.get(userId);
    return row?.bytes ? { mime: row.mime, bytes: row.bytes } : null;
  }

  return { set, clear, photo, json: userId => avatarJson(db, userId) };
}
