import { createHash } from 'node:crypto';

/** Only server code can send verification mail. Never expose provider keys or response bodies. */
export function createAgentMailSender({ apiKey, inboxId, relayUrl, relayKey, fetcher = fetch }) {
  if (!apiKey || !inboxId) return null;
  if (!/^[^\s@/]+@[^\s@/]+\.[^\s@/]+$/.test(inboxId)) throw new Error('AGENTMAIL_INBOX_ID must be a sender inbox address');
  if (Boolean(relayUrl) !== Boolean(relayKey)) throw new Error('Mail relay URL and secret must be configured together');
  if (relayUrl) {
    const url = new URL(relayUrl);
    if (url.protocol !== 'https:' || url.username || url.password || url.search || url.hash || url.pathname !== '/__mail/send' || relayKey.length < 32) throw new Error('Invalid private mail relay configuration');
  }
  const endpoint = relayUrl || `https://api.agentmail.to/v0/inboxes/${encodeURIComponent(inboxId)}/messages/send`;
  return async ({ to, purpose, code }) => {
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(to) || !/^\d{6}$/.test(code) || !['verify', 'reset'].includes(purpose)) throw new Error('INVALID_MAIL');
    const idempotency = createHash('sha256').update(JSON.stringify([inboxId, to, purpose, code])).digest('hex');
    const response = await fetcher(endpoint, {
      method: 'POST', signal: AbortSignal.timeout(10_000), redirect: 'error',
      headers: { Authorization: `Bearer ${apiKey}`, 'Content-Type': 'application/json', 'Idempotency-Key': idempotency,
        ...(relayUrl ? { 'X-Xiaopiao-Relay-Key': relayKey } : {}),
      },
      body: JSON.stringify({ to, subject: purpose === 'verify' ? '验证你的今日小票邮箱 / Verify your email' : '重设今日小票密码 / Reset your password',
        text: `你的今日小票验证码是 ${code}，10 分钟内有效。请勿分享给他人。如果不是你操作的，请忽略本邮件。\n\nYour Daily Receipt code is ${code}. It expires in 10 minutes. Do not share it. If you did not request this, ignore this email.`,
      }),
    });
    // Do not log response bodies, message ids, addresses, codes, or authorization headers.
    if (!response.ok) { await response.body?.cancel(); throw new Error('MAIL_UNAVAILABLE'); }
    await response.body?.cancel();
  };
}
