import { createAgentMailSender } from './mail.mjs';
import { devMailSender } from './dev-mail.mjs';
import { store } from './store.mjs';
import { createApp } from './app.mjs';
import { createAgent } from './agent.mjs';
import { providerFromEnv, providerKey } from './providers.mjs';
const production = process.env.NODE_ENV === 'production';
const origin = process.env.APP_ORIGIN ?? 'http://localhost:5173';
// Local debugging only: codes go to data/dev-mail.log instead of AgentMail. Throws under NODE_ENV=production.
const sendMail = devMailSender({ enabled: process.env.XP_DEV_MAIL === '1', production })
  ?? createAgentMailSender({ apiKey: process.env.AGENTMAIL_API_KEY, inboxId: process.env.AGENTMAIL_INBOX_ID,
    relayUrl: process.env.AGENTMAIL_RELAY_URL, relayKey: process.env.AGENTMAIL_RELAY_KEY });
const app = createApp({
  db: store(process.env.DB_PATH ?? './data/xiaopiao.sqlite'), production, origin, trustedProxy: process.env.TRUST_PROXY === '1',
  agent: createAgent({ apiKey: providerKey(providerFromEnv()), provider: providerFromEnv() }),
  sendMail,
});
app.listen(Number(process.env.PORT ?? 8787), process.env.HOST ?? '127.0.0.1', () => console.log('Xiaopiao API ready'));
for (const signal of ['SIGTERM', 'SIGINT']) process.on(signal, () => { app.close(() => process.exit(0)); setTimeout(() => process.exit(1), 15_000).unref(); });
