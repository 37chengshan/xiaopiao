/**
 * Dev mailbox for local work: sends no mail at all. Each code is appended to a local log as one
 * tab-separated line (ISO time, purpose, address, code) and echoed once, so a simulator or the Web
 * dev server can finish register → verify → sync without AgentMail credentials.
 *
 * Refused in production: a plaintext code log next to the database is an account-takeover path.
 */
import { appendFileSync, mkdirSync } from 'node:fs';
import { dirname } from 'node:path';

export const DEV_MAIL_LOG = './data/dev-mail.log';

export function devMailSender({ enabled = false, production = false, file = DEV_MAIL_LOG, log = console.log } = {}) {
  if (!enabled) return null;
  if (production) throw new Error('XP_DEV_MAIL must be off in production');
  mkdirSync(dirname(file), { recursive: true, mode: 0o700 });
  return async ({ to, purpose, code }) => {
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(to) || !/^\d{6}$/.test(code) || !['verify', 'reset'].includes(purpose)) throw new Error('INVALID_MAIL');
    appendFileSync(file, `${new Date().toISOString()}\t${purpose}\t${to}\t${code}\n`, { mode: 0o600 });
    log(`dev mail → ${to} ${purpose} ${code}`);
  };
}
