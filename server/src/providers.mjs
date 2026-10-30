/**
 * Provider descriptors. One agent instance talks to exactly one provider, but every vendor-specific detail
 * (endpoints, auth header, user agent, free-model allowlist) lives here so adding a vendor is a descriptor,
 * not a second copy of the streaming loop.
 *
 * To add a vendor: add a descriptor with the same shape, point MODEL_PROVIDER at its id, and set its key.
 * An OpenAI-compatible endpoint only needs baseUrl + models + chat path + auth.
 */
export const commandcode = {
  id: 'commandcode',
  label: 'Command Code',
  baseUrl: 'https://api.commandcode.ai/provider/v1',
  /** Cloudflare in front of this provider rejects default client agents (error 1010). */
  headers: { 'User-Agent': 'Xiaopiao-Agent/1.0' },
  auth: 'bearer',
  supportsTools: true,
  /** Free tier only; paid ids must never be added here. */
  models: ['reflection/beam:free', 'stealth/glyph-cluster:free', 'poolside/laguna-s-2.1-free'],
};

export const providers = { [commandcode.id]: commandcode };

export function providerFromEnv(env = process.env) {
  const id = env.MODEL_PROVIDER?.trim() || commandcode.id;
  const provider = providers[id];
  if (!provider) throw new Error(`UNKNOWN_MODEL_PROVIDER:${id}`);
  return provider;
}

export function providerKey(provider, env = process.env) {
  return provider.keyEnv ? env[provider.keyEnv] : env.MODEL_API_KEY;
}

export const endpoints = provider => ({
  models: `${provider.baseUrl.replace(/\/$/, '')}/models`,
  chat: `${provider.baseUrl.replace(/\/$/, '')}/chat/completions`,
});

export const headersFor = (provider, apiKey) => ({
  Authorization: `Bearer ${apiKey}`,
  ...provider.headers,
});
