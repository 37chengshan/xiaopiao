import { test } from 'node:test';
import assert from 'node:assert/strict';
import { scryptSync } from 'node:crypto';
import { secret } from '../src/store.mjs';
import { KDF, PASSWORD, createPasswords, checkStrength } from '../src/password.mjs';

/** Real cost is 128 MiB and ~0.3s; the shape of the rules is what these tests are about. */
const fast = createPasswords({ kdf: { ...KDF, logN: 12 } });
const PHRASE = 'A long test phrase 123';

const legacyHash = (password, salt = secret(), logN = 17) =>
  `${salt}:${scryptSync(password, salt, 64, { N: 2 ** logN, r: 8, p: 1, maxmem: 256 * 1024 * 1024 }).toString('hex')}`;

test('a stored hash carries its own cost and verifies only its own password', async () => {
  const stored = await fast.hash(PHRASE);
  assert.match(stored, /^scrypt1\$12\$8\$1\$[A-Za-z0-9_-]{43}\$[A-Za-z0-9_-]+$/);
  assert.equal(await fast.verify(PHRASE, stored), true);
  assert.equal(await fast.verify(`${PHRASE}!`, stored), false);
  // Two hashes of the same password differ: the salt is fresh every time.
  assert.notEqual(await fast.hash(PHRASE), await fast.hash(PHRASE));
});

test('rows written before versioning still verify and are flagged for upgrade', async () => {
  const legacy = legacyHash(PHRASE);
  assert.equal(await fast.verify(PHRASE, legacy), true);
  assert.equal(await fast.verify('wrong', legacy), false);
  assert.equal(fast.needsUpgrade(legacy), true);
  const current = await fast.hash(PHRASE);
  assert.equal(fast.needsUpgrade(current), false);
  // A row at another cost verifies with the row's parameters, not the instance's.
  const salt = secret();
  const row = `scrypt1$11$8$1$${salt}$${scryptSync(PHRASE, salt, 64, { N: 2048, r: 8, p: 1, maxmem: 64 * 1024 * 1024 }).toString('base64url')}`;
  assert.equal(await fast.verify(PHRASE, row), true);
  assert.equal(fast.needsUpgrade(row), true);
});

test('an unreadable row is a failed login, never an exception', async () => {
  for (const stored of ['x:y', '', 'scrypt1$junk', 'scrypt1$17$8$1$onlyfour', 'scrypt1$99$8$1$a$b', 'argon2id$v=19$x', null, 42, 'scrypt1p$17$8$1$abc$def']) {
    assert.equal(await createPasswords().verify(PHRASE, stored), false, String(stored));
  }
  // A peppered row checked without the pepper fails closed instead of throwing.
  const peppered = await createPasswords({ pepper: 'server-key', kdf: { ...KDF, logN: 12 } }).hash(PHRASE);
  assert.equal(await createPasswords({ kdf: { ...KDF, logN: 12 } }).verify(PHRASE, peppered), false);
});

test('the optional pepper is per-instance and never invalidates older rows', async () => {
  const kdf = { ...KDF, logN: 12 };
  const keyed = createPasswords({ pepper: 'server-key', kdf });
  const stored = await keyed.hash(PHRASE);
  assert.match(stored, /^scrypt1p\$/);
  assert.equal(await keyed.verify(PHRASE, stored), true);
  assert.equal(await createPasswords({ pepper: 'other-key', kdf }).verify(PHRASE, stored), false);
  // Rows written before the pepper existed keep working under it, and are upgraded on the next login.
  const plain = await createPasswords({ kdf }).hash(PHRASE);
  assert.equal(await keyed.verify(PHRASE, plain), true);
  assert.equal(keyed.needsUpgrade(plain), true);
  // The reverse migration is left alone: the old instance cannot rewrite a peppered row.
  assert.equal(createPasswords({ kdf }).needsUpgrade(stored), false);
});

test('strength refusals name the reason', () => {
  const cases = [
    ['short', 'Ab1!x', {}], ['long', 'a'.repeat(PASSWORD.max + 1), {}],
    ['common', 'password123', {}], ['common', 'xiaopiao', {}], ['common', 'woaini520', {}], ['common', '12345678', {}],
    ['repeat', 'aaaaaaaa', {}], ['sequence', '01234567', {}], ['sequence', 'abcdefgh', {}], ['sequence', 'hgfedcba', {}],
    ['identity', 'longperson', { username: 'longperson' }], ['identity', 'someone1', { email: 'SomeOne1@example.test' }],
    ['identity', 'longperson', { username: 'LongPerson' }],
  ];
  for (const [reason, password, identity] of cases) {
    assert.deepEqual(checkStrength(password, identity), { ok: false, reason }, `${password} → ${reason}`);
  }
  for (const password of [PHRASE, 'Mint9-k2', 'Ab1!xYz9', '这一句话里有汉字也不短', 'zzz12345']) {
    assert.deepEqual(checkStrength(password), { ok: true }, password);
  }
  // Length is inclusive at both ends.
  assert.equal(checkStrength('Mint9-k2' + 'z'.repeat(PASSWORD.max - 8)).ok, true);
  assert.equal(checkStrength('Mint9-k2' + 'z'.repeat(PASSWORD.max - 7)).ok, false);
});

test('derivations are gated: an overloaded queue is refused instead of piling up memory', async () => {
  const small = createPasswords({ kdf: { ...KDF, logN: 12, concurrency: 1, queue: 1 } });
  const first = small.hash(PHRASE);
  const second = small.hash(PHRASE);
  await assert.rejects(async () => { await small.hash(PHRASE); }, /SERVER_BUSY/);
  assert.match(await first, /^scrypt1\$/);
  assert.match(await second, /^scrypt1\$/);
  // The slot is returned, so the next derivation is accepted.
  assert.match(await small.hash(PHRASE), /^scrypt1\$/);
});
