import { DatabaseSync, backup } from 'node:sqlite';
import { chmodSync, mkdirSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
// Run as the API owner. SQLite backup API includes WAL; copying just the main file is unsafe.
const source = process.env.DB_PATH;
const target = process.argv[2];
if (!source || !target || resolve(source) === resolve(target)) throw new Error('Set DB_PATH and provide a distinct destination');
mkdirSync(dirname(target), { recursive: true, mode: 0o700 });
const db = new DatabaseSync(source, { readOnly: true });
try { await backup(db, target); chmodSync(target, 0o600); console.log('Backup complete'); } finally { db.close(); }
