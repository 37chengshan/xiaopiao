import { test } from 'node:test';
import assert from 'node:assert/strict';
import { providers, providerFromEnv, providerKey, endpoints, headersFor, commandcode } from '../src/providers.mjs';
import { buildSystemPrompt, SYSTEM_RULES } from '../src/prompt.mjs';

test('commandcode descriptor is the free-only default and carries the required user agent', () => {
  assert.deepEqual(providerFromEnv({}), commandcode);
  assert.equal(providerKey(commandcode, { MODEL_API_KEY: 'k' }), 'k');
  assert.equal(providerFromEnv({ MODEL_PROVIDER: 'commandcode' }).id, 'commandcode');
  assert.throws(() => providerFromEnv({ MODEL_PROVIDER: 'nope' }), /UNKNOWN_MODEL_PROVIDER/);
  assert.ok(commandcode.models.every(id => id.endsWith(':free') || id.endsWith('-free')));
  assert.match(headersFor(commandcode, 'k')['User-Agent'], /Xiaopiao/);
  assert.equal(endpoints(commandcode).chat, 'https://api.commandcode.ai/provider/v1/chat/completions');
  assert.equal(endpoints(commandcode).models, 'https://api.commandcode.ai/provider/v1/models');
});
test('a new vendor is only a descriptor with the same shape', () => {
  const vendor = { ...commandcode, id: 'example', baseUrl: 'https://api.example.test/v1', keyEnv: 'EXAMPLE_KEY', models: ['small:free'] };
  assert.equal(providers[commandcode.id].id, 'commandcode');
  assert.equal(providerKey(vendor, { EXAMPLE_KEY: 'e', MODEL_API_KEY: 'k' }), 'e');
  assert.equal(endpoints(vendor).chat, 'https://api.example.test/v1/chat/completions');
});
test('system prompt keeps every guardrail and pins the user timezone', () => {
  const prompt = buildSystemPrompt({ timezone: 'Asia/Shanghai', today: '2026-10-29' });
  for (const phrase of ['数字只能来自本轮的取数工具结果', '展示一律用 show_view', '逐字一致', '禁止消费建议', '不代用户操作', '未共享就说「未共享」', '不能写', '不可信数据', '不要输出内部思考过程', '人民币符号']) {
    assert.ok(prompt.includes(phrase), `missing guardrail: ${phrase}`);
  }
  assert.ok(prompt.includes('用户时区 = Asia/Shanghai'));
  assert.ok(prompt.includes('今天 = 2026-10-29'));
  assert.equal(SYSTEM_RULES.length >= 12, true);
  for (const kind of ['spend_summary', 'category_split', 'trend', 'entries', 'peak', 'weekly_report', 'subscriptions', 'draft_list']) {
    assert.ok(prompt.includes(kind), `view catalogue missing ${kind}`);
  }
  assert.equal(buildSystemPrompt({ timezone: 'Not/AZone' }).includes('今天 ='), true);
});
