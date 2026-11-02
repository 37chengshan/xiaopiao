#!/usr/bin/env node
/**
 * Read the newest feedback rows on the server this database lives on: one JSON object per line on stdout.
 *
 *   node scripts/feedback.mjs [--since=<epochMs>] [--limit=50]
 *
 * `DB_PATH` picks the database (same default as the server). There is deliberately no HTTP read endpoint: the text
 * can quote a user's notification, so it stays reachable only from the machine that owns the file.
 *
 *   node scripts/feedback.mjs --limit=20 | jq -r '"\(.createdAt)\t\(.kind)\t\(.text)"'
 */
import { existsSync } from 'node:fs';
import { resolve } from 'node:path';
import { store } from '../src/store.mjs';
import { createFeedback, LIMITS } from '../src/feedback.mjs';

const flag = name => process.argv.slice(2).find(value => value.startsWith(`--${name}=`))?.slice(name.length + 3);
const bad = message => { console.error(`feedback: ${message}`); process.exit(1); };

const rawSince = flag('since') ?? '';
const rawLimit = flag('limit') ?? '';
if (rawSince && !/^\d{1,16}$/.test(rawSince)) bad('--since 需要毫秒时间戳，例如 --since=1762000000000');
if (rawLimit && !/^\d{1,4}$/.test(rawLimit)) bad(`--limit 需要 1..${LIMITS.readMax} 的整数`);
const since = rawSince ? Number(rawSince) : 0;
const limit = rawLimit ? Number(rawLimit) : LIMITS.readDefault;
if (limit < 1 || limit > LIMITS.readMax) bad(`--limit 需要 1..${LIMITS.readMax} 的整数`);

const path = resolve(process.env.DB_PATH ?? './data/xiaopiao.sqlite');
// Never create an empty database: a typo in DB_PATH must fail loudly instead of printing nothing.
if (!existsSync(path)) bad(`找不到数据库 ${path}（用 DB_PATH 指定）`);

const db = store(path);
try {
  for (const row of createFeedback({ db }).read({ since, limit })) console.log(JSON.stringify(row));
} finally { db.close(); }
