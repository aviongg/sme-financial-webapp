// Additional read-only contract evidence. The original DEF-08 assertions live in
// Stage A and are invoked unchanged before this probe, against the same real DB.
import assert from 'node:assert/strict';
import { mkdirSync, writeFileSync } from 'node:fs';
import path from 'node:path';

export async function capabilityReadOnly({ origin, call, csrf, outDir, testedSHA }) {
  const output = path.join(outDir, 'capability-readonly'); mkdirSync(output, { recursive: true });
  const checks = [], responses = [];
  const get = async route => { const reply = await call('GET', route); assert.equal(reply.status, 200, route + ': ' + reply.body); return reply.json; };
  const snapshot = async () => {
    const facts = {};
    for (const route of ['/finance/accounts', '/finance/transactions', '/records/monthly', '/whatsapp/deliveries']) {
      facts[route] = JSON.parse(JSON.stringify(await get(route), (key, value) => key === 'observationAt' ? undefined : value));
    }
    return facts;
  };
  let before, after, failure;
  try {
    // Settle the existing projection-on-read before taking the observation pair.
    await get('/records/monthly');
    before = await snapshot();
    const activeBefore = await get('/businesses/active');
    const expected = { provider: 'mock', automaticWeeklySummaries: false };
    for (let i = 0; i < 3; i++) {
      const body = await get('/whatsapp/delivery-capability');
      assert.deepEqual(Object.keys(body).sort(), ['automaticWeeklySummaries', 'provider']);
      assert.deepEqual(body, expected); responses.push(body);
    }
    checks.push('Real runtime returns only mock provider and disabled scheduler; no identity, phone, URL or secret fields');
    const anonymous = await fetch(origin + '/api/whatsapp/delivery-capability');
    assert.equal(anonymous.status, 401); checks.push('Anonymous capability read denied with 401');
    await csrf();
    for (const method of ['POST', 'PUT', 'PATCH', 'DELETE']) {
      const reply = await call(method, '/whatsapp/delivery-capability', { provider: 'meta' });
      assert.equal(reply.status, 405, method + ': ' + reply.body);
    }
    assert.deepEqual(await get('/whatsapp/delivery-capability'), expected);
    checks.push('All four write verbs denied with valid CSRF; capability cannot reconfigure the provider');
    assert.deepEqual(await get('/businesses/active'), activeBefore);
    after = await snapshot();
    assert.deepEqual(after, before, 'Capability reads and denied writes must not change accounts, transactions, month projections or deliveries');
    checks.push('Exact financial/account/month/delivery facts unchanged; only request observationAt clock is excluded');
  } catch (error) { failure = String(error); throw error; }
  finally {
    writeFileSync(path.join(output, 'results.json'), JSON.stringify({ testedSHA,
      scope: 'Real Spring/PostgreSQL; mock provider, scheduler off; no external acceptance',
      status: failure ? 'FAIL' : 'PASS', checks, responses, before, after, ...(failure ? { failure } : {}),
    }, null, 2));
  }
  console.log('CAPABILITY READ-ONLY PASS: schema, anonymity, write denial and unchanged financial facts');
}
