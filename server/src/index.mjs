import { createAgentMailSender } from './mail.mjs';
import { devMailSender } from './dev-mail.mjs';
import { store } from './store.mjs';
import { createApp } from './app.mjs';
import { createAgent } from './agent.mjs';
import { providerFromEnv, providerKey } from './providers.mjs';
import { createWechatClient } from './wechat.mjs';
import { createVision } from './life.mjs';
const production = process.env.NODE_ENV === 'production';
const origin = process.env.APP_ORIGIN ?? 'http://localhost:5173';
// Local debugging only: codes go to data/dev-mail.log instead of AgentMail. Throws under NODE_ENV=production.
const sendMail = devMailSender({ enabled: process.env.XP_DEV_MAIL === '1', production })
  ?? createAgentMailSender({ apiKey: process.env.AGENTMAIL_API_KEY, inboxId: process.env.AGENTMAIL_INBOX_ID,
    relayUrl: process.env.AGENTMAIL_RELAY_URL, relayKey: process.env.AGENTMAIL_RELAY_KEY });
const provider = providerFromEnv();
// WeChat: personal subject, mini program only. Both variables absent simply disables the routes with a clear 503.
const wechatClient = createWechatClient({ appId: process.env.WX_MP_APPID, secret: process.env.WX_MP_SECRET });
// Photo recognition: off unless a vision model is named; the key may be shared with the text provider or its own.
const vision = createVision({
  apiKey: process.env.LIFE_VISION_API_KEY || providerKey(provider),
  model: process.env.LIFE_VISION_MODEL || null,
  baseUrl: process.env.LIFE_VISION_BASE_URL || provider.baseUrl,
});
const app = createApp({
  db: store(process.env.DB_PATH ?? './data/xiaopiao.sqlite'), production, origin, trustedProxy: process.env.TRUST_PROXY === '1',
  pepper: process.env.PASSWORD_PEPPER || null,
  agent: createAgent({ apiKey: providerKey(provider), provider }),
  sendMail, wechatClient, vision,
});
app.listen(Number(process.env.PORT ?? 8787), process.env.HOST ?? '127.0.0.1', () => console.log('Xiaopiao API ready'));
for (const signal of ['SIGTERM', 'SIGINT']) process.on(signal, () => { app.close(() => process.exit(0)); setTimeout(() => process.exit(1), 15_000).unref(); });

// Fixed-size aggregate counters only; never log IPs, emails, routes, credentials or payloads.
if (production) {
  const defenseLog = setInterval(() => {
    const snapshot = app.defenseSnapshot();
    if (Object.keys(snapshot.rejected).length || snapshot.guard.locked || snapshot.guard.tracked) console.log(JSON.stringify({ type: 'security_summary', ...snapshot }));
  }, 60_000).unref();
  app.once('close', () => clearInterval(defenseLog));
}
