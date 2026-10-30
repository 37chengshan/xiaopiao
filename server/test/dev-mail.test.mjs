import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, readFileSync, statSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { devMailSender } from '../src/dev-mail.mjs';

const tempFile = () => join(mkdtempSync(join(tmpdir(), 'xp-dev-mail-')), 'dev-mail.log');

test('the dev mailbox is off unless XP_DEV_MAIL=1, and never in production', () => {
  assert.equal(devMailSender({ enabled: false }), null);
  assert.equal(devMailSender({ enabled: false, production: true }), null);
  assert.throws(() => devMailSender({ enabled: true, production: true }), /production/);
});

test('a dev code lands as one tab-separated line and prints once', async () => {
  const file = tempFile(); const printed = [];
  const send = devMailSender({ enabled: true, file, log: line => printed.push(line) });
  await send({ to: 'dev@example.test', purpose: 'verify', code: '123456' });
  await send({ to: 'dev@example.test', purpose: 'reset', code: '654321' });

  const lines = readFileSync(file, 'utf8').trimEnd().split('\n');
  assert.equal(lines.length, 2);
  const [at, purpose, to, code] = lines[0].split('\t');
  assert.equal(purpose, 'verify');
  assert.equal(to, 'dev@example.test');
  assert.equal(code, '123456');
  assert.ok(!Number.isNaN(Date.parse(at)), 'ISO timestamp leads the line');
  assert.equal(lines[1].split('\t')[3], '654321');
  assert.deepEqual(printed, ['dev mail → dev@example.test verify 123456', 'dev mail → dev@example.test reset 654321']);
  assert.equal(statSync(file).mode & 0o077, 0, 'the code log is private to the owner');
});

test('a malformed code request is refused and writes nothing', async () => {
  const file = tempFile();
  const send = devMailSender({ enabled: true, file, log: () => {} });
  await assert.rejects(send({ to: 'dev@example.test', purpose: 'verify', code: '12' }), /INVALID_MAIL/);
  await assert.rejects(send({ to: 'not-an-address', purpose: 'verify', code: '123456' }), /INVALID_MAIL/);
  await assert.rejects(send({ to: 'dev@example.test', purpose: 'invoice', code: '123456' }), /INVALID_MAIL/);
  assert.throws(() => readFileSync(file, 'utf8'), /ENOENT/);
});
