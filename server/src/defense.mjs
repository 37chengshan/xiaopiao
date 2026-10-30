/** Resource budgets for one small API instance. No identities or payloads in diagnostics. */
export const POLICY = Object.freeze({ requests: 64, perIp: 8, auth: 3, chats: 4, bodyMs: 15_000, chatMs: 120_000, rateKeys: 20_000 });
export function securityError(code, retryAfter) { return Object.assign(new Error(code), { code, retryAfter }); }
export function createDefense(policy = POLICY) {
  let requests = 0;
  const ips = new Map(), pools = new Map(), rejected = Object.create(null);
  const reject = code => { rejected[code] = Math.min((rejected[code] ?? 0) + 1, Number.MAX_SAFE_INTEGER); };
  function acquire(pool, max) {
    if ((pools.get(pool) ?? 0) >= max) throw securityError('SERVER_BUSY', 5);
    pools.set(pool, (pools.get(pool) ?? 0) + 1);
    let released = false;
    return () => { if (!released) { released = true; pools.set(pool, pools.get(pool) - 1); } };
  }
  function admit(ip) {
    if (requests >= policy.requests || (ips.get(ip) ?? 0) >= policy.perIp) throw securityError('SERVER_BUSY', 5);
    requests++; ips.set(ip, (ips.get(ip) ?? 0) + 1);
    let released = false;
    return () => {
      if (released) return;
      released = true; requests--;
      const n = ips.get(ip) - 1;
      if (n) ips.set(ip, n); else ips.delete(ip);
    };
  }
  return { admit, acquire, reject, snapshot: () => ({ activeRequests: requests, activeIps: ips.size, pools: Object.fromEntries(pools), rejected: { ...rejected } }) };
}

/** Bound bytes and wall time before parsing; decode UTF-8 only after all byte chunks arrive. */
export async function readJson(req, limit, tooLarge = 'INVALID_INPUT', timeoutMs = POLICY.bodyMs) {
  if (!/^application\/json(?:\s*;|\s*$)/i.test(req.headers['content-type'] ?? '') ||
      (req.headers['content-encoding'] && req.headers['content-encoding'] !== 'identity')) throw securityError('INVALID_INPUT');
  if (Number(req.headers['content-length']) > limit) throw securityError(tooLarge);
  const raw = await new Promise((resolve, reject) => {
    const chunks = []; let bytes = 0;
    const cleanup = () => { clearTimeout(timer); req.off('data', data); req.off('end', end); req.off('aborted', aborted); req.off('error', aborted); };
    const fail = code => { cleanup(); req.pause(); reject(securityError(code)); };
    const data = chunk => { bytes += chunk.length; if (bytes > limit) return fail(tooLarge); chunks.push(chunk); };
    const end = () => { cleanup(); resolve(Buffer.concat(chunks, bytes)); };
    const aborted = () => fail('INVALID_INPUT');
    const timer = setTimeout(() => fail('REQUEST_TIMEOUT'), timeoutMs).unref();
    req.on('data', data); req.once('end', end); req.once('aborted', aborted); req.once('error', aborted);
  });
  let value;
  try { value = JSON.parse(new TextDecoder('utf-8', { fatal: true }).decode(raw)); } catch { throw securityError('INVALID_INPUT'); }
  if (!value || typeof value !== 'object' || Array.isArray(value)) throw securityError('INVALID_INPUT');
  const stack = [[value, 0]]; let nodes = 0;
  while (stack.length) {
    const [item, depth] = stack.pop();
    if (++nodes > 10_000 || depth > 24) throw securityError('INVALID_INPUT');
    if (item && typeof item === 'object') for (const child of Object.values(item)) stack.push([child, depth + 1]);
  }
  return value;
}
